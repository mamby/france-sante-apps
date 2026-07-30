package net.mamby.health.backup

import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import net.mamby.health.settings.BackupIssue

@Serializable
data class BackupHeader(
    val formatVersion: Int = PortableBackupFormat.VERSION,
    val kdf: String = BackupKeyDeriver.ALGORITHM_LABEL,
    val iterations: Int,
    val saltBase64: String,
    val passphraseWrappedKeyBase64: String,
)

@Serializable
data class BackupDocumentEntry(
    val index: Int,
    val blobId: String,
    val sizeBytes: Long,
) {
    val entryName: String
        get() = "blobs/${index.toString().padStart(8, '0')}.enc"
}

@Serializable
data class BackupManifest(
    val formatVersion: Int = PortableBackupFormat.VERSION,
    val sourceEnvironment: String,
    val vaultSchemaVersion: Int,
    val revision: Long,
    val updatedAt: String,
    val vaultJsonBase64: String,
    val documents: List<BackupDocumentEntry>,
)

data class RestorePreview(
    val token: UUID,
    val sourceEnvironment: String,
    val currentEnvironment: String,
    val revision: Long,
    val updatedAt: Instant,
    val documentCount: Int,
) {
    val requiresCrossFlavorConfirmation: Boolean
        get() = sourceEnvironment != currentEnvironment
}

sealed interface BackupOperationResult {
    data class Success(val completedAt: Instant) : BackupOperationResult

    data object NotConfigured : BackupOperationResult

    data class InvalidPassphrase(val minimumLength: Int) : BackupOperationResult

    data class Failure(
        val issue: BackupIssue,
        val retryable: Boolean,
    ) : BackupOperationResult
}

sealed interface RestorePreparationResult {
    data class Ready(val preview: RestorePreview) : RestorePreparationResult

    data object WrongPassphrase : RestorePreparationResult

    data object Corrupt : RestorePreparationResult

    data object UnsupportedVersion : RestorePreparationResult

    data object DestinationUnavailable : RestorePreparationResult
}

sealed interface RestoreCommitResult {
    data object Success : RestoreCommitResult

    data object NotPrepared : RestoreCommitResult

    data class CrossFlavorConfirmationRequired(val preview: RestorePreview) :
        RestoreCommitResult

    data object FailedSafely : RestoreCommitResult
}

object PortableBackupFormat {
    const val VERSION = 1
    const val HEADER_ENTRY_NAME = "header.json"
    const val MANIFEST_ENTRY_NAME = "manifest.enc"
    const val FILE_EXTENSION = "phvbackup"
    const val MIME_TYPE = "application/vnd.net.mamby.health.backup"
    const val MAX_HEADER_BYTES = 64 * 1024
    const val MAX_MANIFEST_BYTES = 16 * 1024 * 1024
    const val MAX_DOCUMENT_BYTES = 25L * 1024L * 1024L
    const val MAX_DOCUMENT_COUNT = 10_000
    const val MAX_CONTAINER_BYTES = 2L * 1024L * 1024L * 1024L
}

internal class BackupCorruptionException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

internal class UnsupportedBackupException(message: String) : Exception(message)

internal class WrongBackupPassphraseException(cause: Throwable? = null) : Exception(cause)
