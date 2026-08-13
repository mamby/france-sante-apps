package net.mamby.health.data

import java.io.OutputStream
import java.net.URI
import java.security.GeneralSecurityException
import java.time.Clock
import java.time.Instant
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.mamby.health.core.model.BuiltInDocumentCategoryPreference
import net.mamby.health.core.model.CareDirective
import net.mamby.health.core.model.CustomDocumentCategory
import net.mamby.health.core.model.CustomMeasurementType
import net.mamby.health.core.model.DocumentCategoryRef
import net.mamby.health.core.model.EmergencyContact
import net.mamby.health.core.model.FamilyHistoryEntry
import net.mamby.health.core.model.HealthIdentifier
import net.mamby.health.core.model.HealthMeasurement
import net.mamby.health.core.model.HealthNote
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.core.model.Medication
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.Schedule
import net.mamby.health.core.model.UnsupportedVaultVersionException
import net.mamby.health.core.model.Vaccination
import net.mamby.health.core.model.VaultContact
import net.mamby.health.core.model.allDocuments
import net.mamby.health.core.model.isDocumentCategoryAvailable
import net.mamby.health.core.model.profileRecord
import net.mamby.health.core.model.requireValid
import net.mamby.health.core.model.normalized

fun interface UuidGenerator {
    fun next(): UUID
}

