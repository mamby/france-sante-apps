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
    fun schemaV4RoundTripsMultipleProfilesAndVaultWideNotesWithoutDerivedViews() {
        val doctorId = UUID.fromString("34a502d7-23c0-4be5-a8fd-34b56dca82d0")
        val noteId = UUID.fromString("096c83e2-7703-49b4-8d24-e5bf5f9c63e4")
        val measurementId = UUID.fromString("8c918e28-6344-484b-a969-a2d23c109bf3")
        val directiveId = UUID.fromString("6114ffae-bcbc-43a2-845e-cd3798596a1c")
        val identifierId = UUID.fromString("74015d24-2f46-4b83-b2bd-fb83ad25d25a")
        val familyId = UUID.fromString("2c93fa7e-41d8-4ae7-a84d-dd624180b399")
        val categoryId = UUID.fromString("0d47f8cf-e992-4585-b9cd-2413a9f269c4")
        val document = MedicalDocument(
            id = documentId,
            title = "Lab result",
            category = BuiltInDocumentCategory.LAB_RESULTS.asReference(),
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
                    profile = HealthProfile(
                        firstId,
                        "Amina",
                        bloodType = "O+",
                        primaryDoctorEntryId = doctorId,
                        lastUpdatedAt = now,
                    ),
                    documents = listOf(document.copy(category = DocumentCategoryRef.Custom(categoryId), sourceEntryId = doctorId)),
                    measurements = listOf(
                        HealthMeasurement(
                            measurementId,
                            MeasurementTypeRef.BuiltIn(BuiltInMeasurementType.WEIGHT),
                            MeasurementReading.Scalar(
                                72.5,
                                MeasurementUnitRef.BuiltIn(MeasurementUnit.KILOGRAM),
                            ),
                            now,
                            "Morning",
                            now,
                        ),
                    ),
                    careDirectory = listOf(
                        CareDirectoryEntry(doctorId, CareDirectoryKind.DOCTOR, "Dr Martin", updatedAt = now),
                    ),
                    familyHistory = listOf(FamilyHistoryEntry(familyId, "Parent", "Diabetes", 48, updatedAt = now)),
                    directives = listOf(
                        CareDirective(
                            directiveId,
                            CareDirectiveKind.CARE_PREFERENCE,
                            "Preference",
                            "Personal preference text",
                            LocalDate.of(2026, 7, 30),
                            listOf(documentId),
                            now,
                        ),
                    ),
                    healthIdentifiers = listOf(
                        HealthIdentifier(
                            identifierId,
                            HealthIdentifierKind.SOCIAL_SECURITY,
                            "Social security",
                            "secret-value",
                            "Issuer",
                            "FR",
                            updatedAt = now,
                        ),
                    ),
                    customDocumentCategories = listOf(CustomDocumentCategory(categoryId, "Invoices", now)),
                    builtInDocumentCategoryPreferences = listOf(
                        BuiltInDocumentCategoryPreference(BuiltInDocumentCategory.OTHER, "Miscellaneous"),
                    ),
                ),
                ProfileRecord(HealthProfile(secondId, "Sam", lastUpdatedAt = now)),
            ),
            notes = listOf(HealthNote(noteId, "Follow-up", "Context", now, now)),
            updatedAt = now,
        ).requireValid()

        val encoded = VaultCodec.encode(vault)
        val decoded = VaultCodec.decode(encoded)

        assertEquals(4, decoded.sourceVersion)
        assertEquals(vault, decoded.vault)
        assertEquals(noteId, decoded.vault.notes.single().id)
        assertEquals("O+", decoded.vault.profiles.first().summary().bloodType)
        assertEquals(
            listOf(documentId, measurementId, doctorId, familyId, directiveId, identifierId),
            decoded.vault.profiles.first().index().map(VaultItem::id),
        )
        val text = encoded.decodeToString()
        assertFalse(text.contains("\"summary\""))
        assertFalse(text.contains("\"vaultItems\""))
    }

    @Test
    fun exactV3PayloadMigratesProfileNotesToVaultScopeInStableOrder() {
        val firstNote = UUID.fromString("1fd70aaf-9404-45a3-8586-4ba5ecfa2cc2")
        val secondNote = UUID.fromString("e6a40b07-999c-4e73-bbd8-8249b5c18bdc")
        val payload = """
            {
              "version": 3,
              "revision": 12,
              "profiles": [
                {
                  "profile": {"id":"$firstId","displayName":"Amina","lastUpdatedAt":"$now"},
                  "notes": [{
                    "id":"$firstNote","title":"First","body":"One",
                    "notedAt":"$now","updatedAt":"$now"
                  }]
                },
                {
                  "profile": {"id":"$secondId","displayName":"Sam","lastUpdatedAt":"$now"},
                  "notes": [{
                    "id":"$secondNote","title":"Second","body":"Two",
                    "notedAt":"$now","updatedAt":"$now"
                  }]
                }
              ],
              "updatedAt":"$now"
            }
        """.trimIndent().encodeToByteArray()

        val decoded = VaultCodec.decode(payload)

        assertEquals(3, decoded.sourceVersion)
        assertEquals(4, decoded.vault.version)
        assertEquals(12L, decoded.vault.revision)
        assertEquals(listOf(firstNote, secondNote), decoded.vault.notes.map(HealthNote::id))
        assertEquals(listOf(firstId, secondId), decoded.vault.profiles.map { it.profile.id })
    }

    @Test
    fun exactV2PayloadMigratesToV4PreservingProfilesCategoriesAndBlobs() {
        val secondDocumentId = UUID.fromString("a5f1105a-586b-4b91-bcb5-15a174f55c13")
        val secondBlobId = UUID.fromString("4f139214-9a53-4ef8-8cef-5b221b03759e")
        val payload = """
            {
              "version": 2,
              "revision": 11,
              "profiles": [
                {
                  "profile": {"id":"$firstId","displayName":"Amina","lastUpdatedAt":"$now"},
                  "documents": [{
                    "id":"$documentId","title":"Invoice","category":"OTHER",
                    "documentDate":"2026-07-28","source":"Clinic","notes":"Keep this note",
                    "tags":["paid"],"blobId":"$blobId","mimeType":"application/pdf",
                    "sizeBytes":10,"originalFileName":"invoice.pdf","updatedAt":"$now"
                  }]
                },
                {
                  "profile": {"id":"$secondId","displayName":"Sam","lastUpdatedAt":"$now"},
                  "documents": [{
                    "id":"$secondDocumentId","title":"Report","category":"REPORTS",
                    "documentDate":"2026-07-29","source":"Hospital",
                    "blobId":"$secondBlobId","mimeType":"application/pdf","sizeBytes":20,"updatedAt":"$now"
                  }]
                }
              ],
              "updatedAt":"$now"
            }
        """.trimIndent().encodeToByteArray()

        val decoded = VaultCodec.decode(payload)

        assertEquals(2, decoded.sourceVersion)
        assertEquals(4, decoded.vault.version)
        assertEquals(11L, decoded.vault.revision)
        assertEquals(listOf(firstId, secondId), decoded.vault.profiles.map { it.profile.id })
        val firstDocument = decoded.vault.profiles.first().documents.single()
        assertEquals(documentId, firstDocument.id)
        assertEquals(blobId, firstDocument.blobId)
        assertEquals(BuiltInDocumentCategory.OTHER.asReference(), firstDocument.category)
        assertEquals("Keep this note", firstDocument.notes)
        assertEquals(listOf("paid"), firstDocument.tags)
        assertEquals("invoice.pdf", firstDocument.originalFileName)
        assertEquals(secondBlobId, decoded.vault.profiles[1].documents.single().blobId)
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
        assertEquals(4, decoded.vault.version)
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
            BuiltInDocumentCategory.REPORTS.asReference(),
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
    fun validationRejectsInvalidMeasurementsAndCrossProfileDirectoryReferences() {
        val doctorId = UUID.randomUUID()
        val wrongUnit = HealthMeasurement(
            id = UUID.randomUUID(),
            type = MeasurementTypeRef.BuiltIn(BuiltInMeasurementType.WEIGHT),
            reading = MeasurementReading.Scalar(
                Double.NaN,
                MeasurementUnitRef.BuiltIn(MeasurementUnit.CELSIUS),
            ),
            measuredAt = now,
            updatedAt = now,
        )
        val first = ProfileRecord(
            profile = HealthProfile(firstId, "Amina", primaryDoctorEntryId = doctorId, lastUpdatedAt = now),
            measurements = listOf(wrongUnit),
        )
        val second = ProfileRecord(
            profile = HealthProfile(secondId, "Sam", lastUpdatedAt = now),
            careDirectory = listOf(
                CareDirectoryEntry(doctorId, CareDirectoryKind.DOCTOR, "Dr Sam", updatedAt = now),
            ),
        )
        val invalid = HealthVault(revision = 4, profiles = listOf(first, second), updatedAt = now)

        assertThrows(VaultValidationException::class.java) { invalid.requireValid() }

        val finiteButWrongUnit = invalid.copy(
            profiles = listOf(
                first.copy(
                    profile = first.profile.copy(primaryDoctorEntryId = null),
                    measurements = listOf(
                        wrongUnit.copy(
                            reading = MeasurementReading.Scalar(
                                72.0,
                                MeasurementUnitRef.BuiltIn(MeasurementUnit.CELSIUS),
                            ),
                        ),
                    ),
                ),
                second,
            ),
        )
        assertThrows(VaultValidationException::class.java) { finiteButWrongUnit.requireValid() }

        val nonFinite = finiteButWrongUnit.copy(
            profiles = listOf(
                finiteButWrongUnit.profiles.first().copy(
                    measurements = listOf(
                        wrongUnit.copy(
                            reading = MeasurementReading.Scalar(
                                Double.POSITIVE_INFINITY,
                                MeasurementUnitRef.BuiltIn(MeasurementUnit.KILOGRAM),
                            ),
                        ),
                    ),
                ),
                second,
            ),
        )
        assertThrows(VaultValidationException::class.java) { nonFinite.requireValid() }
    }

    @Test
    fun validationRejectsDuplicateNewObjectIdsAcrossProfiles() {
        val sharedId = UUID.randomUUID()
        val invalid = HealthVault(
            revision = 1,
            profiles = listOf(
                ProfileRecord(HealthProfile(firstId, "Amina", lastUpdatedAt = now)),
                ProfileRecord(
                    HealthProfile(secondId, "Sam", lastUpdatedAt = now),
                    healthIdentifiers = listOf(
                        HealthIdentifier(
                            sharedId,
                            HealthIdentifierKind.PATIENT,
                            "Patient number",
                            "value",
                            updatedAt = now,
                        ),
                    ),
                ),
            ),
            notes = listOf(HealthNote(sharedId, "First", "Body", now, now)),
            updatedAt = now,
        )

        assertThrows(VaultValidationException::class.java) { invalid.requireValid() }
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
        category = BuiltInDocumentCategory.REPORTS.asReference(),
        documentDate = LocalDate.of(2026, 7, 1),
        source = "Clinic",
        blobId = blobId,
        mimeType = "application/pdf",
        sizeBytes = 1,
        updatedAt = now,
    )
}
