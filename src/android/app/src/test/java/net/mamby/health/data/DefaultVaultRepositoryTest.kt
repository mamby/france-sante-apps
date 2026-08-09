package net.mamby.health.data

import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.runTest
import net.mamby.health.core.model.BuiltInDocumentCategory
import net.mamby.health.core.model.Appointment
import net.mamby.health.core.model.CareDirective
import net.mamby.health.core.model.CareDirectiveKind
import net.mamby.health.core.model.CareDirectoryEntry
import net.mamby.health.core.model.CareDirectoryKind
import net.mamby.health.core.model.CustomDocumentCategory
import net.mamby.health.core.model.CustomMeasurementType
import net.mamby.health.core.model.DocumentCategoryRef
import net.mamby.health.core.model.HealthNote
import net.mamby.health.core.model.HealthMeasurement
import net.mamby.health.core.model.MeasurementReading
import net.mamby.health.core.model.MeasurementTypeRef
import net.mamby.health.core.model.MeasurementUnitRef
import net.mamby.health.core.model.asReference
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.Medication
import net.mamby.health.core.model.profileRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultVaultRepositoryTest {
    private val now = Instant.parse("2026-07-30T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun missingStorageProducesExplicitMissingState() = runTest {
        val repository = repository(FakeVaultStore(), FakeDocumentBlobStore())

        repository.initialize()

        assertEquals(VaultState.Missing, repository.state.value)
    }

    @Test
    fun corruptionBecomesUnreadableWithoutSavingReplacementData() = runTest {
        val store = FakeVaultStore().apply { loadFailure = VaultCorruptionException("tampered") }
        val repository = repository(store, FakeDocumentBlobStore())

        repository.initialize()

        val state = repository.state.value as VaultState.Unreadable
        assertEquals(UnreadableReason.CORRUPT, state.reason)
        assertEquals(0, store.saveCount)
    }

    @Test
    fun explicitProfileOwnershipRejectsStaleWork() = runTest {
        val repository = repository(FakeVaultStore(), FakeDocumentBlobStore())
        repository.initialize()
        repository.createVault("Amina")
        val first = (repository.state.value as VaultState.Ready).vault.profiles.single().profile.id
        val second = repository.addProfile("Sam")
        val revisionAfterAdd = repository.exportSnapshot().revision
        repository.upsertMedication(
            second,
            Medication(UUID.randomUUID(), "Medication", "5 mg", "Daily", updatedAt = Instant.EPOCH),
        )

        val vault = repository.exportSnapshot()
        assertTrue(vault.profileRecord(first).medications.isEmpty())
        assertEquals(1, vault.profileRecord(second).medications.size)
        assertEquals(revisionAfterAdd + 1, vault.revision)
        assertThrows(NoSuchElementException::class.java) {
            kotlinx.coroutines.test.runTest {
                repository.upsertMedication(
                    UUID.randomUUID(),
                    Medication(UUID.randomUUID(), "Invalid", "", "", updatedAt = now),
                )
            }
        }
    }

    @Test
    fun vaultWideNotesSurviveProfileDeletionAndAdvanceRevision() = runTest {
        val repository = repository(FakeVaultStore(), FakeDocumentBlobStore())
        repository.initialize()
        repository.createVault("First")
        val first = (repository.state.value as VaultState.Ready).vault.profiles.single().profile.id
        val second = repository.addProfile("Second")
        val note = HealthNote(UUID.randomUUID(), "Shared", "Vault context", now, Instant.EPOCH)
        val beforeNote = repository.exportSnapshot().revision

        repository.upsertHealthNote(note)

        val afterNote = repository.exportSnapshot()
        assertEquals(beforeNote + 1, afterNote.revision)
        assertEquals(now, afterNote.notes.single().updatedAt)
        repository.deleteProfile(second)
        assertEquals(note.id, repository.exportSnapshot().notes.single().id)
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.test.runTest { repository.deleteProfile(first) }
        }

        repository.deleteHealthNote(note.id)
        assertTrue(repository.exportSnapshot().notes.isEmpty())
    }

    @Test
    fun importFailureCleansBlobAndCallerPlaintext() = runTest {
        val store = FakeVaultStore()
        val blobs = FakeDocumentBlobStore()
        val repository = repository(store, blobs)
        repository.initialize()
        repository.createVault("Owner")
        val profileId = (repository.state.value as VaultState.Ready).vault.profiles.single().profile.id
        val content = "%PDF-private".encodeToByteArray()
        store.failNextSave = true

        try {
            repository.importDocument(
                profileId,
                MedicalDocumentDraft("Document", BuiltInDocumentCategory.REPORTS.asReference(), LocalDate.of(2026, 7, 30), "Clinic"),
                ImportedDocumentData("report.pdf", "application/pdf", content.size.toLong(), content),
            )
        } catch (_: TestStorageFailure) {
            // Expected.
        }

        assertTrue(blobs.contents.isEmpty())
        assertTrue(content.all { it == 0.toByte() })
        assertTrue(repository.exportSnapshot().profileRecord(profileId).documents.isEmpty())
    }

    @Test
    fun restoreFailurePreservesStateAndSuccessfulRestoreReplacesTheVault() = runTest {
        val store = FakeVaultStore()
        val repository = repository(store, FakeDocumentBlobStore())
        repository.initialize()
        repository.createVault("Existing")
        val before = repository.exportSnapshot()
        val replacement = HealthVault.empty(
            now,
            UUID.fromString("a8e0a4ad-3b5b-41ca-a1f1-ff9c280aa094"),
            "Replacement",
        )
        store.replaceFailure = TestStorageFailure()

        try {
            repository.restore(replacement, emptyList())
        } catch (_: TestStorageFailure) {
            // Expected.
        }

        assertEquals(before, repository.exportSnapshot())

        store.replaceFailure = null
        repository.restore(replacement, emptyList())

        assertEquals(replacement, repository.exportSnapshot())
    }

    @Test
    fun directoryAndDocumentDeletionCleanReferencesAtomicallyWithOneRevisionEach() = runTest {
        val repository = repository(FakeVaultStore(), FakeDocumentBlobStore())
        repository.initialize()
        repository.createVault("Owner")
        val profileId = (repository.state.value as VaultState.Ready).vault.profiles.single().profile.id
        val doctorId = UUID.randomUUID()
        repository.upsertCareDirectoryEntry(
            profileId,
            CareDirectoryEntry(doctorId, CareDirectoryKind.DOCTOR, "Dr Martin", updatedAt = Instant.EPOCH),
        )
        repository.setPrimaryDoctor(profileId, doctorId)
        val document = repository.importDocument(
            profileId,
            MedicalDocumentDraft(
                "Directive attachment",
                BuiltInDocumentCategory.DIRECTIVES.asReference(),
                LocalDate.of(2026, 7, 30),
                "Dr Martin",
                sourceEntryId = doctorId,
            ),
            importedPdf(),
        )
        repository.upsertAppointment(
            profileId,
            Appointment(
                UUID.randomUUID(),
                "Visit",
                "Dr Martin",
                "Clinic",
                now,
                relatedDocumentIds = listOf(document.id),
                clinicianEntryId = doctorId,
                updatedAt = Instant.EPOCH,
            ),
        )
        repository.upsertCareDirective(
            profileId,
            CareDirective(
                UUID.randomUUID(),
                CareDirectiveKind.ADVANCE_DIRECTIVE,
                "Directive",
                "Personal text",
                LocalDate.of(2026, 7, 30),
                relatedDocumentIds = listOf(document.id),
                updatedAt = Instant.EPOCH,
            ),
        )

        val beforeDirectoryDelete = repository.exportSnapshot().revision
        repository.deleteCareDirectoryEntry(profileId, doctorId)
        val afterDirectoryDelete = repository.exportSnapshot()
        val afterDirectoryRecord = afterDirectoryDelete.profileRecord(profileId)
        assertEquals(beforeDirectoryDelete + 1, afterDirectoryDelete.revision)
        assertEquals(null, afterDirectoryRecord.profile.primaryDoctorEntryId)
        assertEquals(null, afterDirectoryRecord.documents.single().sourceEntryId)
        assertEquals("Dr Martin", afterDirectoryRecord.documents.single().source)
        assertEquals(null, afterDirectoryRecord.appointments.single().clinicianEntryId)
        assertEquals("Dr Martin", afterDirectoryRecord.appointments.single().clinician)

        val beforeDocumentDelete = afterDirectoryDelete.revision
        repository.deleteDocument(profileId, document.id)
        val afterDocumentDelete = repository.exportSnapshot()
        val afterDocumentRecord = afterDocumentDelete.profileRecord(profileId)
        assertEquals(beforeDocumentDelete + 1, afterDocumentDelete.revision)
        assertTrue(afterDocumentRecord.documents.isEmpty())
        assertTrue(afterDocumentRecord.appointments.single().relatedDocumentIds.isEmpty())
        assertTrue(afterDocumentRecord.directives.single().relatedDocumentIds.isEmpty())
    }

    @Test
    fun deletingUsedCustomCategoryReclassifiesDocumentsInOneMutation() = runTest {
        val repository = repository(FakeVaultStore(), FakeDocumentBlobStore())
        repository.initialize()
        repository.createVault("Owner")
        val profileId = (repository.state.value as VaultState.Ready).vault.profiles.single().profile.id
        val categoryId = UUID.randomUUID()
        repository.upsertCustomDocumentCategory(
            profileId,
            CustomDocumentCategory(categoryId, "Invoices", Instant.EPOCH),
        )
        val document = repository.importDocument(
            profileId,
            MedicalDocumentDraft(
                "Invoice",
                DocumentCategoryRef.Custom(categoryId),
                LocalDate.of(2026, 7, 30),
                "Hospital",
            ),
            importedPdf(),
        )
        val before = repository.exportSnapshot().revision

        repository.deleteCustomDocumentCategory(
            profileId,
            categoryId,
            BuiltInDocumentCategory.INVOICES_RECEIPTS.asReference(),
        )

        val after = repository.exportSnapshot()
        val record = after.profileRecord(profileId)
        assertEquals(before + 1, after.revision)
        assertTrue(record.customDocumentCategories.isEmpty())
        assertEquals(
            BuiltInDocumentCategory.INVOICES_RECEIPTS.asReference(),
            record.documents.single { it.id == document.id }.category,
        )
    }

    @Test
    fun customMeasurementTypeEditsDoNotReinterpretHistoryAndDeletionIsBlockedWhileUsed() = runTest {
        val repository = repository(FakeVaultStore(), FakeDocumentBlobStore())
        repository.initialize()
        repository.createVault("Owner")
        val profileId = (repository.state.value as VaultState.Ready).vault.profiles.single().profile.id
        val typeId = UUID.randomUUID()
        val measurementId = UUID.randomUUID()
        repository.upsertCustomMeasurementType(
            profileId,
            CustomMeasurementType(typeId, "Waist", "cm", Instant.EPOCH),
        )
        repository.upsertMeasurement(
            profileId,
            HealthMeasurement(
                measurementId,
                MeasurementTypeRef.Custom(typeId),
                MeasurementReading.Scalar(82.0, MeasurementUnitRef.Custom("cm")),
                now,
                updatedAt = Instant.EPOCH,
            ),
        )

        repository.upsertCustomMeasurementType(
            profileId,
            CustomMeasurementType(typeId, "Waist circumference", "in", Instant.EPOCH),
        )
        val record = repository.exportSnapshot().profileRecord(profileId)
        assertEquals("in", record.customMeasurementTypes.single().suggestedUnit)
        assertEquals(
            MeasurementUnitRef.Custom("cm"),
            (record.measurements.single().reading as MeasurementReading.Scalar).unit,
        )
        assertThrows(IllegalStateException::class.java) {
            runTest { repository.deleteCustomMeasurementType(profileId, typeId) }
        }

        repository.deleteMeasurement(profileId, measurementId)
        repository.deleteCustomMeasurementType(profileId, typeId)
        assertTrue(repository.exportSnapshot().profileRecord(profileId).customMeasurementTypes.isEmpty())
    }

    private fun repository(
        store: FakeVaultStore,
        blobs: FakeDocumentBlobStore,
    ) = DefaultVaultRepository(store, blobs, clock, UuidGenerator(UUID::randomUUID))

    private fun importedPdf(): ImportedDocumentData {
        val content = "%PDF-private".encodeToByteArray()
        return ImportedDocumentData("document.pdf", "application/pdf", content.size.toLong(), content)
    }
}

