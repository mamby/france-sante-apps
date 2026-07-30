package net.mamby.health.data

import java.io.OutputStream
import java.security.GeneralSecurityException
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.mamby.health.core.model.Appointment
import net.mamby.health.core.model.DocumentCategory
import net.mamby.health.core.model.DocumentSearch
import net.mamby.health.core.model.EmergencyContact
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.core.model.Medication
import net.mamby.health.core.model.Reminder
import net.mamby.health.core.model.UnsupportedVaultVersionException
import net.mamby.health.core.model.Vaccination
import net.mamby.health.core.model.requireValid

fun interface UuidGenerator {
    fun next(): UUID
}

@Singleton
class DefaultVaultRepository @Inject constructor(
    private val vaultStore: VaultStore,
    private val documentBlobStore: DocumentBlobStore,
    private val demoVaultProvider: DemoVaultProvider,
    private val clock: Clock,
    private val uuidGenerator: UuidGenerator,
) : VaultRepository {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<VaultState>(VaultState.Loading)

    override val state: StateFlow<VaultState> = mutableState.asStateFlow()

    override suspend fun initialize() {
        mutex.withLock {
            mutableState.value = VaultState.Loading
            try {
                val vault = vaultStore.load()
                if (vault == null) {
                    mutableState.value = VaultState.Demo(demoVaultProvider.create(clock.instant()).requireValid())
                } else {
                    documentBlobStore.cleanupOrphans(vault.documents.map(MedicalDocument::blobId).toSet())
                    mutableState.value = VaultState.Ready(vault)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: UnsupportedVaultVersionException) {
                mutableState.value = VaultState.Unreadable(
                    UnreadableReason.UNSUPPORTED_VERSION,
                    error.message,
                )
            } catch (error: VaultKeyUnavailableException) {
                mutableState.value = VaultState.Unreadable(UnreadableReason.KEY_UNAVAILABLE, error.message)
            } catch (error: VaultCorruptionException) {
                mutableState.value = VaultState.Unreadable(UnreadableReason.CORRUPT, error.message)
            } catch (error: GeneralSecurityException) {
                mutableState.value = VaultState.Unreadable(UnreadableReason.KEY_UNAVAILABLE, error.message)
            } catch (error: Exception) {
                mutableState.value = VaultState.Unreadable(UnreadableReason.IO_FAILURE, error.message)
            }
        }
    }

    override suspend fun createEmpty(displayName: String) {
        mutex.withLock {
            check(mutableState.value is VaultState.Demo) {
                "A real vault can only be created from the sample workspace."
            }
            val now = clock.instant()
            val vault = HealthVault.empty(
                now = now,
                profileId = uuidGenerator.next(),
                displayName = displayName.trim(),
            ).copy(revision = 1).requireValid()
            vaultStore.save(vault)
            mutableState.value = VaultState.Ready(vault)
        }
    }

    override suspend fun updateProfile(profile: HealthProfile) = mutate { current, now ->
        current.copy(profile = profile.copy(lastUpdatedAt = now))
    }

    override suspend fun upsertEmergencyContact(contact: EmergencyContact) = mutate { current, now ->
        current.copy(
            profile = current.profile.copy(
                emergencyContacts = current.profile.emergencyContacts.upsert(contact, EmergencyContact::id),
                lastUpdatedAt = now,
            ),
        )
    }

    override suspend fun deleteEmergencyContact(contactId: UUID) = mutate { current, now ->
        current.copy(
            profile = current.profile.copy(
                emergencyContacts = current.profile.emergencyContacts.filterNot { it.id == contactId },
                lastUpdatedAt = now,
            ),
        )
    }

    override suspend fun importDocument(
        draft: MedicalDocumentDraft,
        imported: ImportedDocumentData,
    ): MedicalDocument = mutex.withLock {
        val current = readyVault()
        validateImport(draft, imported)
        val now = clock.instant()
        val blobId = uuidGenerator.next()
        val document = MedicalDocument(
            id = uuidGenerator.next(),
            title = draft.title.trim(),
            category = draft.category,
            documentDate = draft.documentDate,
            source = draft.source.trim(),
            notes = draft.notes?.trim()?.takeIf(String::isNotEmpty),
            tags = draft.tags.map(String::trim).filter(String::isNotEmpty).distinct(),
            blobId = blobId,
            mimeType = imported.mimeType,
            sizeBytes = imported.sizeBytes,
            originalFileName = imported.displayName,
            updatedAt = now,
        )
        var staged: StagedDocumentBlob? = null
        var committed = false
        try {
            staged = documentBlobStore.stage(blobId, imported.content)
            documentBlobStore.commit(staged)
            committed = true
            val next = current.copy(documents = current.documents + document).nextRevision(now)
            try {
                vaultStore.save(next)
            } catch (error: Throwable) {
                runCatching { documentBlobStore.delete(blobId) }
                throw error
            }
            mutableState.value = VaultState.Ready(next)
            document
        } finally {
            if (!committed && staged != null) runCatching { documentBlobStore.discard(staged) }
            imported.content.fill(0)
        }
    }

    override suspend fun updateDocument(document: MedicalDocument) = mutate { current, now ->
        val existing = current.documents.singleOrNull { it.id == document.id }
            ?: throw NoSuchElementException("Document not found: ${document.id}")
        val updated = document.copy(
            blobId = existing.blobId,
            mimeType = existing.mimeType,
            sizeBytes = existing.sizeBytes,
            originalFileName = existing.originalFileName,
            updatedAt = now,
        )
        current.copy(documents = current.documents.upsert(updated, MedicalDocument::id))
    }

    override suspend fun deleteDocument(documentId: UUID) {
        mutex.withLock {
            val current = readyVault()
            val document = current.documents.singleOrNull { it.id == documentId } ?: return
            val now = clock.instant()
            val next = current.copy(
                documents = current.documents.filterNot { it.id == documentId },
                appointments = current.appointments.map { appointment ->
                    appointment.copy(
                        relatedDocumentIds = appointment.relatedDocumentIds.filterNot(documentId::equals),
                    )
                },
            ).nextRevision(now)
            vaultStore.save(next)
            mutableState.value = VaultState.Ready(next)
            runCatching { documentBlobStore.delete(document.blobId) }
        }
    }

    override suspend fun upsertMedication(medication: Medication) = mutate { current, now ->
        current.copy(
            medications = current.medications.upsert(medication.copy(updatedAt = now), Medication::id),
        )
    }

    override suspend fun deleteMedication(medicationId: UUID) = mutate { current, _ ->
        current.copy(medications = current.medications.filterNot { it.id == medicationId })
    }

    override suspend fun upsertAppointment(appointment: Appointment) = mutate { current, now ->
        current.copy(
            appointments = current.appointments.upsert(appointment.copy(updatedAt = now), Appointment::id),
        )
    }

    override suspend fun deleteAppointment(appointmentId: UUID) = mutate { current, _ ->
        current.copy(appointments = current.appointments.filterNot { it.id == appointmentId })
    }

    override suspend fun upsertVaccination(vaccination: Vaccination) = mutate { current, now ->
        current.copy(
            vaccinations = current.vaccinations.upsert(vaccination.copy(updatedAt = now), Vaccination::id),
        )
    }

    override suspend fun deleteVaccination(vaccinationId: UUID) = mutate { current, _ ->
        current.copy(vaccinations = current.vaccinations.filterNot { it.id == vaccinationId })
    }

    override suspend fun upsertReminder(reminder: Reminder) = mutate { current, now ->
        current.copy(reminders = current.reminders.upsert(reminder.copy(updatedAt = now), Reminder::id))
    }

    override suspend fun deleteReminder(reminderId: UUID) = mutate { current, _ ->
        current.copy(reminders = current.reminders.filterNot { it.id == reminderId })
    }

    override fun searchDocuments(
        query: String,
        category: DocumentCategory,
    ): Flow<List<MedicalDocument>> = state
        .map { vaultState ->
            val documents = when (vaultState) {
                is VaultState.Demo -> vaultState.vault.documents
                is VaultState.Ready -> vaultState.vault.documents
                is VaultState.Loading,
                is VaultState.Unreadable,
                -> emptyList()
            }
            DocumentSearch.search(documents, query, category)
        }
        .distinctUntilChanged()

    override suspend fun exportSnapshot(): HealthVault = mutex.withLock { readyVault() }

    override suspend fun readDocumentBlob(blobId: UUID): ByteArray? = mutex.withLock {
        val document = referencedDocument(blobId)
        val plaintext = documentBlobStore.read(blobId)
            ?: throw VaultCorruptionException("Encrypted document blob is missing.")
        if (plaintext.size.toLong() != document.sizeBytes) {
            plaintext.fill(0)
            throw VaultCorruptionException("Encrypted document size does not match its metadata.")
        }
        plaintext
    }

    override suspend fun copyDocumentBlob(blobId: UUID, output: OutputStream): Long = mutex.withLock {
        val document = referencedDocument(blobId)
        val copied = documentBlobStore.copyTo(blobId, output)
        if (copied != document.sizeBytes) {
            throw VaultCorruptionException("Encrypted document size does not match its metadata.")
        }
        copied
    }

    override suspend fun restore(
        vault: HealthVault,
        documentBlobs: List<RestoreDocumentBlob>,
    ) {
        mutex.withLock {
            vault.requireValid()
            vaultStore.replaceAtomically(vault, documentBlobs)
            val restored = vaultStore.load()
                ?: throw VaultCorruptionException("Restored vault metadata is missing.")
            documentBlobStore.cleanupOrphans(restored.documents.map(MedicalDocument::blobId).toSet())
            mutableState.value = VaultState.Ready(restored)
        }
    }

    override suspend fun deleteVault() {
        mutex.withLock {
            vaultStore.delete()
            mutableState.value = VaultState.Demo(demoVaultProvider.create(clock.instant()).requireValid())
        }
    }

    private suspend fun mutate(transform: (HealthVault, Instant) -> HealthVault) {
        mutex.withLock {
            val current = readyVault()
            val now = clock.instant()
            val next = transform(current, now).nextRevision(now)
            vaultStore.save(next)
            mutableState.value = VaultState.Ready(next)
        }
    }

    private fun readyVault(): HealthVault = when (val current = mutableState.value) {
        is VaultState.Ready -> current.vault
        is VaultState.Demo -> error("Create a real vault before changing health data.")
        is VaultState.Loading -> error("Vault has not finished loading.")
        is VaultState.Unreadable -> error("Vault is unreadable and must be recovered or deleted.")
    }

    private fun referencedDocument(blobId: UUID): MedicalDocument =
        requireNotNull(readyVault().documents.singleOrNull { it.blobId == blobId }) {
            "Unknown document blob: $blobId"
        }

    private fun validateImport(draft: MedicalDocumentDraft, imported: ImportedDocumentData) {
        require(draft.title.isNotBlank()) { "Document title is required." }
        require(draft.category != DocumentCategory.ALL) { "A document category must be selected." }
        require(imported.mimeType in SUPPORTED_DOCUMENT_MIME_TYPES) { "Unsupported document MIME type." }
        require(imported.sizeBytes == imported.content.size.toLong()) { "Document size is inconsistent." }
        require(imported.sizeBytes in 0..DocumentImportPolicy.MAX_DOCUMENT_BYTES) {
            "Document exceeds the supported size limit."
        }
    }

    private fun HealthVault.nextRevision(now: Instant): HealthVault = copy(
        revision = Math.addExact(revision, 1),
        updatedAt = now,
    ).requireValid()

    private fun <T, K> List<T>.upsert(value: T, key: (T) -> K): List<T> {
        val targetKey = key(value)
        val index = indexOfFirst { key(it) == targetKey }
        if (index < 0) return this + value
        return toMutableList().apply { this[index] = value }
    }

    companion object {
        private val SUPPORTED_DOCUMENT_MIME_TYPES = setOf(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp",
        )
    }
}
