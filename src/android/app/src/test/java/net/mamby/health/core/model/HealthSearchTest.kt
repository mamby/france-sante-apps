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
    fun searchExcludesReminderTextAndDocumentBodies() {
        val record = ProfileRecord(
            profile = HealthProfile(UUID.randomUUID(), "Owner", lastUpdatedAt = now),
            reminders = listOf(
                Reminder(
                    UUID.randomUUID(),
                    "Secret reminder phrase",
                    LocalDate.of(2026, 8, 1),
                    java.time.LocalTime.NOON,
                    updatedAt = now,
                ),
            ),
        )

        assertTrue(HealthSearch.search(record, "secret reminder").isEmpty())
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
}
