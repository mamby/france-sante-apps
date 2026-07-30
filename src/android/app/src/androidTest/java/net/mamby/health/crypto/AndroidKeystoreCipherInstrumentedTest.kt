package net.mamby.health.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.UUID
import javax.crypto.KeyGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreCipherInstrumentedTest {
    @Test
    fun keystoreKey_roundTripsAndRejectsTampering() {
        val alias = "net.mamby.health.instrumented.${UUID.randomUUID()}"
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        try {
            val key = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                .apply {
                    init(
                        KeyGenParameterSpec.Builder(
                            alias,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(AesGcmVaultCipher.KEY_SIZE_BYTES * Byte.SIZE_BITS)
                            .setRandomizedEncryptionRequired(true)
                            .build(),
                    )
                }
                .generateKey()
            val plaintext = "synthetic health record".encodeToByteArray()
            val associatedData = "instrumented-vault-v1".encodeToByteArray()
            val cipher = AesGcmVaultCipher()

            val envelope = cipher.encrypt(plaintext, key, associatedData)
            val decrypted = cipher.decrypt(envelope, key, associatedData)

            assertFalse(envelope.contentEquals(plaintext))
            assertArrayEquals(plaintext, decrypted)

            val tampered = envelope.copyOf().also { bytes ->
                bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
            }
            assertThrows(GeneralSecurityException::class.java) {
                cipher.decrypt(tampered, key, associatedData)
            }

            plaintext.fill(0)
            decrypted.fill(0)
            envelope.fill(0)
            tampered.fill(0)
        } finally {
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
