package net.mamby.health.crypto

import java.io.InputStream
import java.io.OutputStream
import javax.crypto.SecretKey

interface VaultCipher {
    fun encrypt(
        plaintext: ByteArray,
        key: SecretKey,
        associatedData: ByteArray,
    ): ByteArray

    fun decrypt(
        envelope: ByteArray,
        key: SecretKey,
        associatedData: ByteArray,
    ): ByteArray

    /**
     * Authenticates and decrypts an envelope into a caller-owned, discardable output stream.
     * Callers must discard the output if this method throws because GCM providers may emit data
     * before validating the final authentication tag.
     */
    fun decryptTo(
        envelope: InputStream,
        key: SecretKey,
        associatedData: ByteArray,
        output: OutputStream,
    ): Long
}

interface VaultKeyProvider {
    suspend fun getOrCreateKey(): SecretKey

    suspend fun deleteKey()
}

class VaultCiphertextException(message: String) : IllegalArgumentException(message)
