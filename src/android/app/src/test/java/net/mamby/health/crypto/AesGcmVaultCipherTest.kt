package net.mamby.health.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class AesGcmVaultCipherTest {
    private val cipher = AesGcmVaultCipher()
    private val key = SecretKeySpec(ByteArray(AesGcmVaultCipher.KEY_SIZE_BYTES) { it.toByte() }, "AES")
    private val aad = "metadata/v1".encodeToByteArray()

    @Test
    fun roundTripUsesAUniqueNonceForEveryEnvelope() {
        val plaintext = "private health data".encodeToByteArray()

        val first = cipher.encrypt(plaintext, key, aad)
        val second = cipher.encrypt(plaintext, key, aad)

        assertFalse(first.contentEquals(second))
        assertArrayEquals(plaintext, cipher.decrypt(first, key, aad))
        assertArrayEquals(plaintext, cipher.decrypt(second, key, aad))
    }

    @Test
    fun tamperingOrChangingAssociatedDataFailsAuthentication() {
        val envelope = cipher.encrypt("private".encodeToByteArray(), key, aad)
        val tampered = envelope.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }

        assertThrows(GeneralSecurityException::class.java) {
            cipher.decrypt(tampered, key, aad)
        }
        assertThrows(GeneralSecurityException::class.java) {
            cipher.decrypt(envelope, key, "different-record".encodeToByteArray())
        }
    }

    @Test
    fun streamingDecryptionAuthenticatesAndCopiesWithoutAPlaintextFile() {
        val plaintext = ByteArray(256 * 1024) { index -> (index % 251).toByte() }
        val envelope = cipher.encrypt(plaintext, key, aad)
        val output = ByteArrayOutputStream()

        val copied = cipher.decryptTo(ByteArrayInputStream(envelope), key, aad, output)

        assertEquals(plaintext.size.toLong(), copied)
        assertArrayEquals(plaintext, output.toByteArray())
    }

    @Test
    fun streamingDecryptionRejectsTamperedCiphertext() {
        val envelope = cipher.encrypt("private".encodeToByteArray(), key, aad)
            .also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }

        assertThrows(GeneralSecurityException::class.java) {
            cipher.decryptTo(ByteArrayInputStream(envelope), key, aad, ByteArrayOutputStream())
        }
    }
}
