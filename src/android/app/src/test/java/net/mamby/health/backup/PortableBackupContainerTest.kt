package net.mamby.health.backup

import java.io.File
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PortableBackupContainerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val container = PortableBackupContainer(
        json = Json { encodeDefaults = true },
        cryptography = PortableBackupCryptography(BackupKeyDeriver(), SecureRandom()),
    )

    @Test
    fun containerRoundTripsAuthenticatedManifestAndDocument(): Unit = runBlocking {
        val target = temporaryFolder.newFile("vault.phvbackup")
        val key = ByteArray(BackupKeyDeriver.KEY_SIZE_BYTES) { (it + 3).toByte() }
        val documentBytes = "authenticated document".encodeToByteArray()
        val document = BackupDocumentEntry(0, "123e4567-e89b-12d3-a456-426614174000", documentBytes.size.toLong())
        val manifest = manifest(document)

        try {
            container.write(target, header(), manifest, key) { _, output ->
                output.write(documentBytes)
                documentBytes.size.toLong()
            }

            assertEquals(header(), container.readHeader(target))
            assertEquals(manifest, container.readManifest(target, key))
            container.validateStructure(target, manifest)
            container.verifyDocuments(target, manifest, key)
            val restored = container.openVerifiedDocument(target, document, key).use { it.readBytes() }
            assertArrayEquals(documentBytes, restored)
            restored.fill(0)
        } finally {
            key.fill(0)
            documentBytes.fill(0)
        }
    }

    @Test
    fun authenticatedDocumentTamperingIsRejected(): Unit = runBlocking {
        val original = temporaryFolder.newFile("original.phvbackup")
        val tampered = temporaryFolder.newFile("tampered.phvbackup")
        val key = ByteArray(BackupKeyDeriver.KEY_SIZE_BYTES) { (it + 4).toByte() }
        val documentBytes = "private document".encodeToByteArray()
        val document = BackupDocumentEntry(0, "123e4567-e89b-12d3-a456-426614174001", documentBytes.size.toLong())
        val manifest = manifest(document)

        try {
            container.write(original, header(), manifest, key) { _, output ->
                output.write(documentBytes)
                documentBytes.size.toLong()
            }
            rewriteWithTamperedEntry(original, tampered, document.entryName)

            assertThrows(BackupCorruptionException::class.java) {
                container.verifyDocuments(tampered, manifest, key)
            }
        } finally {
            key.fill(0)
            documentBytes.fill(0)
        }
    }

    private fun header() = BackupHeader(
        iterations = BackupKeyDeriver.ITERATIONS,
        saltBase64 = "AAECAwQFBgcICQoLDA0ODw==",
        passphraseWrappedKeyBase64 = "wrapped-key",
    )

    private fun manifest(document: BackupDocumentEntry) = BackupManifest(
        sourceEnvironment = "dev",
        vaultSchemaVersion = 1,
        revision = 7,
        updatedAt = "2026-07-30T12:00:00Z",
        vaultJsonBase64 = "e30=",
        documents = listOf(document),
    )

    private fun rewriteWithTamperedEntry(source: File, target: File, entryName: String) {
        ZipFile(source).use { inputZip ->
            ZipOutputStream(target.outputStream()).use { outputZip ->
                inputZip.entries().asSequence().forEach { entry ->
                    val bytes = inputZip.getInputStream(entry).use { it.readBytes() }
                    if (entry.name == entryName) {
                        bytes[PortableBackupCryptography.NONCE_SIZE_BYTES] =
                            (bytes[PortableBackupCryptography.NONCE_SIZE_BYTES].toInt() xor 1).toByte()
                    }
                    outputZip.putNextEntry(ZipEntry(entry.name).apply { time = 0L })
                    outputZip.write(bytes)
                    outputZip.closeEntry()
                    bytes.fill(0)
                }
            }
        }
    }
}
