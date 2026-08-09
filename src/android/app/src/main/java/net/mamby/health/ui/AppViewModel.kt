package net.mamby.health.ui

import android.app.NotificationManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.mamby.health.R
import net.mamby.health.backup.BackupOperationResult
import net.mamby.health.backup.BackupRepository
import net.mamby.health.backup.RestoreCommitResult
import net.mamby.health.backup.RestorePreparationResult
import net.mamby.health.backup.RestorePreview
import net.mamby.health.core.model.Appointment
import net.mamby.health.core.model.BuiltInDocumentCategoryPreference
import net.mamby.health.core.model.CareDirective
import net.mamby.health.core.model.CareDirectoryEntry
import net.mamby.health.core.model.CustomDocumentCategory
import net.mamby.health.core.model.CustomMeasurementType
import net.mamby.health.core.model.DocumentCategoryRef
import net.mamby.health.core.model.FamilyHistoryEntry
import net.mamby.health.core.model.HealthIdentifier
import net.mamby.health.core.model.HealthMeasurement
import net.mamby.health.core.model.HealthNote
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.MedicalDocument
import net.mamby.health.core.model.Medication
import net.mamby.health.core.model.Reminder
import net.mamby.health.core.model.Vaccination
import net.mamby.health.data.DocumentImportException
import net.mamby.health.data.DocumentImportFailure
import net.mamby.health.data.DocumentImporter
import net.mamby.health.data.MedicalDocumentDraft
import net.mamby.health.data.VaultRepository
import net.mamby.health.data.VaultState
import net.mamby.health.feature.vault.DocumentImportDraft
import net.mamby.health.feature.vault.DocumentPreviewState
import net.mamby.health.feature.vault.SecureDocumentPreviewer
import net.mamby.health.notifications.NotificationPermissionState
import net.mamby.health.notifications.NotificationPublisher
import net.mamby.health.notifications.ReminderScheduler
import net.mamby.health.notifications.ReminderSource
import net.mamby.health.notifications.ZoneIdProvider
import net.mamby.health.navigation.DeepLinkCoordinator
import net.mamby.health.security.AppLockManager
import net.mamby.health.security.AppLockState
import net.mamby.health.security.UnlockResult
import net.mamby.health.settings.AppSettings
import net.mamby.health.settings.SettingsRepository
import net.mamby.health.settings.ThemeMode

data class UiNotice(@StringRes val resourceId: Int)

