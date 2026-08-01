package net.mamby.health.data

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.mamby.health.crypto.VaultCipher
import net.mamby.health.crypto.VaultKeyProvider

@Singleton
class EncryptedSelectedProfileStore @Inject constructor(
    @ApplicationContext context: Context,
    private val keyProvider: VaultKeyProvider,
    private val cipher: VaultCipher,
) : SelectedProfileStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, FILE_PATH))
    private val mutex = Mutex()

    override suspend fun load(): UUID? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!file.baseFile.isFile) return@withLock null
            try {
                val envelope = file.openRead().use { it.readBytes() }
                val plaintext = cipher.decrypt(envelope, keyProvider.requireVaultKey(), ASSOCIATED_DATA)
                try {
                    UUID.fromString(plaintext.toString(StandardCharsets.US_ASCII))
                } finally {
                    plaintext.fill(0)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                file.delete()
                null
            }
        }
    }

    override suspend fun save(profileId: UUID): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val plaintext = profileId.toString().toByteArray(StandardCharsets.US_ASCII)
            try {
                val envelope = cipher.encrypt(plaintext, keyProvider.requireVaultKey(), ASSOCIATED_DATA)
                val output = file.startWrite()
                try {
                    output.write(envelope)
                    file.finishWrite(output)
                } catch (error: Throwable) {
                    file.failWrite(output)
                    throw error
                } finally {
                    envelope.fill(0)
                }
            } finally {
                plaintext.fill(0)
            }
        }
    }

    override suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock { file.delete() }
    }

    private companion object {
        const val FILE_PATH = "health-vault/selected-profile.phvs"
        val ASSOCIATED_DATA =
            "net.mamby.health/local-vault/selected-profile/v1".toByteArray(StandardCharsets.UTF_8)
    }
}
