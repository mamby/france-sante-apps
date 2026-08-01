package net.mamby.health.core.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import net.mamby.health.data.VaultCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class HealthVaultSerializationTest {
    private val now = Instant.parse("2026-07-30T08:15:30Z")
    private val firstId = UUID.fromString("2361588e-ee3f-466b-b054-6d8f4f132c60")
    private val secondId = UUID.fromString("5b49ac57-8a98-41c4-b66b-b6f69cfd9b04")
    private val documentId = UUID.fromString("9eb4c6cb-7a77-4c2f-891b-d437fe7a7d98")
    private val blobId = UUID.fromString("27d14e33-91aa-47d5-bf19-fd7beb082d96")

    @Test
    fun schemaV2RoundTripsMultipleProfilesWithoutDerivedViews() {
        val document = MedicalDocument(
            id = documentId,
            title = "Lab result",
            category = DocumentCategory.LAB_RESULTS,
            documentDate = LocalDate.of(2026, 7, 28),
            source = "Laboratory",
            blobId = blobId,
            mimeType = "application/pdf",
            sizeBytes = 1_024,
            updatedAt = now,
        )
        val vault = HealthVault(
            revision = 7,
            profiles = listOf(
                ProfileRecord(
                    profile = HealthProfile(firstId, "Amina", bloodType = "O+", lastUpdatedAt = now),
                    documents = listOf(document),
                ),
                ProfileRecord(HealthProfile(secondId, "Sam", lastUpdatedAt = now)),
            ),
            updatedAt = now,
        ).requireValid()

        val encoded = VaultCodec.encode(vault)
        val decoded = VaultCodec.decode(encoded)

        assertEquals(2, decoded.sourceVersion)
        assertEquals(vault, decoded.vault)
        assertEquals("O+", decoded.vault.profiles.first().summary().bloodType)
        assertEquals(listOf(documentId), decoded.vault.profiles.first().index().map(VaultItem::id))
        val text = encoded.decodeToString()
        assertFalse(text.contains("\"summary\""))
        assertFalse(text.contains("\"vaultItems\""))
    }

    @Test
    fun exactV1PayloadMigratesToOneProfilePreservingIdsOrderAndRevision() {
        val payload = """
            {
              "version": 1,
              "revision": 3,
              "profile": {
                "id": "$firstId",
                "displayName": "Amina",
                "lastUpdatedAt": "$now"
              },
              "documents": [{
                "id": "$documentId",
                "title": "Lab",
                "category": "LAB_RESULTS",
                "documentDate": "2026-07-28",
                "source": "Clinic",
                "blobId": "$blobId",
                "mimeType": "application/pdf",
                "sizeBytes": 10,
                "updatedAt": "$now"
              }],
              "updatedAt": "$now"
            }
        """.trimIndent().encodeToByteArray()

        val decoded = VaultCodec.decode(payload)

        assertEquals(1, decoded.sourceVersion)
        assertEquals(2, decoded.vault.version)
        assertEquals(3L, decoded.vault.revision)
        assertEquals(firstId, decoded.vault.profiles.single().profile.id)
        assertEquals(documentId, decoded.vault.profiles.single().documents.single().id)
        assertEquals(blobId, decoded.vault.profiles.single().documents.single().blobId)
    }

    @Test
    fun futureVersionIsRejected() {
        assertThrows(UnsupportedVaultVersionException::class.java) {
            VaultCodec.decode("""{"version":99}""".encodeToByteArray())
        }
        assertThrows(UnsupportedVaultVersionException::class.java) {
            VaultCodec.encode(
                HealthVault.empty(now, firstId, "Amina").copy(version = 1),
            )
        }
    }

    @Test
    fun validationRejectsGlobalIdCollisionsAndCrossProfileDocumentLinks() {
        val sharedId = UUID.randomUUID()
        val document = MedicalDocument(
            sharedId,
            "Report",
            DocumentCategory.REPORTS,
            LocalDate.of(2026, 7, 1),
            "Clinic",
            blobId = UUID.randomUUID(),
            mimeType = "application/pdf",
            sizeBytes = 5,
            updatedAt = now,
        )
        val invalid = HealthVault(
            revision = 0,
            profiles = listOf(
                ProfileRecord(HealthProfile(firstId, "Amina", lastUpdatedAt = now), documents = listOf(document)),
                ProfileRecord(
                    HealthProfile(secondId, "Sam", lastUpdatedAt = now),
                    appointments = listOf(
                        Appointment(
                            UUID.randomUUID(),
                            "Visit",
                            "Clinician",
                            "Clinic",
                            now,
                            relatedDocumentIds = listOf(sharedId),
                            updatedAt = now,
                        ),
                    ),
                ),
            ),
            updatedAt = now,
        )

        assertThrows(IllegalArgumentException::class.java) { invalid.requireValid() }
    }

    @Test
    fun backupDocumentFlatteningPreservesProfileThenListOrder() {
        val firstDocument = document(UUID.randomUUID(), UUID.randomUUID(), "First")
        val secondDocument = document(UUID.randomUUID(), UUID.randomUUID(), "Second")
        val thirdDocument = document(UUID.randomUUID(), UUID.randomUUID(), "Third")
        val vault = HealthVault(
            revision = 1,
            profiles = listOf(
                ProfileRecord(
                    HealthProfile(firstId, "Amina", lastUpdatedAt = now),
                    documents = listOf(firstDocument, secondDocument),
                ),
                ProfileRecord(
                    HealthProfile(secondId, "Sam", lastUpdatedAt = now),
                    documents = listOf(thirdDocument),
                ),
            ),
            updatedAt = now,
        ).requireValid()

        assertEquals(
            listOf(firstDocument.id, secondDocument.id, thirdDocument.id),
            vault.allDocuments().map(MedicalDocument::id),
        )
        assertEquals(2, vault.profiles.size)
    }

    private fun document(id: UUID, blobId: UUID, title: String) = MedicalDocument(
        id = id,
        title = title,
        category = DocumentCategory.REPORTS,
        documentDate = LocalDate.of(2026, 7, 1),
        source = "Clinic",
        blobId = blobId,
        mimeType = "application/pdf",
        sizeBytes = 1,
        updatedAt = now,
    )
}
