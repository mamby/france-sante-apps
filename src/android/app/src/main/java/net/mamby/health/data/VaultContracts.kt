package net.mamby.health.data

import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow
import net.mamby.health.core.model.BuiltInDocumentCategoryPreference
import net.mamby.health.core.model.CareDirective
import net.mamby.health.core.model.CareDirectoryEntry
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
import net.mamby.health.core.model.Schedule
import net.mamby.health.core.model.Vaccination

sealed interface VaultState {
    data object Loading : VaultState

    data object Missing : VaultState

    data class Ready(val vault: HealthVault) : VaultState

    data class Unreadable(
        val reason: UnreadableReason,
        val diagnosticMessage: String? = null,
    ) : VaultState
}

enum class UnreadableReason {
    CORRUPT,
    UNSUPPORTED_VERSION,
    KEY_UNAVAILABLE,
    IO_FAILURE,
}

data class RestoreDocumentBlob(
    val blobId: UUID,
    val expectedSizeBytes: Long,
    val openStream: () -> InputStream,
)

data class StagedDocumentBlob(
    val blobId: UUID,
    val generationId: UUID,
    val token: UUID,
)

interface VaultStore {
    suspend fun load(): HealthVault?

    suspend fun save(vault: HealthVault)

    suspend fun replaceAtomically(
        vault: HealthVault,
        documentBlobs: List<RestoreDocumentBlob>,
    )

    suspend fun delete()
}

interface DocumentBlobStore {
    suspend fun stage(blobId: UUID, plaintext: ByteArray): StagedDocumentBlob

    suspend fun commit(stagedBlob: StagedDocumentBlob)

    suspend fun discard(stagedBlob: StagedDocumentBlob)

    suspend fun read(blobId: UUID): ByteArray?

    suspend fun copyTo(blobId: UUID, output: OutputStream): Long

    suspend fun delete(blobId: UUID)

    suspend fun listIds(): Set<UUID>

    suspend fun cleanupOrphans(referencedBlobIds: Set<UUID>)
}

data class MedicalDocumentDraft(
    val title: String,
    val category: DocumentCategoryRef,
    val documentDate: LocalDate,
    val source: String,
    val sourceEntryId: UUID? = null,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
)

data class ImportedDocumentData(
    val displayName: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val content: ByteArray,
)

interface VaultRepository {
    val state: StateFlow<VaultState>

    suspend fun initialize()

    suspend fun createVault(firstProfileName: String)

    suspend fun addProfile(displayName: String): UUID

    suspend fun updateProfile(profileId: UUID, profile: HealthProfile)

    suspend fun deleteProfile(profileId: UUID)

    suspend fun upsertEmergencyContact(profileId: UUID, contact: EmergencyContact)

    suspend fun deleteEmergencyContact(profileId: UUID, contactId: UUID)

    suspend fun importDocument(
        profileId: UUID,
        draft: MedicalDocumentDraft,
        imported: ImportedDocumentData,
    ): MedicalDocument

    suspend fun updateDocument(profileId: UUID, document: MedicalDocument)

    suspend fun deleteDocument(profileId: UUID, documentId: UUID)

    suspend fun upsertMedication(profileId: UUID, medication: Medication)

    suspend fun deleteMedication(profileId: UUID, medicationId: UUID)

    suspend fun upsertSchedule(schedule: Schedule)

    suspend fun deleteSchedule(scheduleId: UUID)

    suspend fun upsertVaccination(profileId: UUID, vaccination: Vaccination)

    suspend fun deleteVaccination(profileId: UUID, vaccinationId: UUID)

    suspend fun upsertHealthNote(note: HealthNote)

    suspend fun deleteHealthNote(noteId: UUID)

    suspend fun upsertMeasurement(profileId: UUID, measurement: HealthMeasurement)

    suspend fun deleteMeasurement(profileId: UUID, measurementId: UUID)

    suspend fun upsertCustomMeasurementType(profileId: UUID, type: CustomMeasurementType)

    suspend fun deleteCustomMeasurementType(profileId: UUID, typeId: UUID)

    suspend fun upsertCareDirectoryEntry(profileId: UUID, entry: CareDirectoryEntry)

    suspend fun deleteCareDirectoryEntry(profileId: UUID, entryId: UUID)

    suspend fun setPrimaryDoctor(profileId: UUID, entryId: UUID?)

    suspend fun upsertFamilyHistoryEntry(profileId: UUID, entry: FamilyHistoryEntry)

    suspend fun deleteFamilyHistoryEntry(profileId: UUID, entryId: UUID)

    suspend fun upsertCareDirective(profileId: UUID, directive: CareDirective)

    suspend fun deleteCareDirective(profileId: UUID, directiveId: UUID)

    suspend fun upsertHealthIdentifier(profileId: UUID, identifier: HealthIdentifier)

    suspend fun deleteHealthIdentifier(profileId: UUID, identifierId: UUID)

    suspend fun upsertCustomDocumentCategory(profileId: UUID, category: CustomDocumentCategory)

    suspend fun deleteCustomDocumentCategory(
        profileId: UUID,
        categoryId: UUID,
        replacement: DocumentCategoryRef? = null,
    )

    suspend fun updateBuiltInDocumentCategoryPreference(
        profileId: UUID,
        preference: BuiltInDocumentCategoryPreference,
        replacement: DocumentCategoryRef? = null,
    )

    suspend fun exportSnapshot(): HealthVault

    suspend fun readDocumentBlob(profileId: UUID, blobId: UUID): ByteArray?

    suspend fun copyDocumentBlob(profileId: UUID, blobId: UUID, output: OutputStream): Long

    suspend fun restore(
        vault: HealthVault,
        documentBlobs: List<RestoreDocumentBlob>,
    )

    suspend fun deleteVault()
}

open class VaultStorageException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class VaultCorruptionException(message: String, cause: Throwable? = null) :
    VaultStorageException(message, cause)

class VaultKeyUnavailableException(message: String, cause: Throwable? = null) :
    VaultStorageException(message, cause)
