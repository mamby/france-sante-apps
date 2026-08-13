package net.mamby.health.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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
    fun schemaV6RoundTripsMultipleProfilesNotesSchedulesAndContactsWithoutDerivedViews() {
        val contactId = UUID.fromString("34a502d7-23c0-4be5-a8fd-34b56dca82d0")
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
                        lastUpdatedAt = now,
                    ),
                    documents = listOf(document.copy(category = DocumentCategoryRef.Custom(categoryId))),
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
            schedules = listOf(
                Schedule(
                    id = UUID.randomUUID(),
                    title = "Family check-in",
                    timing = ScheduleTiming.AllDay(LocalDate.of(2026, 8, 1)),
                    recurrence = ScheduleRecurrence.Monthly(1, LocalDate.of(2026, 12, 1)),
                    alert = ScheduleAlert.AllDay(1, LocalTime.of(9, 0)),
                    people = listOf("Amina", "Sam"),
                    location = "Home",
                    notes = "Bring records",
                    updatedAt = now,
                ),
            ),
            contacts = listOf(
                VaultContact(
                    id = contactId,
                    name = "Dr Martin",
                    phoneNumbers = listOf("+33 1 23 45 67 89", "+33 6 00 00 00 00"),
                    emailAddresses = listOf("doctor@example.com"),
                    websites = listOf("https://example.com"),
                    addresses = listOf("12 Rue de la Santé\n75014 Paris\nFrance"),
                    notes = "Family doctor",
                    updatedAt = now,
                ),
            ),
            updatedAt = now,
        ).requireValid()

        val encoded = VaultCodec.encode(vault)
        val decoded = VaultCodec.decode(encoded)

        assertEquals(6, decoded.sourceVersion)
        assertEquals(vault, decoded.vault)
        assertEquals(noteId, decoded.vault.notes.single().id)
        assertEquals("O+", decoded.vault.profiles.first().summary().bloodType)
        assertEquals(
            listOf(documentId, measurementId, familyId, directiveId, identifierId),
            decoded.vault.profiles.first().index().map(VaultItem::id),
        )
        assertEquals(listOf(contactId), decoded.vault.contactIndex().map(VaultItem::id))
        val text = encoded.decodeToString()
        assertFalse(text.contains("\"summary\""))
        assertFalse(text.contains("\"vaultItems\""))
    }

    @Test
    fun exactV3PayloadMigratesProfileNotesToVaultScopeInStableOrder() {
        val firstNote = UUID.fromString("1fd70aaf-9404-45a3-8586-4ba5ecfa2cc2")
        val secondNote = UUID.fromString("e6a40b07-999c-4e73-bbd8-8249b5c18bdc")
        val contactId = UUID.fromString("7569477f-fc10-4763-a802-b9146233c7db")
        val appointmentId = UUID.fromString("ad62eb6e-383d-4fdd-b4dd-aab7f44b4641")
        val reminderId = UUID.fromString("ba22181c-b6a0-4b6b-86a8-cb55fe80bef6")
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
                  }],
                  "careDirectory":[{
                    "id":"$contactId","kind":"CLINIC","name":"Legacy clinic",
                    "phoneNumbers":["+33 1 11 22 33 44"],
                    "emailAddresses":["legacy@example.test"],"updatedAt":"$now"
                  }],
                  "appointments": [{
                    "id":"$appointmentId","title":"Visit","clinician":"Discarded clinician",
                    "location":"Discarded location","startsAt":"2026-08-01T10:00:00Z",
                    "notes":"Discarded notes","reminderLeadMinutes":37,"updatedAt":"$now"
                  }],
                  "reminders": [{
                    "id":"$reminderId","title":"Monthly task","startsOn":"2026-08-31",
                    "timeOfDay":"09:30:00","recurrence":"MONTHLY","endsOn":"2026-12-31",
                    "isEnabled":false,"notes":"Discarded notes","updatedAt":"$now"
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
        assertEquals(6, decoded.vault.version)
        assertEquals(12L, decoded.vault.revision)
        assertEquals(listOf(firstNote, secondNote), decoded.vault.notes.map(HealthNote::id))
        assertEquals(listOf(contactId), decoded.vault.contacts.map(VaultContact::id))
        assertEquals(listOf("+33 1 11 22 33 44"), decoded.vault.contacts.single().phoneNumbers)
        assertEquals(listOf("legacy@example.test"), decoded.vault.contacts.single().emailAddresses)
        assertEquals(listOf(firstId, secondId), decoded.vault.profiles.map { it.profile.id })
        val appointment = decoded.vault.schedules.first { it.id == appointmentId }
        assertEquals(ScheduleAlert.Timed(37), appointment.alert)
        assertEquals(listOf("Amina"), appointment.people)
        assertEquals(null, appointment.location)
        assertEquals(null, appointment.notes)
        val reminder = decoded.vault.schedules.first { it.id == reminderId }
        assertEquals(ScheduleRecurrence.Monthly(31, LocalDate.of(2026, 12, 31)), reminder.recurrence)
        assertEquals(null, reminder.alert)
    }

    @Test
    fun exactV4PayloadMigratesVaultNotesAndCopiesOnlyOwnerNamesIntoSchedules() {
        val appointmentId = UUID.fromString("b9d1a9cd-408f-47d9-aa5f-50d3f28632fe")
        val contactId = UUID.fromString("f900d92a-96b6-4134-aa48-b0fafbb40096")
        val payload = """
            {
              "version":4,"revision":14,
              "profiles":[{
                "profile":{"id":"$firstId","displayName":"Amina","lastUpdatedAt":"$now"},
                "appointments":[{
                  "id":"$appointmentId","title":"Check-up","clinician":"Discard me",
                  "location":"Discard me","startsAt":"2026-09-01T08:00:00Z",
                  "relatedDocumentIds":[],"notes":"Discard me","reminderLeadMinutes":1440,
                  "updatedAt":"$now"
                }],
                "careDirectory":[{
                  "id":"$contactId","kind":"OTHER","name":"Legacy contact","updatedAt":"$now"
                }]
              }],
              "notes":[],"updatedAt":"$now"
            }
        """.trimIndent().encodeToByteArray()

        val decoded = VaultCodec.decode(payload)
        val schedule = decoded.vault.schedules.single()

        assertEquals(4, decoded.sourceVersion)
        assertEquals(6, decoded.vault.version)
        assertEquals(appointmentId, schedule.id)
        assertEquals(contactId, decoded.vault.contacts.single().id)
        assertEquals(listOf("Amina"), schedule.people)
        assertEquals(ScheduleAlert.Timed(1_440), schedule.alert)
        assertEquals(null, schedule.location)
        assertEquals(null, schedule.notes)
    }

    @Test
    fun exactV5PayloadFlattensContactsAndDiscardsDirectoryTypesAndRecordLinks() {
        val firstContactId = UUID.fromString("a51b44bc-2ed4-44d1-849e-12d839c16917")
        val firstProfileSecondContactId = UUID.fromString("78e4d0ec-e48a-4e8d-b73a-e7595c6f8888")
        val secondContactId = UUID.fromString("818de9e4-fc67-4db1-b514-e0765a4b8bf1")
        val medicationId = UUID.fromString("76e31a97-452f-4ff6-a89f-8d30bb53a75e")
        val vaccinationId = UUID.fromString("b0b9dd16-fadd-4e52-87eb-dc4879615dc4")
        val payload = """
            {
              "version":5,"revision":18,
              "profiles":[
                {
                  "profile":{
                    "id":"$firstId","displayName":"Amina",
                    "primaryDoctorEntryId":"$firstContactId","lastUpdatedAt":"$now"
                  },
                  "documents":[{
                    "id":"$documentId","title":"Report",
                    "category":{"type":"builtIn","category":"REPORTS"},
                    "documentDate":"2026-07-29","source":"Dr Martin",
                    "sourceEntryId":"$firstContactId","blobId":"$blobId",
                    "mimeType":"application/pdf","sizeBytes":10,"updatedAt":"$now"
                  }],
                  "medications":[{
                    "id":"$medicationId","name":"Treatment","dose":"5 mg","instructions":"Daily",
                    "prescriberEntryId":"$firstContactId","pharmacyEntryId":"$firstContactId",
                    "updatedAt":"$now"
                  }],
                  "vaccinations":[{
                    "id":"$vaccinationId","name":"Vaccine","dateAdministered":"2026-07-01",
                    "provider":"Clinic","providerEntryId":"$firstContactId","updatedAt":"$now"
                  }],
                  "careDirectory":[{
                    "id":"$firstContactId","kind":"DOCTOR","name":"Shared name",
                    "specialty":"Discarded","organization":"Discarded",
                    "address":{
                      "addressLines":["12 Main St","Unit 4"],"locality":"Paris",
                      "region":"Île-de-France","postalCode":"75014","country":"France"
                    },
                    "phoneNumbers":["+33 1 23 45 67 89"],
                    "emailAddresses":["doctor@example.com"],"notes":"Keep this","updatedAt":"$now"
                  },{
                    "id":"$firstProfileSecondContactId","kind":"OTHER","name":"Second in first profile",
                    "phoneNumbers":[],"emailAddresses":[],"updatedAt":"$now"
                  }]
                },
                {
                  "profile":{"id":"$secondId","displayName":"Sam","lastUpdatedAt":"$now"},
                  "careDirectory":[{
                    "id":"$secondContactId","kind":"PHARMACY","name":"Shared name",
                    "address":{},"phoneNumbers":[],"emailAddresses":[],"updatedAt":"$now"
                  }]
                }
              ],
              "notes":[],"schedules":[],"updatedAt":"$now"
            }
        """.trimIndent().encodeToByteArray()

        val decoded = VaultCodec.decode(payload)

        assertEquals(5, decoded.sourceVersion)
        assertEquals(6, decoded.vault.version)
        assertEquals(
            listOf(firstContactId, firstProfileSecondContactId, secondContactId),
            decoded.vault.contacts.map(VaultContact::id),
        )
        assertEquals(
            listOf("Shared name", "Second in first profile", "Shared name"),
            decoded.vault.contacts.map(VaultContact::name),
        )
        assertEquals(
            listOf("12 Main St\nUnit 4\n75014 Paris\nÎle-de-France · France"),
            decoded.vault.contacts.first().addresses,
        )
        assertEquals(emptyList<String>(), decoded.vault.contacts.first().websites)
        assertEquals("Keep this", decoded.vault.contacts.first().notes)
        assertEquals(now, decoded.vault.contacts.first().updatedAt)
        assertEquals("Dr Martin", decoded.vault.profiles.first().documents.single().source)
        assertEquals("Clinic", decoded.vault.profiles.first().vaccinations.single().provider)
        val migratedJson = VaultCodec.encode(decoded.vault).decodeToString()
        assertFalse(migratedJson.contains("primaryDoctorEntryId"))
        assertFalse(migratedJson.contains("sourceEntryId"))
        assertFalse(migratedJson.contains("prescriberEntryId"))
        assertFalse(migratedJson.contains("pharmacyEntryId"))
        assertFalse(migratedJson.contains("providerEntryId"))
        assertFalse(migratedJson.contains("careDirectory"))
    }

    @Test
    fun exactV2PayloadMigratesToV6PreservingProfilesCategoriesAndBlobs() {
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
        assertEquals(6, decoded.vault.version)
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
        assertEquals(6, decoded.vault.version)
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
    fun validationRejectsGlobalIdCollisionsAcrossVaultAndProfileObjects() {
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
                ),
            ),
            schedules = listOf(
                Schedule(sharedId, "Visit", ScheduleTiming.InstantTimed(now), updatedAt = now),
            ),
            updatedAt = now,
        )

        assertThrows(IllegalArgumentException::class.java) { invalid.requireValid() }
    }

    @Test
    fun validationRejectsInvalidMeasurementsAndContactWebsites() {
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
            profile = HealthProfile(firstId, "Amina", lastUpdatedAt = now),
            measurements = listOf(wrongUnit),
        )
        val second = ProfileRecord(
            profile = HealthProfile(secondId, "Sam", lastUpdatedAt = now),
        )
        val invalid = HealthVault(revision = 4, profiles = listOf(first, second), updatedAt = now)

        assertThrows(VaultValidationException::class.java) { invalid.requireValid() }

        val finiteButWrongUnit = invalid.copy(
            profiles = listOf(
                first.copy(
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

        val invalidWebsite = HealthVault(
            revision = 4,
            profiles = listOf(first.copy(measurements = emptyList()), second),
            contacts = listOf(VaultContact(UUID.randomUUID(), "Clinic", websites = listOf("ftp://example.com"), updatedAt = now)),
            updatedAt = now,
        )
        assertThrows(VaultValidationException::class.java) { invalidWebsite.requireValid() }
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
