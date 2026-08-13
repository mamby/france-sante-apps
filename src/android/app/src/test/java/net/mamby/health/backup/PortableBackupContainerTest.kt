package net.mamby.health.backup

import java.io.File
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.mamby.health.core.model.BuiltInDocumentCategory
import net.mamby.health.core.model.CareDirective
import net.mamby.health.core.model.CareDirectiveKind
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.VaultContact
import net.mamby.health.core.model.asReference
import net.mamby.health.data.VaultCodec
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

    @Test
    fun formatV1ContainerRoundTripsSchemaV6ContactsInvoiceAndDirectiveAttachment(): Unit = runBlocking {
        val target = temporaryFolder.newFile("schema-v6.phvbackup")
        val key = ByteArray(BackupKeyDeriver.KEY_SIZE_BYTES) { (it + 5).toByte() }
        val documentBytes = "encrypted invoice".encodeToByteArray()
        val now = Instant.parse("2026-07-30T12:00:00Z")
        val profileId = UUID.fromString("9a8cf916-3fbe-4d35-bffe-e7dd4530eb67")
        val documentId = UUID.fromString("7946b206-8607-4c68-adc5-f71af3148438")
        val blobId = UUID.fromString("4eb7fcad-dda6-47b8-8dda-7d485d43ca1e")
        val contactId = UUID.fromString("10740464-a7a1-4498-a377-03b94c3ed075")
        val document = MedicalDocument(
            documentId,
            "Hospital invoice",
            BuiltInDocumentCategory.INVOICES_RECEIPTS.asReference(),
            LocalDate.of(2026, 7, 29),
            "Hospital",
            blobId = blobId,
            mimeType = "application/pdf",
            sizeBytes = documentBytes.size.toLong(),
            updatedAt = now,
        )
        val vault = HealthVault(
            revision = 12,
            profiles = listOf(
                ProfileRecord(
                    HealthProfile(profileId, "Owner", lastUpdatedAt = now),
                    documents = listOf(document),
                    directives = listOf(
                        CareDirective(
                            UUID.fromString("90750f8d-5589-4e23-bdb6-0e8676d391b4"),
                            CareDirectiveKind.CARE_PREFERENCE,
                            "Preference",
                            "Personal record",
                            LocalDate.of(2026, 7, 30),
                            listOf(documentId),
                            now,
                        ),
                    ),
                ),
            ),
            contacts = listOf(
                VaultContact(
                    id = contactId,
                    name = "Hospital",
                    phoneNumbers = listOf("+33 1 23 45 67 89"),
                    websites = listOf("https://example.com"),
                    addresses = listOf("12 Main St\nParis"),
                    updatedAt = now,
                ),
            ),
            updatedAt = now,
        )
        val vaultBytes = VaultCodec.encode(vault)
        val backupDocument = BackupDocumentEntry(0, blobId.toString(), documentBytes.size.toLong())
        val manifest = BackupManifest(
            sourceEnvironment = "dev",
            vaultSchemaVersion = HealthVault.CURRENT_VERSION,
            revision = vault.revision,
            updatedAt = vault.updatedAt.toString(),
            vaultJsonBase64 = Base64.getEncoder().encodeToString(vaultBytes),
            documents = listOf(backupDocument),
        )

        try {
            container.write(target, header(), manifest, key) { _, output ->
                output.write(documentBytes)
                documentBytes.size.toLong()
            }
            val restoredManifest = container.readManifest(target, key)
            assertEquals(PortableBackupFormat.VERSION, restoredManifest.formatVersion)
            assertEquals(HealthVault.CURRENT_VERSION, restoredManifest.vaultSchemaVersion)
            val restoredVault = VaultCodec.decode(
                Base64.getDecoder().decode(restoredManifest.vaultJsonBase64),
            ).vault
            assertEquals(vault, restoredVault)
            container.verifyDocuments(target, restoredManifest, key)
        } finally {
            key.fill(0)
            documentBytes.fill(0)
            vaultBytes.fill(0)
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