@Singleton
class DefaultVaultRepository @Inject constructor(
    private val vaultStore: VaultStore,
    private val documentBlobStore: DocumentBlobStore,
    private val clock: Clock,
    private val uuidGenerator: UuidGenerator,
) : VaultRepository {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<VaultState>(VaultState.Loading)

    override val state: StateFlow<VaultState> = mutableState.asStateFlow()

    override suspend fun initialize() {
        mutex.withLock {
            if (mutableState.value !is VaultState.Loading) return@withLock
            try {
                val vault = vaultStore.load()
                if (vault == null) {
                    mutableState.value = VaultState.Missing
                    return@withLock
                }
                documentBlobStore.cleanupOrphans(vault.allDocuments().map(MedicalDocument::blobId).toSet())
                mutableState.value = VaultState.Ready(vault)
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
            mutableState.value = VaultState.Ready(vault)
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
        mutableState.value = VaultState.Ready(next)
        profileId
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
            val next = current.vault.copy(profiles = remaining).nextRevision(clock.instant())
            vaultStore.save(next)
            mutableState.value = VaultState.Ready(next)
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
        validateImport(record, draft, imported)
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
            mutableState.value = VaultState.Ready(next)
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
            val now = clock.instant()
            val updated = record.copy(
                documents = record.documents.filterNot { it.id == documentId },
                directives = record.directives.map { directive ->
                    if (documentId in directive.relatedDocumentIds) {
                        directive.copy(
                            relatedDocumentIds = directive.relatedDocumentIds.filterNot(documentId::equals),
                            updatedAt = now,
                        )
                    } else directive
                },
            )
            val next = current.vault.replaceProfile(updated).nextRevision(now)
            vaultStore.save(next)
            mutableState.value = VaultState.Ready(next)
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

    override suspend fun upsertSchedule(schedule: Schedule) = mutateVault { vault, now ->
        val normalized = schedule.copy(updatedAt = now).normalized()
        vault.copy(schedules = vault.schedules.upsert(normalized, Schedule::id))
    }

    override suspend fun deleteSchedule(scheduleId: UUID) = mutateVault { vault, _ ->
        vault.copy(schedules = vault.schedules.filterNot { it.id == scheduleId })
    }

    override suspend fun upsertVaccination(profileId: UUID, vaccination: Vaccination) =
        mutateProfile(profileId) { record, now ->
            record.copy(vaccinations = record.vaccinations.upsert(vaccination.copy(updatedAt = now), Vaccination::id))
        }

    override suspend fun deleteVaccination(profileId: UUID, vaccinationId: UUID) =
        mutateProfile(profileId) { record, _ ->
            record.copy(vaccinations = record.vaccinations.filterNot { it.id == vaccinationId })
        }

    override suspend fun upsertHealthNote(note: HealthNote) =
        mutateVault { vault, now ->
            vault.copy(notes = vault.notes.upsert(note.copy(updatedAt = now), HealthNote::id))
        }

    override suspend fun deleteHealthNote(noteId: UUID) =
        mutateVault { vault, _ ->
            vault.copy(notes = vault.notes.filterNot { it.id == noteId })
        }

    override suspend fun upsertMeasurement(profileId: UUID, measurement: HealthMeasurement) =
        mutateProfile(profileId) { record, now ->
            record.copy(
                measurements = record.measurements.upsert(
                    measurement.copy(updatedAt = now),
                    HealthMeasurement::id,
                ),
            )
        }

    override suspend fun deleteMeasurement(profileId: UUID, measurementId: UUID) =
        mutateProfile(profileId) { record, _ ->
            record.copy(measurements = record.measurements.filterNot { it.id == measurementId })
        }

    override suspend fun upsertCustomMeasurementType(profileId: UUID, type: CustomMeasurementType) =
        mutateProfile(profileId) { record, now ->
            val normalized = type.copy(
                name = type.name.trim(),
                suggestedUnit = type.suggestedUnit.trim(),
                updatedAt = now,
            )
            record.copy(
                customMeasurementTypes = record.customMeasurementTypes.upsert(
                    normalized,
                    CustomMeasurementType::id,
                ),
            )
        }

    override suspend fun deleteCustomMeasurementType(profileId: UUID, typeId: UUID) =
        mutateProfile(profileId) { record, _ ->
            require(
                record.measurements.none {
                    (it.type as? net.mamby.health.core.model.MeasurementTypeRef.Custom)?.id == typeId
                },
            ) { "Delete measurements that use this custom type first." }
            record.copy(
                customMeasurementTypes = record.customMeasurementTypes.filterNot { it.id == typeId },
            )
        }

    override suspend fun upsertContact(contact: VaultContact) = mutateVault { vault, now ->
        val normalized = contact.copy(
            name = contact.name.trim(),
            phoneNumbers = contact.phoneNumbers.normalizedContactValues(),
            emailAddresses = contact.emailAddresses.normalizedContactValues(),
            websites = contact.websites
                .mapNotNull { value -> value.trim().takeIf(String::isNotEmpty)?.normalizeWebsite() }
                .distinctByCaseInsensitiveValue(),
            addresses = contact.addresses.normalizedContactValues(),
            notes = contact.notes?.trim()?.takeIf(String::isNotEmpty),
            updatedAt = now,
        )
        vault.copy(contacts = vault.contacts.upsert(normalized, VaultContact::id))
    }

    override suspend fun deleteContact(contactId: UUID) = mutateVault { vault, _ ->
        vault.copy(contacts = vault.contacts.filterNot { it.id == contactId })
    }

    override suspend fun upsertFamilyHistoryEntry(profileId: UUID, entry: FamilyHistoryEntry) =
        mutateProfile(profileId) { record, now ->
            val normalized = entry.copy(
                relationship = entry.relationship.trim(),
                condition = entry.condition.trim(),
                notes = entry.notes?.trim()?.takeIf(String::isNotEmpty),
                updatedAt = now,
            )
            record.copy(familyHistory = record.familyHistory.upsert(normalized, FamilyHistoryEntry::id))
        }

    override suspend fun deleteFamilyHistoryEntry(profileId: UUID, entryId: UUID) =
        mutateProfile(profileId) { record, _ ->
            record.copy(familyHistory = record.familyHistory.filterNot { it.id == entryId })
        }

    override suspend fun upsertCareDirective(profileId: UUID, directive: CareDirective) =
        mutateProfile(profileId) { record, now ->
            val normalized = directive.copy(
                title = directive.title.trim(),
                text = directive.text.trim(),
                relatedDocumentIds = directive.relatedDocumentIds.distinct(),
                updatedAt = now,
            )
            record.copy(directives = record.directives.upsert(normalized, CareDirective::id))
        }

    override suspend fun deleteCareDirective(profileId: UUID, directiveId: UUID) =
        mutateProfile(profileId) { record, _ ->
            record.copy(directives = record.directives.filterNot { it.id == directiveId })
        }

    override suspend fun upsertHealthIdentifier(profileId: UUID, identifier: HealthIdentifier) =
        mutateProfile(profileId) { record, now ->
            val normalized = identifier.copy(
                label = identifier.label.trim(),
                value = identifier.value.trim(),
                issuer = identifier.issuer?.trim()?.takeIf(String::isNotEmpty),
                country = identifier.country?.trim()?.takeIf(String::isNotEmpty),
                notes = identifier.notes?.trim()?.takeIf(String::isNotEmpty),
                updatedAt = now,
            )
            record.copy(
                healthIdentifiers = record.healthIdentifiers.upsert(normalized, HealthIdentifier::id),
            )
        }

    override suspend fun deleteHealthIdentifier(profileId: UUID, identifierId: UUID) =
        mutateProfile(profileId) { record, _ ->
            record.copy(healthIdentifiers = record.healthIdentifiers.filterNot { it.id == identifierId })
        }

    override suspend fun upsertCustomDocumentCategory(
        profileId: UUID,
        category: CustomDocumentCategory,
    ) = mutateProfile(profileId) { record, now ->
        val normalized = category.copy(name = category.name.trim(), updatedAt = now)
        record.copy(
            customDocumentCategories = record.customDocumentCategories.upsert(
                normalized,
                CustomDocumentCategory::id,
            ),
        )
    }

    override suspend fun deleteCustomDocumentCategory(
        profileId: UUID,
        categoryId: UUID,
        replacement: DocumentCategoryRef?,
    ) = mutateProfile(profileId) { record, now ->
        val removed = DocumentCategoryRef.Custom(categoryId)
        val documents = record.documents.reclassify(record, removed, replacement, now)
        record.copy(
            documents = documents,
            customDocumentCategories = record.customDocumentCategories.filterNot { it.id == categoryId },
        )
    }

    override suspend fun updateBuiltInDocumentCategoryPreference(
        profileId: UUID,
        preference: BuiltInDocumentCategoryPreference,
        replacement: DocumentCategoryRef?,
    ) = mutateProfile(profileId) { record, now ->
        val normalized = preference.copy(
            labelOverride = preference.labelOverride?.trim()?.takeIf(String::isNotEmpty),
        )
        val category = DocumentCategoryRef.BuiltIn(preference.category)
        val documents = if (normalized.isHidden) {
            record.documents.reclassify(record, category, replacement, now)
        } else {
            record.documents
        }
        record.copy(
            documents = documents,
            builtInDocumentCategoryPreferences = record.builtInDocumentCategoryPreferences.upsert(
                normalized,
                BuiltInDocumentCategoryPreference::category,
            ),
        )
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
            vaultStore.replaceAtomically(vault, documentBlobs)
            val restored = vaultStore.load()
                ?: throw VaultCorruptionException("Restored vault metadata is missing.")
            documentBlobStore.cleanupOrphans(restored.allDocuments().map(MedicalDocument::blobId).toSet())
            mutableState.value = VaultState.Ready(restored)
        }
    }

    override suspend fun deleteVault() {
        mutex.withLock {
            vaultStore.delete()
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
            mutableState.value = VaultState.Ready(next)
        }
    }

    private suspend fun mutateVault(transform: (HealthVault, Instant) -> HealthVault) {
        mutex.withLock {
            val current = readyState()
            val now = clock.instant()
            val next = transform(current.vault, now).nextRevision(now)
            vaultStore.save(next)
            mutableState.value = VaultState.Ready(next)
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

    private fun validateImport(
        record: ProfileRecord,
        draft: MedicalDocumentDraft,
        imported: ImportedDocumentData,
    ) {
        require(draft.title.isNotBlank()) { "Document title is required." }
        require(record.isDocumentCategoryAvailable(draft.category)) {
            "A visible document category must be selected."
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

    private fun List<String>.normalizedContactValues(): List<String> =
        mapNotNull { value -> value.trim().takeIf(String::isNotEmpty) }
            .distinctByCaseInsensitiveValue()

    private fun List<String>.distinctByCaseInsensitiveValue(): List<String> {
        val seen = mutableSetOf<String>()
        return filter { seen.add(it.lowercase(Locale.ROOT)) }
    }

    private fun String.normalizeWebsite(): String {
        val normalized = if (EXPLICIT_URI_SCHEME.containsMatchIn(this)) this else "https://$this"
        val uri = runCatching { URI(normalized) }
            .getOrElse { throw IllegalArgumentException("Contact website is not a valid URL.", it) }
        require(
            uri.isAbsolute && uri.host != null &&
                (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)),
        ) { "Contact website must be an absolute HTTP or HTTPS URL." }
        return normalized
    }

    private fun List<MedicalDocument>.reclassify(
        record: ProfileRecord,
        removed: DocumentCategoryRef,
        replacement: DocumentCategoryRef?,
        now: Instant,
    ): List<MedicalDocument> {
        val affected = any { it.category == removed }
        if (!affected) return this
        requireNotNull(replacement) { "Choose a replacement for documents in this category." }
        require(replacement != removed) { "Replacement category must be different." }
        require(record.isDocumentCategoryAvailable(replacement)) {
            "Replacement category must be visible and belong to the same profile."
        }
        return map { document ->
            if (document.category == removed) {
                document.copy(category = replacement, updatedAt = now)
            } else document
        }
    }

    private companion object {
        val SUPPORTED_DOCUMENT_MIME_TYPES = setOf(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp",
        )
        val EXPLICIT_URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
    }
}
