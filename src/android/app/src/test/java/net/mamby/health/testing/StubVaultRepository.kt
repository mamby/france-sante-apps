package net.mamby.health.testing

import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.mamby.health.core.model.Appointment
import net.mamby.health.core.model.BuiltInDocumentCategoryPreference
import net.mamby.health.core.model.CareDirective
import net.mamby.health.core.model.CareDirectoryEntry
import net.mamby.health.core.model.CustomDocumentCategory
import net.mamby.health.core.model.CustomMeasurementType
import net.mamby.health.core.model.DocumentCategoryRef
import net.mamby.health.core.model.EmergencyContact
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.FamilyHistoryEntry
import net.mamby.health.core.model.HealthIdentifier
import net.mamby.health.core.model.HealthMeasurement
import net.mamby.health.core.model.HealthNote
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.core.model.Medication
import net.mamby.health.core.model.Reminder
import net.mamby.health.core.model.Vaccination
import net.mamby.health.data.ImportedDocumentData
import net.mamby.health.data.MedicalDocumentDraft
import net.mamby.health.data.RestoreDocumentBlob
import net.mamby.health.data.VaultRepository
import net.mamby.health.data.VaultState

open class StubVaultRepository : VaultRepository {
    override val state: StateFlow<VaultState> = MutableStateFlow(VaultState.Loading)
    override suspend fun initialize(): Unit = unused()
    override suspend fun createVault(firstProfileName: String): Unit = unused()
    override suspend fun addProfile(displayName: String): UUID = unused()
    override suspend fun updateProfile(profileId: UUID, profile: HealthProfile): Unit = unused()
    override suspend fun deleteProfile(profileId: UUID): Unit = unused()
    override suspend fun upsertEmergencyContact(profileId: UUID, contact: EmergencyContact): Unit = unused()
    override suspend fun deleteEmergencyContact(profileId: UUID, contactId: UUID): Unit = unused()
    override suspend fun importDocument(
        profileId: UUID,
        draft: MedicalDocumentDraft,
        imported: ImportedDocumentData,
    ): MedicalDocument = unused()
    override suspend fun updateDocument(profileId: UUID, document: MedicalDocument): Unit = unused()
    override suspend fun deleteDocument(profileId: UUID, documentId: UUID): Unit = unused()
    override suspend fun upsertMedication(profileId: UUID, medication: Medication): Unit = unused()
    override suspend fun deleteMedication(profileId: UUID, medicationId: UUID): Unit = unused()
    override suspend fun upsertAppointment(profileId: UUID, appointment: Appointment): Unit = unused()
    override suspend fun deleteAppointment(profileId: UUID, appointmentId: UUID): Unit = unused()
    override suspend fun upsertVaccination(profileId: UUID, vaccination: Vaccination): Unit = unused()
    override suspend fun deleteVaccination(profileId: UUID, vaccinationId: UUID): Unit = unused()
    override suspend fun upsertReminder(profileId: UUID, reminder: Reminder): Unit = unused()
    override suspend fun deleteReminder(profileId: UUID, reminderId: UUID): Unit = unused()
    override suspend fun upsertHealthNote(note: HealthNote): Unit = unused()
    override suspend fun deleteHealthNote(noteId: UUID): Unit = unused()
    override suspend fun upsertMeasurement(profileId: UUID, measurement: HealthMeasurement): Unit = unused()
    override suspend fun deleteMeasurement(profileId: UUID, measurementId: UUID): Unit = unused()
    override suspend fun upsertCustomMeasurementType(profileId: UUID, type: CustomMeasurementType): Unit = unused()
    override suspend fun deleteCustomMeasurementType(profileId: UUID, typeId: UUID): Unit = unused()
    override suspend fun upsertCareDirectoryEntry(profileId: UUID, entry: CareDirectoryEntry): Unit = unused()
    override suspend fun deleteCareDirectoryEntry(profileId: UUID, entryId: UUID): Unit = unused()
    override suspend fun setPrimaryDoctor(profileId: UUID, entryId: UUID?): Unit = unused()
    override suspend fun upsertFamilyHistoryEntry(profileId: UUID, entry: FamilyHistoryEntry): Unit = unused()
    override suspend fun deleteFamilyHistoryEntry(profileId: UUID, entryId: UUID): Unit = unused()
    override suspend fun upsertCareDirective(profileId: UUID, directive: CareDirective): Unit = unused()
    override suspend fun deleteCareDirective(profileId: UUID, directiveId: UUID): Unit = unused()
    override suspend fun upsertHealthIdentifier(profileId: UUID, identifier: HealthIdentifier): Unit = unused()
    override suspend fun deleteHealthIdentifier(profileId: UUID, identifierId: UUID): Unit = unused()
    override suspend fun upsertCustomDocumentCategory(profileId: UUID, category: CustomDocumentCategory): Unit = unused()
    override suspend fun deleteCustomDocumentCategory(
        profileId: UUID,
        categoryId: UUID,
        replacement: DocumentCategoryRef?,
    ): Unit = unused()
    override suspend fun updateBuiltInDocumentCategoryPreference(
        profileId: UUID,
        preference: BuiltInDocumentCategoryPreference,
        replacement: DocumentCategoryRef?,
    ): Unit = unused()
    override suspend fun exportSnapshot(): HealthVault = unused()
    override suspend fun readDocumentBlob(profileId: UUID, blobId: UUID): ByteArray? = unused()
    override suspend fun copyDocumentBlob(profileId: UUID, blobId: UUID, output: OutputStream): Long = unused()
    override suspend fun restore(vault: HealthVault, documentBlobs: List<RestoreDocumentBlob>): Unit = unused()
    override suspend fun deleteVault(): Unit = unused()

    protected fun <T> unused(): T = error("Not used by this test")
}
