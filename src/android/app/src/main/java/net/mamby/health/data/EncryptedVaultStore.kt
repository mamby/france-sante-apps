package net.mamby.health.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.UnsupportedVaultVersionException
import net.mamby.health.core.model.requireValid
import net.mamby.health.crypto.VaultCipher
import net.mamby.health.crypto.VaultCiphertextException
import net.mamby.health.crypto.VaultKeyProvider

@Singleton
class EncryptedVaultStore @Inject constructor(
    @ApplicationContext context: Context,
    private val keyProvider: VaultKeyProvider,
    private val cipher: VaultCipher,
) : VaultStore {
    private val layout = LocalVaultLayout(context.noBackupFilesDir)
    private val mutex = Mutex()

    override suspend fun load(): HealthVault? = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val generation = layout.activeGeneration()
                if (generation == null) {
                    layout.cleanupInactive(null)
                    return@withLock null
                }
                val metadata = layout.metadataFile(generation)
                if (!metadata.isFile) throw VaultCorruptionException("Encrypted vault metadata is missing.")

                val envelope = layout.read(metadata)
                val key = keyProvider.requireVaultKey()
                val plaintext = cipher.decrypt(envelope, key, METADATA_ASSOCIATED_DATA)
                try {
                    VaultJson.decode(plaintext).requireValid().also {
                        runCatching { layout.cleanupInactive(generation.id) }
                    }
                } finally {
                    plaintext.fill(0)
                }
            } catch (error: UnsupportedVaultVersionException) {
                throw error
            } catch (error: VaultCorruptionException) {
                throw error
            } catch (error: GeneralSecurityException) {
                throw VaultCorruptionException("Encrypted vault authentication failed.", error)
            } catch (error: VaultCiphertextException) {
                throw VaultCorruptionException("Encrypted vault envelope is invalid.", error)
            } catch (error: SerializationException) {
                throw VaultCorruptionException("Encrypted vault metadata is invalid.", error)
            } catch (error: IllegalArgumentException) {
                throw VaultCorruptionException("Encrypted vault metadata failed validation.", error)
            }
        }
    }

    override suspend fun save(vault: HealthVault): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            vault.requireValid()
            val existing = layout.activeGeneration()
            val generation = existing ?: layout.createGeneration()
            try {
                val envelope = encryptMetadata(vault)
                layout.writeAtomic(layout.metadataFile(generation), envelope)
                if (existing == null) layout.activate(generation)
                runCatching { layout.cleanupInactive(generation.id) }
            } catch (error: Throwable) {
                if (existing == null) runCatching { layout.removeGeneration(generation) }
                throw error
            }
        }
    }

    override suspend fun replaceAtomically(
        vault: HealthVault,
        documentBlobs: List<RestoreDocumentBlob>,
    ): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            vault.requireValid()
            validateRestoreSources(vault, documentBlobs)
            val generation = layout.createGeneration()
            var activated = false
            try {
                documentBlobs.forEach { source ->
                    val plaintext = source.openStream().use { input ->
                        input.readBounded(source.expectedSizeBytes)
                    }
                    try {
                        val encrypted = cipher.encrypt(
                            plaintext,
                            keyProvider.requireVaultKey(),
                            blobAssociatedData(source.blobId),
                        )
                        layout.writeAtomic(layout.blobFile(generation, source.blobId), encrypted)
                    } finally {
                        plaintext.fill(0)
                    }
                }
                layout.writeAtomic(layout.metadataFile(generation), encryptMetadata(vault))
                layout.activate(generation)
                activated = true
                runCatching { layout.cleanupInactive(generation.id) }
            } catch (error: Throwable) {
                if (!activated) runCatching { layout.removeGeneration(generation) }
                throw error
            }
        }
    }

    override suspend fun delete(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            var fileFailure: Throwable? = null
            try {
                layout.deleteAll()
            } catch (error: Throwable) {
                fileFailure = error
            } finally {
                keyProvider.deleteKey()
            }
            fileFailure?.let { throw it }
        }
    }

    private suspend fun encryptMetadata(vault: HealthVault): ByteArray {
        val plaintext = VaultJson.encode(vault)
        return try {
            cipher.encrypt(plaintext, keyProvider.requireVaultKey(), METADATA_ASSOCIATED_DATA)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun validateRestoreSources(vault: HealthVault, sources: List<RestoreDocumentBlob>) {
        val expected = vault.documents.associateBy { it.blobId }
        if (sources.map(RestoreDocumentBlob::blobId).toSet().size != sources.size) {
            throw VaultValidationRestoreException("Restore contains duplicate document blobs.")
        }
        if (sources.map(RestoreDocumentBlob::blobId).toSet() != expected.keys) {
            throw VaultValidationRestoreException("Restore document blobs do not match vault metadata.")
        }
        sources.forEach { source ->
            val document = expected.getValue(source.blobId)
            if (source.expectedSizeBytes != document.sizeBytes) {
                throw VaultValidationRestoreException("Restore document size does not match vault metadata.")
            }
            if (source.expectedSizeBytes !in 0..DocumentImportPolicy.MAX_DOCUMENT_BYTES) {
                throw VaultValidationRestoreException("Restore document exceeds the supported size limit.")
            }
        }
    }

    private fun InputStream.readBounded(expectedSize: Long): ByteArray {
        val output = SensitiveByteArrayOutputStream(
            expectedSize.coerceAtMost(BUFFER_SIZE_BYTES.toLong()).toInt(),
        )
        val buffer = ByteArray(BUFFER_SIZE_BYTES)
        var total = 0L
        try {
            while (true) {
                val read = read(buffer)
                if (read < 0) break
                total += read
                if (total > DocumentImportPolicy.MAX_DOCUMENT_BYTES || total > expectedSize) {
                    throw VaultValidationRestoreException("Restore document has an invalid size.")
                }
                output.write(buffer, 0, read)
            }
            if (total != expectedSize) {
                throw VaultValidationRestoreException("Restore document has an invalid size.")
            }
            return output.takeBytes()
        } finally {
            buffer.fill(0)
            output.close()
        }
    }

    private class VaultValidationRestoreException(message: String) : IllegalArgumentException(message)

    companion object {
        private const val BUFFER_SIZE_BYTES = 16 * 1024
        private val METADATA_ASSOCIATED_DATA =
            "net.mamby.health/local-vault/metadata/v1".toByteArray(StandardCharsets.UTF_8)

        internal fun blobAssociatedData(blobId: UUID): ByteArray =
            "net.mamby.health/local-vault/blob/v1/$blobId".toByteArray(StandardCharsets.UTF_8)
    }
}
