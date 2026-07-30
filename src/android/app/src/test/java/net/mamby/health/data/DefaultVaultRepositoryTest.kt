package net.mamby.health.data

import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.mamby.health.core.model.Appointment
import net.mamby.health.core.model.DocumentCategory
import net.mamby.health.core.model.EmergencyContact
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.Medication
import net.mamby.health.core.model.Reminder
import net.mamby.health.core.model.Vaccination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DefaultVaultRepositoryTest {
    private val now = Instant.parse("2026-07-30T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun corruptionBecomesUnreadableWithoutSavingDemoData() = runTest {
        val store = FakeVaultStore().apply { loadFailure = VaultCorruptionException("tampered") }
        val repository = repository(store, FakeDocumentBlobStore())

        repository.initialize()

        val state = repository.state.value
        assertTrue(state is VaultState.Unreadable)
        assertEquals(UnreadableReason.CORRUPT, (state as VaultState.Unreadable).reason)
        assertEquals(0, store.saveCount)
    }

    @Test
    fun importSaveFailureRemovesCommittedBlobAndClearsCallerPlaintext() = runTest {
        val store = FakeVaultStore()
        val blobs = FakeDocumentBlobStore()
        val repository = repository(store, blobs)
        repository.initialize()
        repository.createEmpty("Owner")
        val content = "%PDF-private".encodeToByteArray()
        store.failNextSave = true

        try {
            repository.importDocument(
                MedicalDocumentDraft(
                    title = "Document",
                    category = DocumentCategory.REPORTS,
                    documentDate = LocalDate.of(2026, 7, 30),
                    source = "Clinic",
                ),
                ImportedDocumentData("report.pdf", "application/pdf", content.size.toLong(), content),
            )
            fail("Import should fail when metadata cannot be committed")
        } catch (_: TestStorageFailure) {
            // Expected.
        }

        assertTrue(blobs.contents.isEmpty())
        assertTrue(content.all { it == 0.toByte() })
        assertTrue((repository.state.value as VaultState.Ready).vault.documents.isEmpty())
    }

    @Test
    fun crudUpdatesRevisionAndDocumentDeleteRemovesReferencesAndBlob() = runTest {
        val store = FakeVaultStore()
        val blobs = FakeDocumentBlobStore()
        val repository = repository(store, blobs)
        repository.initialize()
        repository.createEmpty("Owner")
        repository.upsertEmergencyContact(
            EmergencyContact(UUID.randomUUID(), "Alex", "Partner", "+33123456789"),
        )
        val content = "%PDF-content".encodeToByteArray()
        val document = repository.importDocument(
            MedicalDocumentDraft(
                "Report",
                DocumentCategory.REPORTS,
                LocalDate.of(2026, 7, 20),
                "Clinic",
            ),
            ImportedDocumentData("report.pdf", "application/pdf", content.size.toLong(), content),
        )
        repository.upsertMedication(
            Medication(UUID.randomUUID(), "Medication", "5 mg", "Daily", updatedAt = Instant.EPOCH),
        )
        val appointmentId = UUID.randomUUID()
        repository.upsertAppointment(
            Appointment(
                appointmentId,
                "Check-up",
                "Clinician",
                "Clinic",
                now.plusSeconds(86_400),
                relatedDocumentIds = listOf(document.id),
                updatedAt = Instant.EPOCH,
            ),
        )
        repository.upsertVaccination(
            Vaccination(
                UUID.randomUUID(),
                "Influenza",
                LocalDate.of(2026, 1, 10),
                updatedAt = Instant.EPOCH,
            ),
        )
        repository.upsertReminder(
            Reminder(
                UUID.randomUUID(),
                "Reminder",
                LocalDate.of(2026, 7, 31),
                LocalTime.of(8, 0),
                notes = "Notes",
                updatedAt = Instant.EPOCH,
            ),
        )

        repository.deleteDocument(document.id)

        val vault = repository.exportSnapshot()
        assertTrue(vault.revision >= 7)
        assertTrue(vault.documents.isEmpty())
        assertTrue(blobs.contents.isEmpty())
        assertTrue(vault.appointments.single { it.id == appointmentId }.relatedDocumentIds.isEmpty())
        assertEquals(1, vault.medications.size)
        assertEquals(1, vault.vaccinations.size)
        assertEquals(1, vault.reminders.size)
    }

    @Test
    fun failedRestoreLeavesExistingReadyStateUntouched() = runTest {
        val store = FakeVaultStore()
        val repository = repository(store, FakeDocumentBlobStore())
        repository.initialize()
        repository.createEmpty("Existing")
        val before = repository.exportSnapshot()
        store.replaceFailure = TestStorageFailure()

        try {
            repository.restore(
                HealthVault.empty(now, displayName = "Replacement"),
                emptyList(),
            )
            fail("Restore should fail")
        } catch (_: TestStorageFailure) {
            // Expected.
        }

        assertEquals(before, repository.exportSnapshot())
    }

    @Test
    fun creatingAnotherEmptyVaultCannotOverwriteAnExistingVault() = runTest {
        val store = FakeVaultStore()
        val repository = repository(store, FakeDocumentBlobStore())
        repository.initialize()
        repository.createEmpty("Existing")
        val before = repository.exportSnapshot()

        try {
            repository.createEmpty("Replacement")
            fail("An existing real vault must not be replaced by sample onboarding")
        } catch (_: IllegalStateException) {
            // Expected.
        }

        assertEquals(before, repository.exportSnapshot())
        assertEquals(1, store.saveCount)
    }

    private fun repository(
        store: FakeVaultStore,
        blobs: FakeDocumentBlobStore,
    ) = DefaultVaultRepository(
        vaultStore = store,
        documentBlobStore = blobs,
        demoVaultProvider = DemoVaultProvider { HealthVault.empty(it) },
        clock = clock,
        uuidGenerator = UuidGenerator(UUID::randomUUID),
    )
}

