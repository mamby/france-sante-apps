package net.mamby.health.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import net.mamby.health.crypto.VaultCipher
import net.mamby.health.crypto.VaultKeyProvider

class BackupKeyDeriver @Inject constructor() {
    fun derive(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        require(isAcceptablePassphrase(passphrase)) {
            "Backup passphrase must contain at least $MIN_PASSPHRASE_LENGTH characters"
        }
        require(salt.size == SALT_SIZE_BYTES) { "Backup salt must be 128 bits" }
        require(iterations in MIN_ITERATIONS..MAX_ACCEPTED_ITERATIONS) {
            "Unsupported backup key-derivation cost"
        }
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_SIZE_BITS)
        return try {
            SecretKeyFactory.getInstance(JCA_ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val JCA_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val ALGORITHM_LABEL = "PBKDF2-HMAC-SHA256"
        const val ITERATIONS = 600_000
        const val MIN_ITERATIONS = 600_000
        const val MAX_ACCEPTED_ITERATIONS = 2_000_000
        const val MIN_PASSPHRASE_LENGTH = 12
        const val SALT_SIZE_BYTES = 16
        const val KEY_SIZE_BYTES = 32
        const val KEY_SIZE_BITS = KEY_SIZE_BYTES * Byte.SIZE_BITS

        fun isAcceptablePassphrase(passphrase: CharArray): Boolean =
            Character.codePointCount(passphrase, 0, passphrase.size) >= MIN_PASSPHRASE_LENGTH

        fun isAcceptablePassphrase(passphrase: CharSequence): Boolean =
            Character.codePointCount(passphrase, 0, passphrase.length) >= MIN_PASSPHRASE_LENGTH
    }
}

class PortableBackupCryptography @Inject constructor(
    private val keyDeriver: BackupKeyDeriver,
    private val secureRandom: SecureRandom,
) {
    fun newBackupKey(): ByteArray = ByteArray(BackupKeyDeriver.KEY_SIZE_BYTES)
        .also(secureRandom::nextBytes)

    fun newSalt(): ByteArray = ByteArray(BackupKeyDeriver.SALT_SIZE_BYTES)
        .also(secureRandom::nextBytes)

    fun wrapForPassphrase(
        backupKey: ByteArray,
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int = BackupKeyDeriver.ITERATIONS,
    ): ByteArray {
        validateBackupKey(backupKey)
        val derivedKey = keyDeriver.derive(passphrase, salt, iterations)
        return try {
            encrypt(backupKey, derivedKey, PASSPHRASE_WRAP_AAD)
        } finally {
            derivedKey.fill(0)
        }
    }

    @Throws(WrongBackupPassphraseException::class)
    fun unwrapFromPassphrase(
        envelope: ByteArray,
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray {
        val derivedKey = keyDeriver.derive(passphrase, salt, iterations)
        return try {
            decrypt(envelope, derivedKey, PASSPHRASE_WRAP_AAD).also(::validateBackupKey)
        } catch (error: AEADBadTagException) {
            throw WrongBackupPassphraseException(error)
        } finally {
            derivedKey.fill(0)
        }
    }

    fun encrypt(plaintext: ByteArray, key: ByteArray, associatedData: ByteArray): ByteArray {
        validateBackupKey(key)
        val nonce = ByteArray(NONCE_SIZE_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE_BITS, nonce))
            updateAAD(associatedData)
        }
        val ciphertext = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(nonce.size + ciphertext.size)
            .put(nonce)
            .put(ciphertext)
            .array()
    }

    fun decrypt(envelope: ByteArray, key: ByteArray, associatedData: ByteArray): ByteArray {
        validateBackupKey(key)
        if (envelope.size < NONCE_SIZE_BYTES + TAG_SIZE_BYTES) {
            throw BackupCorruptionException("Encrypted backup entry is truncated")
        }
        val nonce = envelope.copyOfRange(0, NONCE_SIZE_BYTES)
        val ciphertext = envelope.copyOfRange(NONCE_SIZE_BYTES, envelope.size)
        return Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE_BITS, nonce))
            updateAAD(associatedData)
            doFinal(ciphertext)
        }
    }

    fun newEncryptingCipher(key: ByteArray, associatedData: ByteArray): Pair<ByteArray, Cipher> {
        validateBackupKey(key)
        val nonce = ByteArray(NONCE_SIZE_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE_BITS, nonce))
            updateAAD(associatedData)
        }
        return nonce to cipher
    }

    fun newDecryptingCipher(
        key: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
    ): Cipher {
        validateBackupKey(key)
        require(nonce.size == NONCE_SIZE_BYTES) { "Invalid backup nonce" }
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE_BITS, nonce))
            updateAAD(associatedData)
        }
    }

    private fun validateBackupKey(key: ByteArray) {
        require(key.size == BackupKeyDeriver.KEY_SIZE_BYTES) { "Backup key must be 256 bits" }
    }

    companion object {
        const val NONCE_SIZE_BYTES = 12
        const val TAG_SIZE_BYTES = 16
        const val TAG_SIZE_BITS = TAG_SIZE_BYTES * Byte.SIZE_BITS
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val PASSPHRASE_WRAP_AAD = "phvbackup:1:key-wrap".encodeToByteArray()

        fun encodeBase64(value: ByteArray): String = Base64.getEncoder().encodeToString(value)

        fun decodeBase64(value: String): ByteArray = try {
            Base64.getDecoder().decode(value)
        } catch (error: IllegalArgumentException) {
            throw BackupCorruptionException("Backup contains invalid Base64 data", error)
        }
    }
}

interface LocalBackupKeyProtector {
    suspend fun wrap(backupKey: ByteArray): ByteArray

    suspend fun unwrap(envelope: ByteArray): ByteArray
}

@Singleton
class VaultCipherBackupKeyProtector @Inject constructor(
    private val vaultCipher: VaultCipher,
    private val vaultKeyProvider: VaultKeyProvider,
) : LocalBackupKeyProtector {
    override suspend fun wrap(backupKey: ByteArray): ByteArray =
        vaultCipher.encrypt(
            plaintext = backupKey,
            key = vaultKeyProvider.getOrCreateKey(),
            associatedData = LOCAL_KEY_WRAP_AAD,
        )

    override suspend fun unwrap(envelope: ByteArray): ByteArray =
        vaultCipher.decrypt(
            envelope = envelope,
            key = vaultKeyProvider.getOrCreateKey(),
            associatedData = LOCAL_KEY_WRAP_AAD,
        ).also { key ->
            if (key.size != BackupKeyDeriver.KEY_SIZE_BYTES) {
                key.fill(0)
                throw BackupCorruptionException("Locally wrapped backup key has an invalid size")
            }
        }

    private companion object {
        val LOCAL_KEY_WRAP_AAD = "personal-health-vault:backup-key:v1".encodeToByteArray()
    }
}
