package net.mamby.health.crypto

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AesGcmVaultCipher @Inject constructor() : VaultCipher {
    override fun encrypt(
        plaintext: ByteArray,
        key: SecretKey,
        associatedData: ByteArray,
    ): ByteArray {
        validateKey(key)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key)
            updateAAD(associatedData)
        }
        val nonce = cipher.iv
        require(nonce.size == NONCE_SIZE_BYTES) {
            "Vault cipher provider must generate a 96-bit GCM nonce."
        }
        val ciphertextAndTag = cipher.doFinal(plaintext)

        return ByteBuffer.allocate(HEADER_SIZE_BYTES + nonce.size + ciphertextAndTag.size)
            .put(MAGIC)
            .put(ENVELOPE_VERSION.toByte())
            .put(nonce.size.toByte())
            .putInt(ciphertextAndTag.size)
            .put(nonce)
            .put(ciphertextAndTag)
            .array()
    }

    override fun decrypt(
        envelope: ByteArray,
        key: SecretKey,
        associatedData: ByteArray,
    ): ByteArray {
        validateKey(key)
        if (envelope.size < HEADER_SIZE_BYTES + NONCE_SIZE_BYTES + TAG_SIZE_BYTES) {
            throw VaultCiphertextException("Encrypted envelope is truncated.")
        }

        val buffer = ByteBuffer.wrap(envelope)
        val descriptor = parseHeader(buffer)
        if (buffer.remaining() != descriptor.nonceSize + descriptor.ciphertextSize) {
            throw VaultCiphertextException("Encrypted envelope has an invalid ciphertext size.")
        }

        val nonce = ByteArray(descriptor.nonceSize).also(buffer::get)
        val ciphertextAndTag = ByteArray(descriptor.ciphertextSize).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, nonce))
            updateAAD(associatedData)
        }
        return cipher.doFinal(ciphertextAndTag)
    }

    override fun decryptTo(
        envelope: InputStream,
        key: SecretKey,
        associatedData: ByteArray,
        output: OutputStream,
    ): Long {
        validateKey(key)
        val header = ByteArray(HEADER_SIZE_BYTES)
        readExact(envelope, header)
        val descriptor = parseHeader(ByteBuffer.wrap(header))
        val nonce = ByteArray(descriptor.nonceSize)
        val encryptedBuffer = ByteArray(STREAM_BUFFER_SIZE_BYTES)
        return try {
            readExact(envelope, nonce)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BITS, nonce))
                updateAAD(associatedData)
            }
            var remaining = descriptor.ciphertextSize
            var plaintextBytes = 0L
            while (remaining > 0) {
                val read = envelope.read(encryptedBuffer, 0, minOf(encryptedBuffer.size, remaining))
                if (read < 0) throw VaultCiphertextException("Encrypted envelope is truncated.")
                if (read == 0) continue
                remaining -= read
                plaintextBytes += writeAndClear(output, cipher.update(encryptedBuffer, 0, read))
            }
            if (envelope.read() != -1) {
                throw VaultCiphertextException("Encrypted envelope has an invalid ciphertext size.")
            }
            plaintextBytes + writeAndClear(output, cipher.doFinal())
        } finally {
            header.fill(0)
            nonce.fill(0)
            encryptedBuffer.fill(0)
        }
    }

    private fun parseHeader(buffer: ByteBuffer): EnvelopeDescriptor {
        val magic = ByteArray(MAGIC.size).also(buffer::get)
        if (!magic.contentEquals(MAGIC)) {
            throw VaultCiphertextException("Encrypted envelope has an invalid header.")
        }
        val version = buffer.get().toInt() and 0xff
        if (version != ENVELOPE_VERSION) {
            throw VaultCiphertextException("Unsupported encrypted envelope version: $version")
        }
        val nonceSize = buffer.get().toInt() and 0xff
        if (nonceSize != NONCE_SIZE_BYTES) {
            throw VaultCiphertextException("Encrypted envelope has an invalid nonce size.")
        }
        val ciphertextSize = buffer.int
        if (ciphertextSize < TAG_SIZE_BYTES) {
            throw VaultCiphertextException("Encrypted envelope has an invalid ciphertext size.")
        }
        return EnvelopeDescriptor(nonceSize, ciphertextSize)
    }

    private fun readExact(input: InputStream, destination: ByteArray) {
        var offset = 0
        while (offset < destination.size) {
            val read = input.read(destination, offset, destination.size - offset)
            if (read < 0) throw VaultCiphertextException("Encrypted envelope is truncated.")
            if (read == 0) continue
            offset += read
        }
    }

    private fun writeAndClear(output: OutputStream, plaintext: ByteArray?): Long {
        if (plaintext == null || plaintext.isEmpty()) return 0
        return try {
            output.write(plaintext)
            plaintext.size.toLong()
        } finally {
            plaintext.fill(0)
        }
    }

    private fun validateKey(key: SecretKey) {
        require(key.algorithm.equals("AES", ignoreCase = true)) { "Vault key must be an AES key." }
        key.encoded?.let { encoded ->
            require(encoded.size == KEY_SIZE_BYTES) { "Vault key must be a 256-bit AES key." }
        }
    }

    companion object {
        const val KEY_SIZE_BYTES: Int = 32
        const val NONCE_SIZE_BYTES: Int = 12
        const val TAG_SIZE_BYTES: Int = 16
        const val TAG_SIZE_BITS: Int = TAG_SIZE_BYTES * Byte.SIZE_BITS
        const val ENVELOPE_VERSION: Int = 1

        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val HEADER_SIZE_BYTES = 10
        private const val STREAM_BUFFER_SIZE_BYTES = 64 * 1024
        private val MAGIC = byteArrayOf('P'.code.toByte(), 'H'.code.toByte(), 'V'.code.toByte(), 'C'.code.toByte())
    }

    private data class EnvelopeDescriptor(
        val nonceSize: Int,
        val ciphertextSize: Int,
    )
}
