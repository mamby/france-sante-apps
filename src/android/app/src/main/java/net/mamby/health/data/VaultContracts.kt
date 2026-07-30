package net.mamby.health.data

import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import net.mamby.health.core.model.Appointment
import net.mamby.health.core.model.DocumentCategory
import net.mamby.health.core.model.EmergencyContact
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.core.model.Medication
import net.mamby.health.core.model.Reminder
import net.mamby.health.core.model.Vaccination

sealed interface VaultState {
    data object Loading : VaultState

    data class Demo(val vault: HealthVault) : VaultState

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

fun interface DemoVaultProvider {
    fun create(now: Instant): HealthVault
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
    val category: DocumentCategory,
    val documentDate: LocalDate,
    val source: String,
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

    suspend fun createEmpty(displayName: String = "")

    suspend fun updateProfile(profile: HealthProfile)

    suspend fun upsertEmergencyContact(contact: EmergencyContact)

    suspend fun deleteEmergencyContact(contactId: UUID)

    suspend fun importDocument(
        draft: MedicalDocumentDraft,
        imported: ImportedDocumentData,
    ): MedicalDocument

    suspend fun updateDocument(document: MedicalDocument)

    suspend fun deleteDocument(documentId: UUID)

    suspend fun upsertMedication(medication: Medication)

    suspend fun deleteMedication(medicationId: UUID)

    suspend fun upsertAppointment(appointment: Appointment)

    suspend fun deleteAppointment(appointmentId: UUID)

    suspend fun upsertVaccination(vaccination: Vaccination)

    suspend fun deleteVaccination(vaccinationId: UUID)

    suspend fun upsertReminder(reminder: Reminder)

    suspend fun deleteReminder(reminderId: UUID)

    fun searchDocuments(
        query: String = "",
        category: DocumentCategory = DocumentCategory.ALL,
    ): Flow<List<MedicalDocument>>

    suspend fun exportSnapshot(): HealthVault

    suspend fun readDocumentBlob(blobId: UUID): ByteArray?

    suspend fun copyDocumentBlob(blobId: UUID, output: OutputStream): Long

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