private class FakeVaultStore : VaultStore {
    var stored: HealthVault? = null
    var loadFailure: Exception? = null
    var replaceFailure: Exception? = null
    var failNextSave = false
    var saveCount = 0

    override suspend fun load(): HealthVault? {
        loadFailure?.let { throw it }
        return stored
    }

    override suspend fun save(vault: HealthVault) {
        if (failNextSave) {
            failNextSave = false
            throw TestStorageFailure()
        }
        stored = vault
        saveCount++
    }

    override suspend fun replaceAtomically(
        vault: HealthVault,
        documentBlobs: List<RestoreDocumentBlob>,
    ) {
        replaceFailure?.let { throw it }
        documentBlobs.forEach { source -> source.openStream().use { input -> input.readBytes() } }
        stored = vault
    }

    override suspend fun delete() {
        stored = null
    }
}

private class FakeDocumentBlobStore : DocumentBlobStore {
    val contents = mutableMapOf<UUID, ByteArray>()
    private val staged = mutableMapOf<UUID, Pair<UUID, ByteArray>>()
    private val generation = UUID.randomUUID()

    override suspend fun stage(blobId: UUID, plaintext: ByteArray): StagedDocumentBlob {
        val token = UUID.randomUUID()
        staged[token] = blobId to plaintext.copyOf()
        return StagedDocumentBlob(blobId, generation, token)
    }

    override suspend fun commit(stagedBlob: StagedDocumentBlob) {
        val (blobId, bytes) = staged.remove(stagedBlob.token) ?: error("Missing staged blob")
        contents[blobId] = bytes
    }

    override suspend fun discard(stagedBlob: StagedDocumentBlob) {
        staged.remove(stagedBlob.token)?.second?.fill(0)
    }

    override suspend fun read(blobId: UUID): ByteArray? = contents[blobId]?.copyOf()

    override suspend fun copyTo(blobId: UUID, output: OutputStream): Long {
        val content = contents[blobId] ?: return 0
        output.write(content)
        return content.size.toLong()
    }

    override suspend fun delete(blobId: UUID) {
        contents.remove(blobId)?.fill(0)
    }

    override suspend fun listIds(): Set<UUID> = contents.keys

    override suspend fun cleanupOrphans(referencedBlobIds: Set<UUID>) {
        contents.keys.filterNot(referencedBlobIds::contains).forEach(contents::remove)
    }
}

private class TestStorageFailure : RuntimeException()