private class FakeVaultStore : VaultStore {
    var stored: HealthVault? = null
    var loadFailure: Exception? = null
    var replaceFailure: Exception? = null
    var failNextSave = false
    var saveCount = 0
    override suspend fun load(): HealthVault? { loadFailure?.let { throw it }; return stored }
    override suspend fun save(vault: HealthVault) {
        if (failNextSave) { failNextSave = false; throw TestStorageFailure() }
        stored = vault
        saveCount++
    }
    override suspend fun replaceAtomically(vault: HealthVault, documentBlobs: List<RestoreDocumentBlob>) {
        replaceFailure?.let { throw it }
        documentBlobs.forEach { it.openStream().use { input -> input.readBytes() } }
        stored = vault
    }
    override suspend fun delete() { stored = null }
}

private class FakeDocumentBlobStore : DocumentBlobStore {
    val contents = mutableMapOf<UUID, ByteArray>()
    private val staged = mutableMapOf<UUID, Pair<UUID, ByteArray>>()
    override suspend fun stage(blobId: UUID, plaintext: ByteArray): StagedDocumentBlob {
        val token = UUID.randomUUID()
        staged[token] = blobId to plaintext.copyOf()
        return StagedDocumentBlob(blobId, UUID.randomUUID(), token)
    }
    override suspend fun commit(stagedBlob: StagedDocumentBlob) {
        val (id, bytes) = staged.remove(stagedBlob.token) ?: error("Missing staged blob")
        contents[id] = bytes
    }
    override suspend fun discard(stagedBlob: StagedDocumentBlob) { staged.remove(stagedBlob.token)?.second?.fill(0) }
    override suspend fun read(blobId: UUID): ByteArray? = contents[blobId]?.copyOf()
    override suspend fun copyTo(blobId: UUID, output: OutputStream): Long {
        val bytes = contents[blobId] ?: return 0
        output.write(bytes)
        return bytes.size.toLong()
    }
    override suspend fun delete(blobId: UUID) { contents.remove(blobId)?.fill(0) }
    override suspend fun listIds(): Set<UUID> = contents.keys
    override suspend fun cleanupOrphans(referencedBlobIds: Set<UUID>) {
        contents.keys.filterNot(referencedBlobIds::contains).forEach(contents::remove)
    }
}

private class TestStorageFailure : RuntimeException()