@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultRepository: VaultRepository,
    private val documentImporter: DocumentImporter,
    private val documentPreviewer: SecureDocumentPreviewer,
    private val settingsRepository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val reminderScheduler: ReminderScheduler,
    private val reminderSource: ReminderSource,
    private val notificationPublisher: NotificationPublisher,
    private val appLockManager: AppLockManager,
    private val deepLinkCoordinator: DeepLinkCoordinator,
    val clock: Clock,
    private val zoneIdProvider: ZoneIdProvider,
) : ViewModel() {
    val vaultState: StateFlow<VaultState> = vaultRepository.state
    val lockState: StateFlow<AppLockState> = appLockManager.state
    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    private val mutablePreview = MutableStateFlow<DocumentPreviewState>(DocumentPreviewState.Idle)
    val preview: StateFlow<DocumentPreviewState> = mutablePreview.asStateFlow()
    private var previewJob: Job? = null

    private val mutableRestorePreview = MutableStateFlow<RestorePreview?>(null)
    val restorePreview: StateFlow<RestorePreview?> = mutableRestorePreview.asStateFlow()

    private val mutableNotice = MutableStateFlow<UiNotice?>(null)
    val notice: StateFlow<UiNotice?> = mutableNotice.asStateFlow()

    val zoneId get() = zoneIdProvider.current()

    fun clearNotice() {
        mutableNotice.value = null
    }

    fun showUnavailable() {
        mutableNotice.value = UiNotice(R.string.record_unavailable)
    }

    fun createVault(displayName: String) = launchOperation {
        vaultRepository.createVault(displayName)
        reconcileReminders()
    }

    fun addProfile(displayName: String, onCreated: (UUID) -> Unit = {}) = launchOperation {
        onCreated(vaultRepository.addProfile(displayName))
    }

    fun deleteProfile(profileId: UUID) = launchOperation {
        vaultRepository.deleteProfile(profileId)
        mutablePreview.value = DocumentPreviewState.Idle
        reconcileReminders()
    }

    fun updateProfile(profileId: UUID, profile: HealthProfile) = launchOperation {
        vaultRepository.updateProfile(profileId, profile)
    }

    fun importDocument(profileId: UUID, draft: DocumentImportDraft) = launchOperation(
        failureNotice = { error ->
            when ((error as? DocumentImportException)?.reason) {
                DocumentImportFailure.FILE_TOO_LARGE -> R.string.import_error_too_large
                DocumentImportFailure.UNSUPPORTED_MIME -> R.string.import_error_unsupported
                DocumentImportFailure.UNSUPPORTED_SIGNATURE,
                DocumentImportFailure.MIME_MISMATCH,
                -> R.string.import_error_signature
                DocumentImportFailure.SOURCE_UNAVAILABLE -> R.string.import_error_unreadable
                null -> R.string.import_error_storage
            }
        },
    ) {
        val imported = documentImporter.import(draft.uri)
        vaultRepository.importDocument(
            profileId,
            MedicalDocumentDraft(
                title = draft.title,
                category = draft.category,
                documentDate = draft.documentDate,
                source = draft.source,
                sourceEntryId = draft.sourceEntryId,
                notes = draft.notes,
                tags = draft.tags,
            ),
            imported,
        )
        mutableNotice.value = UiNotice(R.string.import_success)
    }

    fun updateDocument(profileId: UUID, document: MedicalDocument) = launchOperation {
        vaultRepository.updateDocument(profileId, document)
    }

    fun deleteDocument(profileId: UUID, id: UUID) = launchOperation {
        vaultRepository.deleteDocument(profileId, id)
        resetPreview()
        mutableNotice.value = UiNotice(R.string.document_deleted)
    }

    fun loadPreview(profileId: UUID, document: MedicalDocument, page: Int) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            mutablePreview.value = DocumentPreviewState.Loading(profileId, document.id)
            mutablePreview.value = try {
                val rendered = documentPreviewer.render(profileId, document, page)
                DocumentPreviewState.Ready(
                    profileId,
                    document.id,
                    rendered.image,
                    rendered.page,
                    rendered.pageCount,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                DocumentPreviewState.Error(
                    profileId,
                    document.id,
                    context.getString(R.string.preview_unavailable),
                )
            }
        }
    }

    fun resetPreview() {
        previewJob?.cancel()
        previewJob = null
        mutablePreview.value = DocumentPreviewState.Idle
    }

    fun upsertMedication(profileId: UUID, medication: Medication) = launchOperation {
        vaultRepository.upsertMedication(profileId, medication)
        reconcileReminders()
    }

    fun deleteMedication(profileId: UUID, id: UUID) = launchOperation {
        vaultRepository.deleteMedication(profileId, id)
        reconcileReminders()
    }

    fun upsertAppointment(profileId: UUID, appointment: Appointment) = launchOperation {
        vaultRepository.upsertAppointment(profileId, appointment)
        reconcileReminders()
    }

    fun deleteAppointment(profileId: UUID, id: UUID) = launchOperation {
        vaultRepository.deleteAppointment(profileId, id)
        reconcileReminders()
    }

    fun upsertVaccination(profileId: UUID, vaccination: Vaccination) = launchOperation {
        vaultRepository.upsertVaccination(profileId, vaccination)
    }

    fun deleteVaccination(profileId: UUID, id: UUID) = launchOperation {
        vaultRepository.deleteVaccination(profileId, id)
    }

    fun upsertReminder(profileId: UUID, reminder: Reminder) = launchOperation {
        vaultRepository.upsertReminder(profileId, reminder)
        reconcileReminders()
    }

    fun deleteReminder(profileId: UUID, id: UUID) = launchOperation {
        vaultRepository.deleteReminder(profileId, id)
        reconcileReminders()
    }

    fun upsertHealthNote(note: HealthNote) = launchOperation {
        vaultRepository.upsertHealthNote(note)
    }

    fun deleteHealthNote(id: UUID) = launchOperation {
        vaultRepository.deleteHealthNote(id)
    }

    fun upsertMeasurement(profileId: UUID, measurement: HealthMeasurement) = launchOperation {
        vaultRepository.upsertMeasurement(profileId, measurement)
    }

    fun deleteMeasurement(profileId: UUID, id: UUID) = launchOperation {
        vaultRepository.deleteMeasurement(profileId, id)
    }

    fun upsertCustomMeasurementType(profileId: UUID, type: CustomMeasurementType) = launchOperation {
        vaultRepository.upsertCustomMeasurementType(profileId, type)
    }

    fun deleteCustomMeasurementType(profileId: UUID, id: UUID) = launchOperation {
        vaultRepository.deleteCustomMeasurementType(profileId, id)
    }

    fun upsertCareDirectoryEntry(profileId: UUID, entry: CareDirectoryEntry) = launchOperation {
        vaultRepository.upsertCareDirectoryEntry(profileId, entry)
    }

    fun deleteCareDirectoryEntry(profileId: UUID, id: UUID) = launchOperation {
        vaultRepository.deleteCareDirectoryEntry(profileId, id)
    }

    fun setPrimaryDoctor(profileId: UUID, id: UUID?) = launchOperation {
        vaultRepository.setPrimaryDoctor(profileId, id)
    }

    fun upsertFamilyHistoryEntry(profileId: UUID, entry: FamilyHistoryEntry) = launchOperation {
        vaultRepository.upsertFamilyHistoryEntry(profileId, entry)
    }

    fun deleteFamilyHistoryEntry(profileId: UUID, id: UUID) = launchOperation {
        vaultRepository.deleteFamilyHistoryEntry(profileId, id)
    }

    fun upsertCareDirective(profileId: UUID, directive: CareDirective) = launchOperation {
        vaultRepository.upsertCareDirective(profileId, directive)
    }

    fun deleteCareDirective(profileId: UUID, id: UUID) = launchOperation {
        vaultRepository.deleteCareDirective(profileId, id)
    }

    fun upsertHealthIdentifier(profileId: UUID, identifier: HealthIdentifier) = launchOperation {
        vaultRepository.upsertHealthIdentifier(profileId, identifier)
    }

    fun deleteHealthIdentifier(profileId: UUID, id: UUID) = launchOperation {
        vaultRepository.deleteHealthIdentifier(profileId, id)
    }

    fun upsertCustomDocumentCategory(profileId: UUID, category: CustomDocumentCategory) =
        launchOperation {
            vaultRepository.upsertCustomDocumentCategory(profileId, category)
        }

    fun deleteCustomDocumentCategory(
        profileId: UUID,
        id: UUID,
        replacement: DocumentCategoryRef?,
    ) = launchOperation {
        vaultRepository.deleteCustomDocumentCategory(profileId, id, replacement)
    }

    fun updateBuiltInDocumentCategoryPreference(
        profileId: UUID,
        preference: BuiltInDocumentCategoryPreference,
        replacement: DocumentCategoryRef?,
    ) = launchOperation {
        vaultRepository.updateBuiltInDocumentCategoryPreference(profileId, preference, replacement)
    }

    fun setThemeMode(mode: ThemeMode) = launchOperation {
        settingsRepository.setThemeMode(mode)
    }

    fun setLocaleTag(tag: String) = launchOperation {
        settingsRepository.setLocaleTag(tag)
    }

    fun setAppLockEnabled(activity: FragmentActivity, enabled: Boolean) {
        viewModelScope.launch {
            val result = if (enabled) appLockManager.enable(activity) else {
                appLockManager.disable()
                UnlockResult.Success
            }
            mutableNotice.value = when (result) {
                UnlockResult.Success -> UiNotice(
                    if (enabled) R.string.app_lock_enabled else R.string.app_lock_disabled,
                )
                UnlockResult.Cancelled -> UiNotice(R.string.unlock_canceled)
                is UnlockResult.Failed -> UiNotice(R.string.unlock_failed)
                is UnlockResult.Unavailable -> UiNotice(R.string.unlock_unavailable)
            }
        }
    }

    fun setAppLockTimeout(timeout: Duration) = launchOperation {
        settingsRepository.setAppLockTimeout(timeout)
    }

    fun lockNow() {
        mutablePreview.value = DocumentPreviewState.Idle
        appLockManager.lock()
    }

    fun unlock(activity: FragmentActivity) {
        viewModelScope.launch {
            mutableNotice.value = when (appLockManager.unlock(activity)) {
                UnlockResult.Success -> null
                UnlockResult.Cancelled -> UiNotice(R.string.unlock_canceled)
                is UnlockResult.Failed -> UiNotice(R.string.unlock_failed)
                is UnlockResult.Unavailable -> UiNotice(R.string.unlock_unavailable)
            }
        }
    }

    fun configureBackup(destination: android.net.Uri, passphrase: CharArray, scheduled: Boolean) =
        launchOperation {
            mutableNotice.value = backupRepository.configure(destination, passphrase, scheduled)
                .toNotice()
        }

    fun backupNow() = launchOperation {
        mutableNotice.value = backupRepository.backupNow().toNotice()
    }

    fun clearBackup() = launchOperation {
        backupRepository.clearConfiguration()
    }

    fun prepareRestore(source: android.net.Uri, passphrase: CharArray) = launchOperation {
        when (val result = backupRepository.prepareRestore(source, passphrase)) {
            is RestorePreparationResult.Ready -> {
                mutableRestorePreview.value = result.preview
                mutableNotice.value = null
            }
            RestorePreparationResult.WrongPassphrase -> mutableNotice.value = UiNotice(R.string.restore_wrong_passphrase)
            RestorePreparationResult.Corrupt -> mutableNotice.value = UiNotice(R.string.restore_corrupt)
            RestorePreparationResult.UnsupportedVersion -> mutableNotice.value = UiNotice(R.string.restore_unsupported)
            RestorePreparationResult.DestinationUnavailable -> mutableNotice.value = UiNotice(R.string.restore_provider_error)
        }
    }

    fun commitRestore(preview: RestorePreview, crossFlavorConfirmed: Boolean) = launchOperation {
        when (backupRepository.commitRestore(preview.token, crossFlavorConfirmed)) {
            RestoreCommitResult.Success -> {
                mutableRestorePreview.value = null
                mutablePreview.value = DocumentPreviewState.Idle
                reconcileReminders()
                mutableNotice.value = UiNotice(R.string.restore_success)
            }
            RestoreCommitResult.NotPrepared -> mutableNotice.value = UiNotice(R.string.restore_failed_safely)
            is RestoreCommitResult.CrossFlavorConfirmationRequired ->
                mutableNotice.value = UiNotice(R.string.restore_cross_channel_warning)
            RestoreCommitResult.FailedSafely -> mutableNotice.value = UiNotice(R.string.restore_failed_safely)
        }
    }

    fun discardRestore(preview: RestorePreview) = launchOperation(reportFailure = false) {
        backupRepository.discardRestore(preview.token)
        mutableRestorePreview.value = null
    }

    fun deleteVault() = launchOperation {
        backupRepository.clearConfiguration()
        reminderScheduler.cancelAll()
        context.getSystemService(NotificationManager::class.java)?.cancelAll()
        vaultRepository.deleteVault()
        mutableRestorePreview.value = null
        mutablePreview.value = DocumentPreviewState.Idle
        mutableNotice.value = UiNotice(R.string.delete_vault_success)
    }

    fun notificationsBlocked(): Boolean =
        notificationPublisher.permissionState() == NotificationPermissionState.BLOCKED

    fun deepLinkTargets() = deepLinkCoordinator.targets

    private suspend fun reconcileReminders() {
        reminderScheduler.reconcile(reminderSource.activeReminderRequests())
    }

    private fun launchOperation(
        reportFailure: Boolean = true,
        failureNotice: (Throwable) -> Int = { R.string.error_generic },
        operation: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                operation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (reportFailure) mutableNotice.value = UiNotice(failureNotice(error))
            }
        }
    }

    private fun BackupOperationResult.toNotice(): UiNotice = when (this) {
        is BackupOperationResult.Success -> UiNotice(R.string.backup_success)
        BackupOperationResult.NotConfigured -> UiNotice(R.string.backup_not_configured)
        is BackupOperationResult.InvalidPassphrase -> UiNotice(R.string.backup_passphrase_requirement)
        is BackupOperationResult.Failure -> UiNotice(R.string.backup_failed)
    }
}
