package net.mamby.health.core.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthSearchTest {
    private val now = Instant.parse("2026-07-30T10:00:00Z")

    @Test
    fun searchIsAllTermCaseAndDiacriticInsensitiveWithinSelectedRecord() {
        val selectedId = UUID.randomUUID()
        val record = ProfileRecord(
            profile = HealthProfile(
                selectedId,
                "Amina",
                chronicConditions = listOf("Migraïne chronique"),
                lastUpdatedAt = now,
            ),
            documents = listOf(
                MedicalDocument(
                    UUID.randomUUID(),
                    "Résultat laboratoire",
                    BuiltInDocumentCategory.LAB_RESULTS.asReference(),
                    LocalDate.of(2026, 7, 1),
                    "Clinique",
                    notes = "Suivi annuel",
                    blobId = UUID.randomUUID(),
                    mimeType = "application/pdf",
                    sizeBytes = 10,
                    updatedAt = now,
                ),
            ),
            medications = listOf(
                Medication(UUID.randomUUID(), "Caféine", "5 mg", "Matin", updatedAt = now),
            ),
        )

        val documentResults = HealthSearch.search(record, "LABORATOIRE resultat")
        val healthResults = HealthSearch.search(record, "migraine chronique")

        assertEquals(HealthSearchScope.Profile(selectedId), documentResults.single().scope)
        assertTrue(documentResults.single().target is HealthSearchTarget.Document)
        assertTrue(healthResults.single().target is HealthSearchTarget.HealthInfo)
        assertTrue(HealthSearch.search(record, "").isEmpty())
        assertTrue(HealthSearch.search(record, "laboratoire missing").isEmpty())
    }

    @Test
    fun searchIndexesScheduleVisibleFieldsAtVaultScope() {
        val schedule = Schedule(
            id = UUID.randomUUID(),
            title = "Dental visit",
            timing = ScheduleTiming.InstantTimed(now.plusSeconds(3_600)),
            people = listOf("Samira"),
            location = "North clinic",
            notes = "Bring insurance card",
            updatedAt = now,
        )
        val record = ProfileRecord(
            profile = HealthProfile(UUID.randomUUID(), "Owner", lastUpdatedAt = now),
        )

        listOf("dental", "samira", "north clinic", "insurance card").forEach { query ->
            val result = HealthSearch.search(listOf(record), emptyList(), listOf(schedule), query).single()
            assertEquals(HealthSearchScope.Vault, result.scope)
            assertEquals(HealthSearchTarget.Schedule(schedule.id), result.target)
        }
    }

    @Test
    fun searchIndexesIdentifierLabelAndIssuerButNeverItsValue() {
        val identifier = HealthIdentifier(
            id = UUID.randomUUID(),
            kind = HealthIdentifierKind.SOCIAL_SECURITY,
            label = "National identifier",
            value = "SENSITIVE-123456789",
            issuer = "Public insurer",
            updatedAt = now,
        )
        val record = ProfileRecord(
            profile = HealthProfile(UUID.randomUUID(), "Owner", lastUpdatedAt = now),
            healthIdentifiers = listOf(identifier),
        )

        assertEquals(1, HealthSearch.search(record, "national identifier").size)
        assertEquals(1, HealthSearch.search(record, "public insurer").size)
        assertTrue(HealthSearch.search(record, "SENSITIVE-123456789").isEmpty())
        assertTrue(HealthSearch.search(record, "123456789").isEmpty())
    }

    @Test
    fun searchReturnsVaultScopedNotesAlongsideFilteredProfileRecords() {
        val note = HealthNote(UUID.randomUUID(), "Consultation", "Questions to ask", now, now)
        val selectedRecord = ProfileRecord(
            profile = HealthProfile(UUID.randomUUID(), "Selected", lastUpdatedAt = now),
        )

        val result = HealthSearch.search(listOf(selectedRecord), listOf(note), "questions").single()

        assertEquals(HealthSearchScope.Vault, result.scope)
        assertEquals(HealthSearchGroup.NOTES, result.group)
        assertEquals(HealthSearchTarget.Note(note.id), result.target)
    }

    @Test
    fun searchIndexesEveryContactFieldAtVaultScopeAlongsideFilteredProfiles() {
        val contact = VaultContact(
            id = UUID.randomUUID(),
            name = "Clinique Étoile",
            phoneNumbers = listOf("+33 1 23 45 67 89"),
            emailAddresses = listOf("accueil@etoile.example"),
            websites = listOf("https://etoile.example/path"),
            addresses = listOf("12 rue des Fleurs\n75001 Paris"),
            notes = "Entrée accessible",
            updatedAt = now,
        )
        val selectedRecord = ProfileRecord(
            profile = HealthProfile(UUID.randomUUID(), "Selected", lastUpdatedAt = now),
        )

        listOf("clinique etoile", "+33 1 23", "accueil", "etoile.example/path", "fleurs paris", "accessible")
            .forEach { query ->
                val result = HealthSearch.search(
                    records = listOf(selectedRecord),
                    notes = emptyList(),
                    schedules = emptyList(),
                    contacts = listOf(contact),
                    query = query,
                ).single()

                assertEquals(HealthSearchScope.Vault, result.scope)
                assertEquals(HealthSearchGroup.CONTACTS, result.group)
                assertEquals(HealthSearchTarget.Contact(contact.id), result.target)
            }
    }

    @Test
    fun searchFindsAllRootWideContentWithoutProfiles() {
        val note = HealthNote(
            id = UUID.randomUUID(),
            title = "Shared questions",
            body = "For the next visit",
            notedAt = now,
            updatedAt = now,
        )
        val schedule = Schedule(
            id = UUID.randomUUID(),
            title = "Shared appointment",
            timing = ScheduleTiming.InstantTimed(now.plusSeconds(3_600)),
            updatedAt = now,
        )
        val contact = VaultContact(
            id = UUID.randomUUID(),
            name = "Shared care team",
            updatedAt = now,
        )

        val results = HealthSearch.search(
            records = emptyList(),
            notes = listOf(note),
            schedules = listOf(schedule),
            contacts = listOf(contact),
            query = "shared",
        )

        assertEquals(
            setOf(
                HealthSearchTarget.Note(note.id),
                HealthSearchTarget.Schedule(schedule.id),
                HealthSearchTarget.Contact(contact.id),
            ),
            results.map(HealthSearchResult::target).toSet(),
        )
        assertEquals(
            setOf(HealthSearchGroup.NOTES, HealthSearchGroup.SCHEDULE, HealthSearchGroup.CONTACTS),
            results.map(HealthSearchResult::group).toSet(),
        )
        assertTrue(results.all { it.scope == HealthSearchScope.Vault })
    }
}
