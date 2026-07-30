package net.mamby.health.backup

import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptographyTest {
    private val deriver = BackupKeyDeriver()

    @Test
    fun keyDerivationIsDeterministicForTheSameInputs() {
        val salt = ByteArray(BackupKeyDeriver.SALT_SIZE_BYTES) { it.toByte() }
        val first = deriver.derive(
            "a sufficiently long passphrase".toCharArray(),
            salt,
            BackupKeyDeriver.ITERATIONS,
        )
        val second = deriver.derive(
            "a sufficiently long passphrase".toCharArray(),
            salt,
            BackupKeyDeriver.ITERATIONS,
        )

        try {
            assertArrayEquals(first, second)
            assertFalse(first.all { it == 0.toByte() })
        } finally {
            first.fill(0)
            second.fill(0)
        }
    }

    @Test
    fun wrappedKeyRejectsTheWrongPassphrase() {
        val cryptography = PortableBackupCryptography(deriver, SecureRandom())
        val backupKey = ByteArray(BackupKeyDeriver.KEY_SIZE_BYTES) { (it + 1).toByte() }
        val salt = ByteArray(BackupKeyDeriver.SALT_SIZE_BYTES) { (it + 2).toByte() }
        val envelope = cryptography.wrapForPassphrase(
            backupKey,
            "correct horse battery staple".toCharArray(),
            salt,
        )

        try {
            assertThrows(WrongBackupPassphraseException::class.java) {
                cryptography.unwrapFromPassphrase(
                    envelope,
                    "incorrect passphrase".toCharArray(),
                    salt,
                    BackupKeyDeriver.ITERATIONS,
                )
            }
        } finally {
            backupKey.fill(0)
            salt.fill(0)
            envelope.fill(0)
        }
    }
}
