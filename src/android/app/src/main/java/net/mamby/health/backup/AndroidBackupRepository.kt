@file:kotlinx.serialization.UseSerializers(
    net.mamby.health.core.model.UuidSerializer::class,
    net.mamby.health.core.model.InstantSerializer::class,
    net.mamby.health.core.model.LocalDateSerializer::class,
    net.mamby.health.core.model.LocalTimeSerializer::class,
    net.mamby.health.core.model.DayOfWeekSerializer::class,
)

package net.mamby.health.backup

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.mamby.health.core.model.HealthVault
import net.mamby.health.data.RestoreDocumentBlob
import net.mamby.health.data.VaultRepository
import net.mamby.health.di.ApplicationScope
import net.mamby.health.di.EnvironmentName
import net.mamby.health.settings.BackupConfiguration
import net.mamby.health.settings.BackupIssue
import net.mamby.health.settings.BackupKeyConfiguration
import net.mamby.health.settings.BackupState
import net.mamby.health.settings.BackupStatus
import net.mamby.health.settings.SettingsRepository

@Singleton
class AndroidBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contentResolver: ContentResolver,
    private val settingsRepository: SettingsRepository,
    private val vaultRepository: VaultRepository,
    private val container: PortableBackupContainer,
    private val cryptography: PortableBackupCryptography,
    private val localKeyProtector: LocalBackupKeyProtector,
    private val workManager: WorkManager,
    private val json: Json,
    private val clock: Clock,
    @EnvironmentName private val environmentName: String,
    @ApplicationScope applicationScope: CoroutineScope,
) : BackupRepository {
    override val status = settingsRepository.settings
        .map { it.backupStatus }
        .distinctUntilChanged()

    private val restoreMutex = Mutex()
    private var pendingRestore: PendingRestore? = null

    init {
        cleanupAbandonedStagingFiles()
        applicationScope.launch {
            var observedRevision: Long? = null
            vaultRepository.state
                .map { state ->
                    (state as? net.mamby.health.data.VaultState.Ready)?.vault?.revision
                }
                .distinctUntilChanged()
                .collect { revision ->
                    if (revision == null) return@collect
                    val previous = observedRevision
                    observedRevision = revision
                    if (previous != null && previous != revision) scheduleAfterVaultChange()
                }
        }
    }

    override suspend fun configure(
        destination: Uri,
        passphrase: CharArray,
        scheduled: Boolean,
    ): BackupOperationResult {
        if (!BackupKeyDeriver.isAcceptablePassphrase(passphrase)) {
            passphrase.fill('\u0000')
            return BackupOperationResult.InvalidPassphrase(
                BackupKeyDeriver.MIN_PASSPHRASE_LENGTH,
            )
        }
        if (destination.scheme != ContentResolver.SCHEME_CONTENT) {
            passphrase.fill('\u0000')
            return BackupOperationResult.Failure(
                issue = BackupIssue.DESTINATION_UNAVAILABLE,
                retryable = false,
            )
        }

        val previous = settingsRepository.settings.first().backupConfiguration
        val salt = cryptography.newSalt()
        val backupKey = cryptography.newBackupKey()
        var permissionPersisted = false
        return try {
            contentResolver.takePersistableUriPermission(destination, PERSISTED_URI_FLAGS)
            permissionPersisted = true
            val passphraseWrappedKey = cryptography.wrapForPassphrase(
                backupKey = backupKey,
                passphrase = passphrase,
                salt = salt,
            )
            val locallyWrappedKey = localKeyProtector.wrap(backupKey)
            val configuration = BackupConfiguration(
                destinationUri = destination.toString(),
                scheduled = scheduled,
                key = BackupKeyConfiguration(
                    saltBase64 = PortableBackupCryptography.encodeBase64(salt),
                    passphraseWrappedKeyBase64 =
                        PortableBackupCryptography.encodeBase64(passphraseWrappedKey),
                    locallyWrappedKeyBase64 =
                        PortableBackupCryptography.encodeBase64(locallyWrappedKey),
                    iterations = BackupKeyDeriver.ITERATIONS,
                ),
            )
            settingsRepository.setBackupConfiguration(configuration)
            settingsRepository.setBackupStatus(BackupStatus(state = BackupState.READY))
            if (previous != null && previous.destinationUri != configuration.destinationUri) {
                releasePersistedPermission(previous.destinationUri.toUri())
            }
            backupNow()
        } catch (error: SecurityException) {
            if (permissionPersisted && previous?.destinationUri != destination.toString()) {
                releasePersistedPermission(destination)
            }
            recordFailure(BackupIssue.DESTINATION_PERMISSION_LOST, retryable = false)
        } catch (error: Exception) {
            if (permissionPersisted && previous?.destinationUri != destination.toString()) {
                releasePersistedPermission(destination)
            }
            recordFailure(BackupIssue.CORRUPT_CONFIGURATION, retryable = false)
        } finally {
            passphrase.fill('\u0000')
            salt.fill(0)
            backupKey.fill(0)
        }
    }

    override suspend fun backupNow(): BackupOperationResult {
        val settings = settingsRepository.settings.first()
        val configuration = settings.backupConfiguration
            ?: return BackupOperationResult.NotConfigured
        settingsRepository.setBackupStatus(
            BackupStatus(
                state = BackupState.RUNNING,
                lastSuccess = settings.backupStatus.lastSuccess,
            ),
        )

        var backupKey: ByteArray? = null
        return try {
            val unwrappedKey = localKeyProtector.unwrap(
                PortableBackupCryptography.decodeBase64(
                    configuration.key.locallyWrappedKeyBase64,
                ),
            )
            backupKey = unwrappedKey
            createAndPublishBackup(configuration, unwrappedKey)
            val completedAt = clock.instant()
            settingsRepository.setBackupStatus(
                BackupStatus(
                    state = BackupState.SUCCEEDED,
                    lastSuccess = completedAt,
                ),
            )
            BackupOperationResult.Success(completedAt)
        } catch (error: SecurityException) {
            recordFailure(BackupIssue.DESTINATION_PERMISSION_LOST, retryable = false)
        } catch (error: FileNotFoundException) {
            recordFailure(BackupIssue.DESTINATION_UNAVAILABLE, retryable = false)
        } catch (error: IOException) {
            recordFailure(BackupIssue.IO_FAILURE, retryable = true)
        } catch (error: IllegalStateException) {
            recordFailure(BackupIssue.VAULT_UNAVAILABLE, retryable = true)
        } catch (error: Exception) {
            recordFailure(BackupIssue.CORRUPT_CONFIGURATION, retryable = false)
        } finally {
            backupKey?.fill(0)
        }
    }

    override suspend fun scheduleAfterVaultChange() {
        val configuration = settingsRepository.settings.first().backupConfiguration ?: return
        if (!configuration.scheduled) return
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInitialDelay(BACKUP_CHANGE_COALESCE_DELAY)
            .addTag(BACKUP_WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(
            BACKUP_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override suspend fun prepareRestore(
        source: Uri,
        passphrase: CharArray,
    ): RestorePreparationResult {
        if (!BackupKeyDeriver.isAcceptablePassphrase(passphrase)) {
            passphrase.fill('\u0000')
            return RestorePreparationResult.WrongPassphrase
        }
        val stagedContainer = newStagingFile(RESTORE_STAGING_PREFIX)
        var backupKey: ByteArray? = null
        return try {
            copySourceToStaging(source, stagedContainer)
            val header = container.readHeader(stagedContainer)
            validateHeader(header)
            val salt = PortableBackupCryptography.decodeBase64(header.saltBase64)
            val wrappedKey = PortableBackupCryptography.decodeBase64(
                header.passphraseWrappedKeyBase64,
            )
            val unwrappedKey = try {
                cryptography.unwrapFromPassphrase(
                    envelope = wrappedKey,
                    passphrase = passphrase,
                    salt = salt,
                    iterations = header.iterations,
                )
            } finally {
                salt.fill(0)
                wrappedKey.fill(0)
            }
            backupKey = unwrappedKey
            val manifest = container.readManifest(stagedContainer, unwrappedKey)
            container.validateStructure(stagedContainer, manifest)
            val vault = decodeAndValidateVault(manifest)
            container.verifyDocuments(stagedContainer, manifest, unwrappedKey)
            val preview = RestorePreview(
                token = UUID.randomUUID(),
                sourceEnvironment = manifest.sourceEnvironment,
                currentEnvironment = environmentName,
                revision = manifest.revision,
                updatedAt = Instant.parse(manifest.updatedAt),
                documentCount = manifest.documents.size,
            )
            restoreMutex.withLock {
                pendingRestore?.dispose()
                pendingRestore = PendingRestore(
                    preview = preview,
                    container = stagedContainer,
                    backupKey = unwrappedKey,
                    vault = vault,
                    manifest = manifest,
                )
                backupKey = null
            }
            RestorePreparationResult.Ready(preview)
        } catch (error: WrongBackupPassphraseException) {
            stagedContainer.delete()
            RestorePreparationResult.WrongPassphrase
        } catch (error: UnsupportedBackupException) {
            stagedContainer.delete()
            RestorePreparationResult.UnsupportedVersion
        } catch (error: BackupCorruptionException) {
            stagedContainer.delete()
            RestorePreparationResult.Corrupt
        } catch (error: SecurityException) {
            stagedContainer.delete()
            RestorePreparationResult.DestinationUnavailable
        } catch (error: IOException) {
            stagedContainer.delete()
            RestorePreparationResult.DestinationUnavailable
        } catch (error: Exception) {
            stagedContainer.delete()
            RestorePreparationResult.Corrupt
        } finally {
            passphrase.fill('\u0000')
            backupKey?.fill(0)
        }
    }

    override suspend fun commitRestore(
        token: UUID,
        crossFlavorConfirmed: Boolean,
    ): RestoreCommitResult = restoreMutex.withLock {
        val pending = pendingRestore?.takeIf { it.preview.token == token }
            ?: return@withLock RestoreCommitResult.NotPrepared
        if (pending.preview.requiresCrossFlavorConfirmation && !crossFlavorConfirmed) {
            return@withLock RestoreCommitResult.CrossFlavorConfirmationRequired(pending.preview)
        }

        val sources = pending.manifest.documents.map { document ->
            RestoreDocumentBlob(
                blobId = UUID.fromString(document.blobId),
                expectedSizeBytes = document.sizeBytes,
                openStream = {
                    container.openVerifiedDocument(
                        container = pending.container,
                        document = document,
                        backupKey = pending.backupKey,
                    )
                },
            )
        }
        return@withLock try {
            vaultRepository.restore(pending.vault, sources)
            pending.dispose()
            pendingRestore = null
            RestoreCommitResult.Success
        } catch (error: Exception) {
            pending.dispose()
            pendingRestore = null
            RestoreCommitResult.FailedSafely
        }
    }

    override suspend fun discardRestore(token: UUID) {
        restoreMutex.withLock {
            val pending = pendingRestore?.takeIf { it.preview.token == token } ?: return
            pending.dispose()
            pendingRestore = null
        }
    }

    override suspend fun clearConfiguration() {
        workManager.cancelUniqueWork(BACKUP_WORK_NAME)
        val configuration = settingsRepository.settings.first().backupConfiguration
        if (configuration != null) {
            releasePersistedPermission(configuration.destinationUri.toUri())
        } else {
            contentResolver.persistedUriPermissions.forEach { permission ->
                val grantedFlags =
                    (if (permission.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                        (if (permission.isWritePermission) {
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        } else {
                            0
                        })
                if (grantedFlags != 0) {
                    runCatching {
                        contentResolver.releasePersistableUriPermission(
                            permission.uri,
                            grantedFlags,
                        )
                    }
                }
            }
        }
        settingsRepository.clearBackupConfiguration()
        restoreMutex.withLock {
            pendingRestore?.dispose()
            pendingRestore = null
        }
        cleanupAbandonedStagingFiles()
    }

    private suspend fun createAndPublishBackup(
        configuration: BackupConfiguration,
        backupKey: ByteArray,
    ) {
        val vault = vaultRepository.exportSnapshot()
        val documents = vault.documents.mapIndexed { index, document ->
            BackupDocumentEntry(
                index = index,
                blobId = document.blobId.toString(),
                sizeBytes = document.sizeBytes,
            )
        }
        val vaultJsonBytes = json.encodeToString(vault).encodeToByteArray()
        val vaultJsonBase64 = try {
            PortableBackupCryptography.encodeBase64(vaultJsonBytes)
        } finally {
            vaultJsonBytes.fill(0)
        }
        val manifest = BackupManifest(
            sourceEnvironment = environmentName,
            vaultSchemaVersion = vault.version,
            revision = vault.revision,
            updatedAt = vault.updatedAt.toString(),
            vaultJsonBase64 = vaultJsonBase64,
            documents = documents,
        )
        val header = BackupHeader(
            iterations = configuration.key.iterations,
            saltBase64 = configuration.key.saltBase64,
            passphraseWrappedKeyBase64 = configuration.key.passphraseWrappedKeyBase64,
        )
        val staging = newStagingFile(BACKUP_STAGING_PREFIX)
        try {
            container.write(
                target = staging,
                header = header,
                manifest = manifest,
                backupKey = backupKey,
            ) { document, output ->
                vaultRepository.copyDocumentBlob(UUID.fromString(document.blobId), output)
            }
            publishStagedBackup(staging, configuration.destinationUri.toUri())
        } finally {
            staging.delete()
        }
    }

    private fun validateHeader(header: BackupHeader) {
        if (header.formatVersion != PortableBackupFormat.VERSION) {
            throw UnsupportedBackupException("Unsupported backup format version")
        }
        if (header.kdf != BackupKeyDeriver.ALGORITHM_LABEL) {
            throw UnsupportedBackupException("Unsupported backup key derivation")
        }
        if (header.iterations !in
            BackupKeyDeriver.MIN_ITERATIONS..BackupKeyDeriver.MAX_ACCEPTED_ITERATIONS
        ) {
            throw UnsupportedBackupException("Unsupported backup key-derivation cost")
        }
    }

    private fun decodeAndValidateVault(manifest: BackupManifest): HealthVault {
        if (manifest.formatVersion != PortableBackupFormat.VERSION) {
            throw UnsupportedBackupException("Unsupported backup manifest version")
        }
        val vaultBytes = PortableBackupCryptography.decodeBase64(manifest.vaultJsonBase64)
        val vault = try {
            json.decodeFromString<HealthVault>(vaultBytes.decodeToString())
        } catch (error: Exception) {
            throw BackupCorruptionException("Backup vault snapshot is invalid", error)
        } finally {
            vaultBytes.fill(0)
        }
        if (vault.version != manifest.vaultSchemaVersion) {
            throw BackupCorruptionException("Backup vault schema metadata does not match")
        }
        if (vault.version != HealthVault.CURRENT_VERSION) {
            throw UnsupportedBackupException("Unsupported vault schema version")
        }
        if (vault.revision != manifest.revision || vault.updatedAt.toString() != manifest.updatedAt) {
            throw BackupCorruptionException("Backup vault revision metadata does not match")
        }
        val expectedDocuments = vault.documents.map { it.blobId.toString() to it.sizeBytes }
        val actualDocuments = manifest.documents.map { it.blobId to it.sizeBytes }
        if (expectedDocuments != actualDocuments) {
            throw BackupCorruptionException("Backup document manifest does not match the vault")
        }
        return vault
    }

    private fun copySourceToStaging(source: Uri, destination: File) {
        val input = contentResolver.openInputStream(source)
            ?: throw FileNotFoundException("Backup source is unavailable")
        input.use { sourceStream ->
            destination.outputStream().buffered().use { target ->
                sourceStream.copyBoundedTo(target, PortableBackupFormat.MAX_CONTAINER_BYTES)
            }
        }
    }

    private fun publishStagedBackup(source: File, destination: Uri) {
        val descriptor = contentResolver.openFileDescriptor(destination, "rwt")
            ?: throw FileNotFoundException("Backup destination is unavailable")
        descriptor.use { parcelFileDescriptor ->
            source.inputStream().buffered().use { input ->
                java.io.FileOutputStream(parcelFileDescriptor.fileDescriptor).use { output ->
                    input.copyTo(output)
                    output.flush()
                    parcelFileDescriptor.fileDescriptor.sync()
                }
            }
        }
    }

    private suspend fun recordFailure(
        issue: BackupIssue,
        retryable: Boolean,
    ): BackupOperationResult.Failure {
        val lastSuccess = settingsRepository.settings.first().backupStatus.lastSuccess
        settingsRepository.setBackupStatus(
            BackupStatus(
                state = BackupState.NEEDS_ATTENTION,
                issue = issue,
                lastSuccess = lastSuccess,
            ),
        )
        return BackupOperationResult.Failure(issue, retryable)
    }

    private fun newStagingFile(prefix: String): File {
        val directory = File(context.noBackupFilesDir, STAGING_DIRECTORY).apply { mkdirs() }
        return File.createTempFile(prefix, ".tmp", directory)
    }

    private fun cleanupAbandonedStagingFiles() {
        val directory = File(context.noBackupFilesDir, STAGING_DIRECTORY)
        directory.listFiles()?.forEach(File::delete)
    }

    private fun releasePersistedPermission(uri: Uri) {
        runCatching { contentResolver.releasePersistableUriPermission(uri, PERSISTED_URI_FLAGS) }
    }

    private data class PendingRestore(
        val preview: RestorePreview,
        val container: File,
        val backupKey: ByteArray,
        val vault: HealthVault,
        val manifest: BackupManifest,
    ) {
        fun dispose() {
            backupKey.fill(0)
            container.delete()
        }
    }

    companion object {
        const val BACKUP_WORK_NAME = "portable-vault-backup"
        const val BACKUP_WORK_TAG = "portable-vault-backups"
        val BACKUP_CHANGE_COALESCE_DELAY: Duration = Duration.ofSeconds(30)

        private const val STAGING_DIRECTORY = "backup-staging"
        private const val BACKUP_STAGING_PREFIX = "backup-write-"
        private const val RESTORE_STAGING_PREFIX = "backup-restore-"
        private const val PERSISTED_URI_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}

private fun InputStream.copyBoundedTo(output: OutputStream, maximumBytes: Long): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var copied = 0L
    try {
        while (true) {
            val read = read(buffer)
            if (read < 0) return copied
            copied = Math.addExact(copied, read.toLong())
            if (copied > maximumBytes) {
                throw BackupCorruptionException("Backup container exceeds the supported limit")
            }
            output.write(buffer, 0, read)
        }
    } finally {
        buffer.fill(0)
    }
}
