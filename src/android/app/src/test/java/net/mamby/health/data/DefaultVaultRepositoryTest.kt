package net.mamby.health.data

import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.runTest
import net.mamby.health.core.model.DocumentCategory
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
        val repository = repository(FakeVaultStore(), FakeDocumentBlobStore(), FakeSelectedProfileStore())

        repository.initialize()

        assertEquals(VaultState.Missing, repository.state.value)
    }

    @Test
    fun corruptionBecomesUnreadableWithoutSavingReplacementData() = runTest {
        val store = FakeVaultStore().apply { loadFailure = VaultCorruptionException("tampered") }
        val repository = repository(store, FakeDocumentBlobStore(), FakeSelectedProfileStore())

        repository.initialize()

        val state = repository.state.value as VaultState.Unreadable
        assertEquals(UnreadableReason.CORRUPT, state.reason)
        assertEquals(0, store.saveCount)
    }

    @Test
    fun explicitProfileOwnershipRejectsStaleWorkAndDoesNotUseSelection() = runTest {
        val repository = repository(FakeVaultStore(), FakeDocumentBlobStore(), FakeSelectedProfileStore())
        repository.initialize()
        repository.createVault("Amina")
        val first = (repository.state.value as VaultState.Ready).selectedProfileId
        val second = repository.addProfile("Sam")
        val revisionAfterAdd = repository.exportSnapshot().revision
        repository.selectProfile(first)

        repository.upsertMedication(
            second,
            Medication(UUID.randomUUID(), "Medication", "5 mg", "Daily", updatedAt = Instant.EPOCH),
        )

        val vault = repository.exportSnapshot()
        assertTrue(vault.profileRecord(first)!!.medications.isEmpty())
        assertEquals(1, vault.profileRecord(second)!!.medications.size)
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
    fun selectionDoesNotChangeRevisionAndDeletionUsesNextThenPreviousFallback() = runTest {
        val selectedStore = FakeSelectedProfileStore()
        val repository = repository(FakeVaultStore(), FakeDocumentBlobStore(), selectedStore)
        repository.initialize()
        repository.createVault("First")
        val first = (repository.state.value as VaultState.Ready).selectedProfileId
        val second = repository.addProfile("Second")
        val third = repository.addProfile("Third")
        val revision = repository.exportSnapshot().revision

        repository.selectProfile(second)
        assertEquals(revision, repository.exportSnapshot().revision)
        repository.deleteProfile(second)
        assertEquals(third, (repository.state.value as VaultState.Ready).selectedProfileId)
        repository.deleteProfile(third)
        assertEquals(first, (repository.state.value as VaultState.Ready).selectedProfileId)
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.test.runTest { repository.deleteProfile(first) }
        }
        assertEquals(first, selectedStore.value)
    }

    @Test
    fun importFailureCleansBlobAndCallerPlaintext() = runTest {
        val store = FakeVaultStore()
        val blobs = FakeDocumentBlobStore()
        val repository = repository(store, blobs, FakeSelectedProfileStore())
        repository.initialize()
        repository.createVault("Owner")
        val profileId = (repository.state.value as VaultState.Ready).selectedProfileId
        val content = "%PDF-private".encodeToByteArray()
        store.failNextSave = true

        try {
            repository.importDocument(
                profileId,
                MedicalDocumentDraft("Document", DocumentCategory.REPORTS, LocalDate.of(2026, 7, 30), "Clinic"),
                ImportedDocumentData("report.pdf", "application/pdf", content.size.toLong(), content),
            )
        } catch (_: TestStorageFailure) {
            // Expected.
        }

        assertTrue(blobs.contents.isEmpty())
        assertTrue(content.all { it == 0.toByte() })
        assertTrue(repository.exportSnapshot().profileRecord(profileId)!!.documents.isEmpty())
    }

    @Test
    fun failedRestoreLeavesCurrentReadyStateUntouched() = runTest {
        val store = FakeVaultStore()
        val repository = repository(store, FakeDocumentBlobStore(), FakeSelectedProfileStore())
        repository.initialize()
        repository.createVault("Existing")
        val before = repository.exportSnapshot()
        store.replaceFailure = TestStorageFailure()

        try {
            repository.restore(HealthVault.empty(now, displayName = "Replacement"), emptyList())
        } catch (_: TestStorageFailure) {
            // Expected.
        }

        assertEquals(before, repository.exportSnapshot())
    }

    private fun repository(
        store: FakeVaultStore,
        blobs: FakeDocumentBlobStore,
        selected: FakeSelectedProfileStore,
    ) = DefaultVaultRepository(store, blobs, selected, clock, UuidGenerator(UUID::randomUUID))
}

private class FakeSelectedProfileStore : SelectedProfileStore {
    var value: UUID? = null
    override suspend fun load(): UUID? = value
    override suspend fun save(profileId: UUID) { value = profileId }
    override suspend fun clear() { value = null }
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
