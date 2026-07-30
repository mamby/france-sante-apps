package net.mamby.health.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.mamby.health.crypto.VaultCipher
import net.mamby.health.crypto.VaultCiphertextException
import net.mamby.health.crypto.VaultKeyProvider

@Singleton
class EncryptedDocumentBlobStore @Inject constructor(
    @ApplicationContext context: Context,
    private val keyProvider: VaultKeyProvider,
    private val cipher: VaultCipher,
) : DocumentBlobStore {
    private val layout = LocalVaultLayout(context.noBackupFilesDir)
    private val mutex = Mutex()

    override suspend fun stage(blobId: UUID, plaintext: ByteArray): StagedDocumentBlob =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (plaintext.size.toLong() > DocumentImportPolicy.MAX_DOCUMENT_BYTES) {
                    throw IllegalArgumentException("Document exceeds the supported size limit.")
                }
                val generation = layout.activeGeneration()
                    ?: throw IllegalStateException("A real vault must exist before importing documents.")
                val token = UUID.randomUUID()
                val encrypted = cipher.encrypt(
                    plaintext,
                    keyProvider.requireVaultKey(),
                    EncryptedVaultStore.blobAssociatedData(blobId),
                )
                layout.writeAtomic(layout.stagedBlobFile(generation, token), encrypted)
                StagedDocumentBlob(blobId, generation.id, token)
            }
        }

    override suspend fun commit(stagedBlob: StagedDocumentBlob): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val active = layout.activeGeneration()
                ?: throw IllegalStateException("A real vault must exist before committing documents.")
            if (active.id != stagedBlob.generationId) {
                throw IllegalStateException("The vault changed while a document import was staged.")
            }
            val source = layout.stagedBlobFile(active, stagedBlob.token)
            if (!source.isFile) throw VaultStorageException("Staged document blob is missing.")
            layout.moveAtomically(source, layout.blobFile(active, stagedBlob.blobId))
        }
    }

    override suspend fun discard(stagedBlob: StagedDocumentBlob): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            layout.stagedBlobFile(layout.generation(stagedBlob.generationId), stagedBlob.token).delete()
        }
    }

    override suspend fun read(blobId: UUID): ByteArray? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val generation = layout.activeGeneration() ?: return@withLock null
            val file = layout.blobFile(generation, blobId)
            if (!file.isFile) return@withLock null
            try {
                cipher.decrypt(
                    layout.read(file),
                    keyProvider.requireVaultKey(),
                    EncryptedVaultStore.blobAssociatedData(blobId),
                )
            } catch (error: GeneralSecurityException) {
                throw VaultCorruptionException("Encrypted document authentication failed.", error)
            } catch (error: VaultCiphertextException) {
                throw VaultCorruptionException("Encrypted document envelope is invalid.", error)
            }
        }
    }

    override suspend fun copyTo(blobId: UUID, output: OutputStream): Long =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val generation = layout.activeGeneration() ?: return@withLock 0
                val file = layout.blobFile(generation, blobId)
                if (!file.isFile) return@withLock 0
                try {
                    file.inputStream().buffered().use { input ->
                        cipher.decryptTo(
                            input,
                            keyProvider.requireVaultKey(),
                            EncryptedVaultStore.blobAssociatedData(blobId),
                            output,
                        )
                    }
                } catch (error: GeneralSecurityException) {
                    throw VaultCorruptionException("Encrypted document authentication failed.", error)
                } catch (error: VaultCiphertextException) {
                    throw VaultCorruptionException("Encrypted document envelope is invalid.", error)
                }
            }
        }

    override suspend fun delete(blobId: UUID): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            layout.activeGeneration()?.let { layout.blobFile(it, blobId).delete() }
        }
    }

    override suspend fun listIds(): Set<UUID> = withContext(Dispatchers.IO) {
        mutex.withLock { listIdsUnlocked() }
    }

    override suspend fun cleanupOrphans(referencedBlobIds: Set<UUID>): Unit =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val generation = layout.activeGeneration() ?: return@withLock
                layout.stagingDirectory(generation).listFiles().orEmpty().forEach { it.delete() }
                val present = mutableSetOf<UUID>()
                layout.blobsDirectory(generation).listFiles().orEmpty().forEach { file ->
                    val id = file.name
                        .takeIf { it.endsWith(LocalVaultLayout.BLOB_FILE_SUFFIX) }
                        ?.removeSuffix(LocalVaultLayout.BLOB_FILE_SUFFIX)
                        ?.let { value -> runCatching { UUID.fromString(value) }.getOrNull() }
                    if (id == null || id !in referencedBlobIds) {
                        file.delete()
                    } else {
                        present += id
                    }
                }
                if (!present.containsAll(referencedBlobIds)) {
                    throw VaultCorruptionException("One or more encrypted document blobs are missing.")
                }
            }
        }

    private fun listIdsUnlocked(): Set<UUID> {
        val generation = layout.activeGeneration() ?: return emptySet()
        return layout.blobsDirectory(generation).listFiles().orEmpty().mapNotNullTo(mutableSetOf()) { file ->
            file.name
                .takeIf { it.endsWith(LocalVaultLayout.BLOB_FILE_SUFFIX) }
                ?.removeSuffix(LocalVaultLayout.BLOB_FILE_SUFFIX)
                ?.let { value -> runCatching { UUID.fromString(value) }.getOrNull() }
        }
    }
}
