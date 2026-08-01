package net.mamby.health.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import net.mamby.health.crypto.AesGcmVaultCipher
import net.mamby.health.crypto.VaultKeyProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedSelectedProfileStoreInstrumentedTest {
    @Test
    fun selectionIsAuthenticatedEncryptedAndCorruptionFallsBackToNull() = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(base.cacheDir, "selected-profile-${UUID.randomUUID()}").apply {
            check(mkdirs())
            check(File(this, "health-vault").mkdirs())
        }
        val context = object : ContextWrapper(base) {
            override fun getNoBackupFilesDir(): File = directory
        }
        val store = EncryptedSelectedProfileStore(context, FixedKeyProvider, AesGcmVaultCipher())
        val profileId = UUID.fromString("f582987b-f0d7-4707-a9d7-505e94a6df81")

        store.save(profileId)

        val persisted = File(directory, "health-vault/selected-profile.phvs")
        assertEquals(profileId, store.load())
        assertFalse(persisted.readBytes().decodeToString().contains(profileId.toString()))

        persisted.writeBytes(byteArrayOf(1, 2, 3, 4))
        assertNull(store.load())
        assertFalse(persisted.exists())
        directory.deleteRecursively()
        Unit
    }

    private object FixedKeyProvider : VaultKeyProvider {
        private val key: SecretKey = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")
        override suspend fun getOrCreateKey(): SecretKey = key
        override suspend fun deleteKey() = Unit
    }
}
