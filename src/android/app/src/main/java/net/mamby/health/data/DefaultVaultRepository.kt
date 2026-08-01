package net.mamby.health.data

import java.io.OutputStream
import java.security.GeneralSecurityException
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.mamby.health.core.model.Appointment
import net.mamby.health.core.model.EmergencyContact
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.core.model.Medication
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.Reminder
import net.mamby.health.core.model.UnsupportedVaultVersionException
import net.mamby.health.core.model.Vaccination
import net.mamby.health.core.model.allDocuments
import net.mamby.health.core.model.profileRecord
import net.mamby.health.core.model.requireValid

fun interface UuidGenerator {
    fun next(): UUID
}

@Singleton
class DefaultVaultRepository @Inject constructor(
    private val vaultStore: VaultStore,
    private val documentBlobStore: DocumentBlobStore,
    private val selectedProfileStore: SelectedProfileStore,
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
                    runCatching { selectedProfileStore.clear() }
                    mutableState.value = VaultState.Missing
                    return@withLock
                }
                documentBlobStore.cleanupOrphans(vault.allDocuments().map(MedicalDocument::blobId).toSet())
                val requested = selectedProfileStore.load()
                val selected = requested?.takeIf { id -> vault.profiles.any { it.profile.id == id } }
                    ?: vault.profiles.first().profile.id
                if (selected != requested) runCatching { selectedProfileStore.save(selected) }
                mutableState.value = VaultState.Ready(vault, selected)
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

    override suspend fun createVault(firstProfileName: String) {
        mutex.withLock {
            check(mutableState.value is VaultState.Missing) {
                "A new vault can only be created when no vault exists."
            }
            val displayName = firstProfileName.trim()
            require(displayName.isNotEmpty()) { "Profile name is required." }
            val now = clock.instant()
            val profileId = uuidGenerator.next()
            val vault = HealthVault.empty(now, profileId, displayName)
                .copy(revision = 1)
                .requireValid()
            vaultStore.save(vault)
            runCatching { selectedProfileStore.save(profileId) }
            mutableState.value = VaultState.Ready(vault, profileId)
        }
    }

    override suspend fun addProfile(displayName: String): UUID = mutex.withLock {
        val current = readyState()
        val normalizedName = displayName.trim()
        require(normalizedName.isNotEmpty()) { "Profile name is required." }
        val now = clock.instant()
        val profileId = uuidGenerator.next()
        val record = ProfileRecord(
            profile = HealthProfile(
                id = profileId,
                displayName = normalizedName,
                lastUpdatedAt = now,
            ),
        )
        val next = current.vault.copy(profiles = current.vault.profiles + record).nextRevision(now)
        vaultStore.save(next)
        runCatching { selectedProfileStore.save(profileId) }
        mutableState.value = VaultState.Ready(next, profileId)
        profileId
    }

    override suspend fun selectProfile(profileId: UUID) {
        mutex.withLock {
            val current = readyState()
            current.vault.profileRecord(profileId)
            if (current.selectedProfileId == profileId) return@withLock
            runCatching { selectedProfileStore.save(profileId) }
            mutableState.value = VaultState.Ready(current.vault, profileId)
        }
    }

    override suspend fun updateProfile(profileId: UUID, profile: HealthProfile) {
        require(profile.id == profileId) { "Profile identifier cannot be changed." }
        require(profile.displayName.isNotBlank()) { "Profile name is required." }
        mutateProfile(profileId) { record, now ->
            record.copy(profile = profile.copy(displayName = profile.displayName.trim(), lastUpdatedAt = now))
        }
    }

    override suspend fun deleteProfile(profileId: UUID) {
        mutex.withLock {
            val current = readyState()
            require(current.vault.profiles.size > 1) { "The final profile cannot be deleted." }
            val index = current.vault.profiles.indexOfFirst { it.profile.id == profileId }
            if (index < 0) throw NoSuchElementException("Profile not found: $profileId")
            val removed = current.vault.profiles[index]
            val remaining = current.vault.profiles.filterNot { it.profile.id == profileId }
            val selected = if (current.selectedProfileId == profileId) {
                remaining[index.coerceAtMost(remaining.lastIndex)].profile.id
            } else {
                current.selectedProfileId
            }
            val next = current.vault.copy(profiles = remaining).nextRevision(clock.instant())
            vaultStore.save(next)
            runCatching { selectedProfileStore.save(selected) }
            mutableState.value = VaultState.Ready(next, selected)
            removed.documents.forEach { document -> runCatching { documentBlobStore.delete(document.blobId) } }
        }
    }

    override suspend fun upsertEmergencyContact(profileId: UUID, contact: EmergencyContact) =
        mutateProfile(profileId) { record, now ->
            record.copy(
                profile = record.profile.copy(
                    emergencyContacts = record.profile.emergencyContacts.upsert(contact, EmergencyContact::id),
                    lastUpdatedAt = now,
                ),
            )
        }

    override suspend fun deleteEmergencyContact(profileId: UUID, contactId: UUID) =
        mutateProfile(profileId) { record, now ->
            record.copy(
                profile = record.profile.copy(
                    emergencyContacts = record.profile.emergencyContacts.filterNot { it.id == contactId },
                    lastUpdatedAt = now,
                ),
            )
        }

    override suspend fun importDocument(
        profileId: UUID,
        draft: MedicalDocumentDraft,
        imported: ImportedDocumentData,
    ): MedicalDocument = mutex.withLock {
        val current = readyState()
        val record = current.vault.profileRecord(profileId)
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
            val next = current.vault
                .replaceProfile(record.copy(documents = record.documents + document))
                .nextRevision(now)
            try {
                vaultStore.save(next)
            } catch (error: Throwable) {
                runCatching { documentBlobStore.delete(blobId) }
                throw error
            }
            mutableState.value = VaultState.Ready(next, current.selectedProfileId)
            document
        } finally {
            if (!committed && staged != null) runCatching { documentBlobStore.discard(staged) }
            imported.content.fill(0)
        }
    }

    override suspend fun updateDocument(profileId: UUID, document: MedicalDocument) =
        mutateProfile(profileId) { record, now ->
            val existing = record.documents.singleOrNull { it.id == document.id }
                ?: throw NoSuchElementException("Document not found: ${document.id}")
            val updated = document.copy(
                blobId = existing.blobId,
                mimeType = existing.mimeType,
                sizeBytes = existing.sizeBytes,
                originalFileName = existing.originalFileName,
                updatedAt = now,
            )
            record.copy(documents = record.documents.upsert(updated, MedicalDocument::id))
        }

    override suspend fun deleteDocument(profileId: UUID, documentId: UUID) {
        mutex.withLock {
            val current = readyState()
            val record = current.vault.profileRecord(profileId)
            val document = record.documents.singleOrNull { it.id == documentId } ?: return
            val updated = record.copy(
                documents = record.documents.filterNot { it.id == documentId },
                appointments = record.appointments.map { appointment ->
                    appointment.copy(
                        relatedDocumentIds = appointment.relatedDocumentIds.filterNot(documentId::equals),
                    )
                },
            )
            val next = current.vault.replaceProfile(updated).nextRevision(clock.instant())
            vaultStore.save(next)
            mutableState.value = VaultState.Ready(next, current.selectedProfileId)
            runCatching { documentBlobStore.delete(document.blobId) }
        }
    }

    override suspend fun upsertMedication(profileId: UUID, medication: Medication) =
        mutateProfile(profileId) { record, now ->
            record.copy(medications = record.medications.upsert(medication.copy(updatedAt = now), Medication::id))
        }

    override suspend fun deleteMedication(profileId: UUID, medicationId: UUID) =
        mutateProfile(profileId) { record, _ ->
            record.copy(medications = record.medications.filterNot { it.id == medicationId })
        }

    override suspend fun upsertAppointment(profileId: UUID, appointment: Appointment) =
        mutateProfile(profileId) { record, now ->
            record.copy(appointments = record.appointments.upsert(appointment.copy(updatedAt = now), Appointment::id))
        }

    override suspend fun deleteAppointment(profileId: UUID, appointmentId: UUID) =
        mutateProfile(profileId) { record, _ ->
            record.copy(appointments = record.appointments.filterNot { it.id == appointmentId })
        }

    override suspend fun upsertVaccination(profileId: UUID, vaccination: Vaccination) =
        mutateProfile(profileId) { record, now ->
            record.copy(vaccinations = record.vaccinations.upsert(vaccination.copy(updatedAt = now), Vaccination::id))
        }

    override suspend fun deleteVaccination(profileId: UUID, vaccinationId: UUID) =
        mutateProfile(profileId) { record, _ ->
            record.copy(vaccinations = record.vaccinations.filterNot { it.id == vaccinationId })
        }

    override suspend fun upsertReminder(profileId: UUID, reminder: Reminder) =
        mutateProfile(profileId) { record, now ->
            record.copy(reminders = record.reminders.upsert(reminder.copy(updatedAt = now), Reminder::id))
        }

    override suspend fun deleteReminder(profileId: UUID, reminderId: UUID) =
        mutateProfile(profileId) { record, _ ->
            record.copy(reminders = record.reminders.filterNot { it.id == reminderId })
        }

    override suspend fun exportSnapshot(): HealthVault = mutex.withLock { readyState().vault }

    override suspend fun readDocumentBlob(profileId: UUID, blobId: UUID): ByteArray? = mutex.withLock {
        val document = referencedDocument(profileId, blobId)
        val plaintext = documentBlobStore.read(blobId)
            ?: throw VaultCorruptionException("Encrypted document blob is missing.")
        if (plaintext.size.toLong() != document.sizeBytes) {
            plaintext.fill(0)
            throw VaultCorruptionException("Encrypted document size does not match its metadata.")
        }
        plaintext
    }

    override suspend fun copyDocumentBlob(profileId: UUID, blobId: UUID, output: OutputStream): Long =
        mutex.withLock {
            val document = referencedDocument(profileId, blobId)
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
            val previousSelection = (mutableState.value as? VaultState.Ready)?.selectedProfileId
            vaultStore.replaceAtomically(vault, documentBlobs)
            val restored = vaultStore.load()
                ?: throw VaultCorruptionException("Restored vault metadata is missing.")
            documentBlobStore.cleanupOrphans(restored.allDocuments().map(MedicalDocument::blobId).toSet())
            val selected = previousSelection?.takeIf { id -> restored.profiles.any { it.profile.id == id } }
                ?: restored.profiles.first().profile.id
            runCatching { selectedProfileStore.save(selected) }
            mutableState.value = VaultState.Ready(restored, selected)
        }
    }

    override suspend fun deleteVault() {
        mutex.withLock {
            vaultStore.delete()
            runCatching { selectedProfileStore.clear() }
            mutableState.value = VaultState.Missing
        }
    }

    private suspend fun mutateProfile(
        profileId: UUID,
        transform: (ProfileRecord, Instant) -> ProfileRecord,
    ) {
        mutex.withLock {
            val current = readyState()
            val now = clock.instant()
            val updated = transform(current.vault.profileRecord(profileId), now)
            require(updated.profile.id == profileId) { "Profile identifier cannot be changed." }
            val next = current.vault.replaceProfile(updated).nextRevision(now)
            vaultStore.save(next)
            mutableState.value = VaultState.Ready(next, current.selectedProfileId)
        }
    }

    private fun readyState(): VaultState.Ready = when (val current = mutableState.value) {
        is VaultState.Ready -> current
        VaultState.Missing -> error("Create a vault before changing health data.")
        VaultState.Loading -> error("Vault has not finished loading.")
        is VaultState.Unreadable -> error("Vault is unreadable and must be recovered or deleted.")
    }

    private fun referencedDocument(profileId: UUID, blobId: UUID): MedicalDocument =
        requireNotNull(readyState().vault.profileRecord(profileId).documents.singleOrNull { it.blobId == blobId }) {
            "Unknown document blob for profile: $blobId"
        }

    private fun validateImport(draft: MedicalDocumentDraft, imported: ImportedDocumentData) {
        require(draft.title.isNotBlank()) { "Document title is required." }
        require(draft.category != net.mamby.health.core.model.DocumentCategory.ALL) {
            "A document category must be selected."
        }
        require(imported.mimeType in SUPPORTED_DOCUMENT_MIME_TYPES) { "Unsupported document MIME type." }
        require(imported.sizeBytes == imported.content.size.toLong()) { "Document size is inconsistent." }
        require(imported.sizeBytes in 0..DocumentImportPolicy.MAX_DOCUMENT_BYTES) {
            "Document exceeds the supported size limit."
        }
    }

    private fun HealthVault.replaceProfile(value: ProfileRecord): HealthVault = copy(
        profiles = profiles.upsert(value) { it.profile.id },
    )

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

    private companion object {
        val SUPPORTED_DOCUMENT_MIME_TYPES = setOf(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp",
        )
    }
}
