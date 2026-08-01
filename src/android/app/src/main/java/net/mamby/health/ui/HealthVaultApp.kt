package net.mamby.health.ui

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import java.util.UUID
import kotlinx.coroutines.delay
import net.mamby.health.R
import net.mamby.health.core.model.HealthSearchResult
import net.mamby.health.core.model.HealthSearchTarget
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.profileRecord
import net.mamby.health.data.VaultState
import net.mamby.health.feature.appointments.AppointmentDetailScreen
import net.mamby.health.feature.appointments.AppointmentsScreen
import net.mamby.health.feature.dashboard.DashboardScreen
import net.mamby.health.feature.error.VaultLoadingScreen
import net.mamby.health.feature.error.VaultUnreadableScreen
import net.mamby.health.feature.lock.LockScreen
import net.mamby.health.feature.medications.MedicationDetailScreen
import net.mamby.health.feature.medications.MedicationsScreen
import net.mamby.health.feature.profiles.ProfileManagementScreen
import net.mamby.health.feature.profiles.ProfileNameDialog
import net.mamby.health.feature.reminders.RemindersScreen
import net.mamby.health.feature.search.SearchScreen
import net.mamby.health.feature.search.SearchFilter
import net.mamby.health.feature.settings.SettingsScreen
import net.mamby.health.feature.summary.SummaryScreen
import net.mamby.health.feature.vault.DocumentDetailScreen
import net.mamby.health.feature.vault.DocumentPreviewState
import net.mamby.health.feature.vault.VaultScreen
import net.mamby.health.navigation.AppointmentDetailRoute
import net.mamby.health.navigation.AppointmentsRoute
import net.mamby.health.navigation.DeepLinkKind
import net.mamby.health.navigation.DeepLinkTarget
import net.mamby.health.navigation.DocumentDetailRoute
import net.mamby.health.navigation.HealthInfoDetailRoute
import net.mamby.health.navigation.HealthRecordsRoute
import net.mamby.health.navigation.HomeRoute
import net.mamby.health.navigation.ManageProfilesRoute
import net.mamby.health.navigation.MedicationDetailRoute
import net.mamby.health.navigation.MedicationsRoute
import net.mamby.health.navigation.RemindersRoute
import net.mamby.health.navigation.SearchRoute
import net.mamby.health.navigation.SettingsRoute
import net.mamby.health.navigation.TopLevelDestination
import net.mamby.health.navigation.rememberAppNavigationState
import net.mamby.health.security.AppLockState
import net.mamby.health.settings.AppSettings
import net.mamby.health.settings.ThemeMode
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.AppNavigationSuite
import net.mamby.health.ui.components.FormDialog
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.theme.HealthVaultTheme
import net.mamby.health.ui.theme.UiTokens

@Composable
fun HealthVaultApp(viewModel: AppViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val vaultState by viewModel.vaultState.collectAsStateWithLifecycle()
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val restorePreview by viewModel.restorePreview.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    var startDialog by rememberSaveable { mutableStateOf(false) }
    var resetUnreadableDialog by rememberSaveable { mutableStateOf(false) }
    var recoverySettingsVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(NOTICE_DURATION_MILLIS)
            viewModel.clearNotice()
        }
    }
    LaunchedEffect(lockState) {
        if (lockState == AppLockState.Locked || lockState == AppLockState.Authenticating) {
            viewModel.resetPreview()
        }
    }
    LaunchedEffect(vaultState) {
        if (vaultState !is VaultState.Unreadable && vaultState !is VaultState.Missing) {
            recoverySettingsVisible = false
        }
    }

    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    HealthVaultTheme(darkTheme = darkTheme) {
        Surface(Modifier.fillMaxSize()) {
            when (lockState) {
                AppLockState.Initializing -> VaultLoadingScreen()
                AppLockState.Locked, AppLockState.Authenticating -> LockScreen(
                    state = lockState,
                    message = notice?.let { stringResource(it.resourceId) },
                    onUnlock = { activity?.let(viewModel::unlock) },
                )
                AppLockState.Disabled, AppLockState.Unlocked -> when (val state = vaultState) {
                    VaultState.Loading -> VaultLoadingScreen()
                    VaultState.Missing -> if (recoverySettingsVisible) {
                        RecoverySettings(
                            settings = settings,
                            viewModel = viewModel,
                            restorePreview = restorePreview,
                            notice = notice,
                            activity = activity,
                            onBack = { recoverySettingsVisible = false },
                        )
                    } else {
                        MissingVaultScreen(
                            onStart = { startDialog = true },
                            onRestore = { recoverySettingsVisible = true },
                        )
                    }
                    is VaultState.Unreadable -> if (recoverySettingsVisible) {
                        RecoverySettings(
                            settings = settings,
                            viewModel = viewModel,
                            restorePreview = restorePreview,
                            notice = notice,
                            activity = activity,
                            onBack = { recoverySettingsVisible = false },
                        )
                    } else {
                        VaultUnreadableScreen(
                            reason = state.reason,
                            onRestore = { recoverySettingsVisible = true },
                            onReset = { resetUnreadableDialog = true },
                        )
                    }
                    is VaultState.Ready -> VaultNavigation(
                        vault = state.vault,
                        selectedProfileId = state.selectedProfileId,
                        settings = settings,
                        preview = preview,
                        restorePreview = restorePreview,
                        notice = notice,
                        viewModel = viewModel,
                        activity = activity,
                    )
                }
            }
        }
    }

    if (startDialog) {
        ProfileNameDialog(
            title = stringResource(R.string.start_new_title),
            initialName = "",
            onDismiss = { startDialog = false },
            onSave = {
                startDialog = false
                viewModel.createVault(it)
            },
        )
    }
    if (resetUnreadableDialog) {
        ResetUnreadableDialog(
            onDismiss = { resetUnreadableDialog = false },
            onConfirm = {
                resetUnreadableDialog = false
                viewModel.deleteVault()
            },
        )
    }
}

@Composable
private fun RecoverySettings(
    settings: AppSettings,
    viewModel: AppViewModel,
    restorePreview: net.mamby.health.backup.RestorePreview?,
    notice: UiNotice?,
    activity: FragmentActivity?,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    SettingsScreen(
        settings = settings,
        zoneId = viewModel.zoneId,
        restorePreview = restorePreview,
        message = notice?.let { stringResource(it.resourceId) },
        onBack = onBack,
        onThemeChanged = viewModel::setThemeMode,
        onLocaleChanged = viewModel::setLocaleTag,
        onAppLockChanged = { enabled -> activity?.let { viewModel.setAppLockEnabled(it, enabled) } },
        onAppLockTimeoutChanged = viewModel::setAppLockTimeout,
        onLockNow = viewModel::lockNow,
        onConfigureBackup = viewModel::configureBackup,
        onBackupNow = viewModel::backupNow,
        onClearBackup = viewModel::clearBackup,
        onPrepareRestore = viewModel::prepareRestore,
        onCommitRestore = viewModel::commitRestore,
        onDiscardRestore = viewModel::discardRestore,
        onDeleteVault = viewModel::deleteVault,
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun VaultNavigation(
    vault: HealthVault,
    selectedProfileId: UUID,
    settings: AppSettings,
    preview: DocumentPreviewState,
    restorePreview: net.mamby.health.backup.RestorePreview?,
    notice: UiNotice?,
    viewModel: AppViewModel,
    activity: FragmentActivity?,
) {
    val navigation = rememberAppNavigationState()
    val record = vault.profileRecord(selectedProfileId)
    val currentVault by rememberUpdatedState(vault)
    val currentSelectedProfileId by rememberUpdatedState(selectedProfileId)
    val context = LocalContext.current
    var profileSheetVisible by remember { mutableStateOf(false) }
    var addProfileVisible by remember { mutableStateOf(false) }
    var healthTab by remember(selectedProfileId) { mutableStateOf(0) }
    var searchQuery by remember(selectedProfileId) { mutableStateOf("") }
    var searchFilter by remember(selectedProfileId) { mutableStateOf(SearchFilter.ALL) }
    var healthCreation by remember(selectedProfileId) { mutableLongStateOf(0) }
    var documentCreation by remember(selectedProfileId) { mutableLongStateOf(0) }
    var medicationCreation by remember(selectedProfileId) { mutableLongStateOf(0) }
    var appointmentCreation by remember(selectedProfileId) { mutableLongStateOf(0) }
    var previousProfileId by remember { mutableStateOf(selectedProfileId) }
    var pendingDeepLink by remember { mutableStateOf<DeepLinkTarget?>(null) }
    var notificationsBlocked by remember { mutableStateOf(viewModel.notificationsBlocked()) }
    var pendingNotificationAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingNotificationAction?.invoke()
        pendingNotificationAction = null
        notificationsBlocked = viewModel.notificationsBlocked()
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationsBlocked = viewModel.notificationsBlocked()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(selectedProfileId) {
        if (previousProfileId != selectedProfileId) {
            navigation.trimToRoots(keepDestination = true)
            profileSheetVisible = false
            healthCreation = 0
            documentCreation = 0
            medicationCreation = 0
            appointmentCreation = 0
            searchQuery = ""
            searchFilter = SearchFilter.ALL
            viewModel.resetPreview()
            previousProfileId = selectedProfileId
        }
    }
    LaunchedEffect(selectedProfileId, pendingDeepLink) {
        val target = pendingDeepLink?.takeIf { it.profileId == selectedProfileId.toString() }
            ?: return@LaunchedEffect
        pendingDeepLink = null
        when (target.kind) {
            DeepLinkKind.Dashboard -> navigation.select(TopLevelDestination.Home)
            DeepLinkKind.Medication -> navigation.navigate(
                TopLevelDestination.Medications,
                target.recordId?.let { MedicationDetailRoute(selectedProfileId.toString(), it) },
            )
            DeepLinkKind.Appointment -> navigation.navigate(
                TopLevelDestination.Appointments,
                target.recordId?.let { AppointmentDetailRoute(selectedProfileId.toString(), it) },
            )
            DeepLinkKind.Reminder -> navigation.navigate(TopLevelDestination.Home, RemindersRoute)
        }
    }

    fun selectProfile(profileId: UUID) {
        profileSheetVisible = false
        navigation.trimToRoots(keepDestination = true)
        viewModel.resetPreview()
        viewModel.selectProfile(profileId)
    }

    fun withNotificationPermission(enabled: Boolean, action: () -> Unit) {
        val needsRequest = enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsRequest) {
            pendingNotificationAction = action
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else action()
    }

    LaunchedEffect(Unit) {
        viewModel.deepLinkTargets().collect { target ->
            val ownerId = runCatching { UUID.fromString(target.profileId) }.getOrNull()
            if (ownerId == null || currentVault.profiles.none { it.profile.id == ownerId }) {
                navigation.resetTo()
                viewModel.showUnavailable()
                return@collect
            }
            pendingDeepLink = target
            if (ownerId != currentSelectedProfileId) viewModel.selectProfile(ownerId)
        }
    }

    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val directive = remember(adaptiveInfo) {
        calculatePaneScaffoldDirective(adaptiveInfo).copy(horizontalPartitionSpacerSize = 0.dp)
    }
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)
    val now = viewModel.clock.instant()
    val zoneId = viewModel.zoneId
    val today = now.atZone(zoneId).toLocalDate()
    val navigateSettings = dropUnlessResumed { navigation.navigate(SettingsRoute) }
    val openProfileSelector = { profileSheetVisible = true }
    val message = notice?.let { stringResource(it.resourceId) }

    Box(Modifier.fillMaxSize()) {
        BackHandler(enabled = navigation.isAtSecondaryRoot, onBack = navigation::goBack)
        AppNavigationSuite(
            selectedDestination = navigation.selectedDestination,
            onDestinationSelected = navigation::select,
        ) {
            NavDisplay(
                backStack = navigation.currentBackStack,
                onBack = navigation::goBack,
                sceneStrategies = listOf(listDetailStrategy),
                entryProvider = entryProvider {
                    entry<HomeRoute> {
                        DashboardScreen(
                            record = record,
                            clock = viewModel.clock,
                            zoneId = zoneId,
                            onProfileClick = openProfileSelector,
                            onSettings = navigateSettings,
                            onReminders = { navigation.navigate(RemindersRoute) },
                            onDocumentSelected = { id ->
                                healthTab = 1
                                navigation.navigate(
                                    TopLevelDestination.HealthRecords,
                                    DocumentDetailRoute(selectedProfileId.toString(), id),
                                )
                            },
                            onAddHealthInfo = {
                                healthTab = 0
                                healthCreation++
                                navigation.select(TopLevelDestination.HealthRecords)
                            },
                            onImportDocument = {
                                healthTab = 1
                                documentCreation++
                                navigation.select(TopLevelDestination.HealthRecords)
                            },
                            onAddMedication = {
                                medicationCreation++
                                navigation.select(TopLevelDestination.Medications)
                            },
                            onAddAppointment = {
                                appointmentCreation++
                                navigation.select(TopLevelDestination.Appointments)
                            },
                        )
                    }
                    entry<HealthRecordsRoute>(
                        metadata = if (healthTab == 1) {
                            ListDetailSceneStrategy.listPane(
                                detailPlaceholder = { DetailPlaceholder(R.string.no_documents_body) },
                            )
                        } else emptyMap(),
                    ) {
                        if (healthTab == 0) {
                            SummaryScreen(
                                profile = record.profile,
                                vaccinations = record.vaccinations,
                                today = today,
                                onProfileClick = openProfileSelector,
                                onSettings = navigateSettings,
                                onUpdateProfile = { viewModel.updateProfile(selectedProfileId, it) },
                                onUpsertVaccination = { viewModel.upsertVaccination(selectedProfileId, it) },
                                onDeleteVaccination = { viewModel.deleteVaccination(selectedProfileId, it) },
                                creationRequest = healthCreation,
                                selectedTab = healthTab,
                                onTabSelected = { healthTab = it },
                            )
                        } else {
                            VaultScreen(
                                documents = record.documents,
                                profile = record.profile,
                                today = today,
                                onProfileClick = openProfileSelector,
                                onSettings = navigateSettings,
                                onImport = { viewModel.importDocument(selectedProfileId, it) },
                                onDocumentSelected = { id ->
                                    navigation.navigate(DocumentDetailRoute(selectedProfileId.toString(), id))
                                    viewModel.resetPreview()
                                },
                                creationRequest = documentCreation,
                                selectedTab = healthTab,
                                onTabSelected = { healthTab = it },
                            )
                        }
                    }
                    entry<SearchRoute>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { DetailPlaceholder(R.string.search_initial_body) },
                        ),
                    ) {
                        SearchScreen(
                            record = record,
                            onProfileClick = openProfileSelector,
                            onSettings = navigateSettings,
                            onResultSelected = { result ->
                                navigation.navigate(result.toRoute(selectedProfileId))
                                viewModel.resetPreview()
                            },
                            query = searchQuery,
                            filter = searchFilter,
                            onQueryChanged = { searchQuery = it },
                            onFilterChanged = { searchFilter = it },
                        )
                    }
                    entry<MedicationsRoute>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { DetailPlaceholder(R.string.no_medications_body) },
                        ),
                    ) {
                        MedicationsScreen(
                            medications = record.medications,
                            profile = record.profile,
                            today = today,
                            onProfileClick = openProfileSelector,
                            onSettings = navigateSettings,
                            onUpsert = { medication ->
                                withNotificationPermission(medication.remindersEnabled) {
                                    viewModel.upsertMedication(selectedProfileId, medication)
                                }
                            },
                            onSelected = {
                                navigation.navigate(MedicationDetailRoute(selectedProfileId.toString(), it))
                            },
                            creationRequest = medicationCreation,
                        )
                    }
                    entry<AppointmentsRoute>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { DetailPlaceholder(R.string.no_appointments_body) },
                        ),
                    ) {
                        AppointmentsScreen(
                            appointments = record.appointments,
                            documents = record.documents,
                            profile = record.profile,
                            zoneId = zoneId,
                            now = now,
                            onProfileClick = openProfileSelector,
                            onSettings = navigateSettings,
                            onUpsert = { appointment ->
                                withNotificationPermission(appointment.reminderLeadMinutes != null) {
                                    viewModel.upsertAppointment(selectedProfileId, appointment)
                                }
                            },
                            onSelected = {
                                navigation.navigate(AppointmentDetailRoute(selectedProfileId.toString(), it))
                            },
                            creationRequest = appointmentCreation,
                        )
                    }
                    entry<DocumentDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val document = if (route.profileId == selectedProfileId.toString()) {
                            record.documents.firstOrNull { it.id.toString() == route.id }
                        } else null
                        if (document == null) MissingRecordScreen(navigation::goBack) else DocumentDetailScreen(
                            document = document,
                            profile = record.profile,
                            preview = preview,
                            onBack = navigation::goBack,
                            onLoadPreview = { viewModel.loadPreview(selectedProfileId, document, it) },
                            onEdit = { viewModel.updateDocument(selectedProfileId, it) },
                            onDelete = {
                                viewModel.deleteDocument(selectedProfileId, document.id)
                                navigation.goBack()
                            },
                            onProfileClick = openProfileSelector,
                        )
                    }
                    entry<MedicationDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val medication = if (route.profileId == selectedProfileId.toString()) {
                            record.medications.firstOrNull { it.id.toString() == route.id }
                        } else null
                        if (medication == null) MissingRecordScreen(navigation::goBack) else MedicationDetailScreen(
                            medication = medication,
                            profile = record.profile,
                            today = today,
                            onBack = navigation::goBack,
                            onUpsert = { updated ->
                                withNotificationPermission(updated.remindersEnabled) {
                                    viewModel.upsertMedication(selectedProfileId, updated)
                                }
                            },
                            onDelete = {
                                viewModel.deleteMedication(selectedProfileId, medication.id)
                                navigation.goBack()
                            },
                            onProfileClick = openProfileSelector,
                        )
                    }
                    entry<AppointmentDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val appointment = if (route.profileId == selectedProfileId.toString()) {
                            record.appointments.firstOrNull { it.id.toString() == route.id }
                        } else null
                        if (appointment == null) MissingRecordScreen(navigation::goBack) else AppointmentDetailScreen(
                            appointment = appointment,
                            profile = record.profile,
                            documents = record.documents,
                            zoneId = zoneId,
                            today = today,
                            onBack = navigation::goBack,
                            onUpsert = { updated ->
                                withNotificationPermission(updated.reminderLeadMinutes != null) {
                                    viewModel.upsertAppointment(selectedProfileId, updated)
                                }
                            },
                            onDelete = {
                                viewModel.deleteAppointment(selectedProfileId, appointment.id)
                                navigation.goBack()
                            },
                            onDocumentSelected = { id ->
                                healthTab = 1
                                navigation.navigate(
                                    TopLevelDestination.HealthRecords,
                                    DocumentDetailRoute(selectedProfileId.toString(), id),
                                )
                            },
                            onProfileClick = openProfileSelector,
                        )
                    }
                    entry<HealthInfoDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        if (route.profileId != selectedProfileId.toString()) {
                            MissingRecordScreen(navigation::goBack)
                        } else {
                            HealthInfoDetailScreen(
                                record = record,
                                route = route,
                                onBack = navigation::goBack,
                                onProfileClick = openProfileSelector,
                                onOpenRecords = {
                                    healthTab = 0
                                    navigation.resetTo(TopLevelDestination.HealthRecords)
                                },
                            )
                        }
                    }
                    entry<RemindersRoute> {
                        RemindersScreen(
                            reminders = record.reminders,
                            profile = record.profile,
                            today = today,
                            notificationsBlocked = notificationsBlocked,
                            now = now,
                            zoneId = zoneId,
                            onBack = navigation::goBack,
                            onProfileClick = openProfileSelector,
                            onUpsert = { reminder ->
                                withNotificationPermission(reminder.isEnabled) {
                                    viewModel.upsertReminder(selectedProfileId, reminder)
                                }
                            },
                            onDelete = { viewModel.deleteReminder(selectedProfileId, it) },
                            onOpenNotificationSettings = context::openNotificationSettings,
                        )
                    }
                    entry<SettingsRoute> {
                        SettingsScreen(
                            settings = settings,
                            zoneId = zoneId,
                            restorePreview = restorePreview,
                            message = null,
                            onBack = navigation::goBack,
                            onThemeChanged = viewModel::setThemeMode,
                            onLocaleChanged = viewModel::setLocaleTag,
                            onAppLockChanged = { enabled ->
                                activity?.let { viewModel.setAppLockEnabled(it, enabled) }
                            },
                            onAppLockTimeoutChanged = viewModel::setAppLockTimeout,
                            onLockNow = viewModel::lockNow,
                            onConfigureBackup = viewModel::configureBackup,
                            onBackupNow = viewModel::backupNow,
                            onClearBackup = viewModel::clearBackup,
                            onPrepareRestore = viewModel::prepareRestore,
                            onCommitRestore = { restore, confirmed ->
                                searchQuery = ""
                                searchFilter = SearchFilter.ALL
                                viewModel.commitRestore(restore, confirmed)
                                navigation.resetTo()
                            },
                            onDiscardRestore = viewModel::discardRestore,
                            onDeleteVault = {
                                viewModel.deleteVault()
                                navigation.resetTo()
                            },
                            onManageProfiles = { navigation.navigate(ManageProfilesRoute) },
                        )
                    }
                    entry<ManageProfilesRoute> {
                        ProfileManagementScreen(
                            profiles = vault.profiles,
                            onBack = navigation::goBack,
                            onAdd = {
                                viewModel.addProfile(it)
                                navigation.resetTo()
                            },
                            onRename = viewModel::updateProfile,
                            onDelete = {
                                viewModel.deleteProfile(it)
                                navigation.resetTo()
                            },
                        )
                    }
                },
            )
        }
        if (message != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(UiTokens.ScreenPadding),
            ) { Text(message) }
        }
    }

    if (profileSheetVisible) {
        ModalBottomSheet(onDismissRequest = { profileSheetVisible = false }) {
            Text(
                stringResource(R.string.profiles_title),
                modifier = Modifier.padding(UiTokens.ScreenPadding),
                style = MaterialTheme.typography.titleLarge,
            )
            vault.profiles.forEach { candidate ->
                ListItem(
                    headlineContent = { Text(candidate.profile.displayName) },
                    trailingContent = {
                        if (candidate.profile.id == selectedProfileId) {
                            Icon(Icons.Outlined.Check, stringResource(R.string.profile_selected))
                        }
                    },
                    modifier = Modifier.clickable { selectProfile(candidate.profile.id) },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.add_profile)) },
                leadingContent = { Icon(Icons.Outlined.Add, contentDescription = null) },
                modifier = Modifier.clickable {
                    profileSheetVisible = false
                    addProfileVisible = true
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.manage_profiles)) },
                leadingContent = { Icon(Icons.Outlined.ManageAccounts, contentDescription = null) },
                modifier = Modifier.clickable {
                    profileSheetVisible = false
                    navigation.navigate(ManageProfilesRoute)
                },
            )
        }
    }
    if (addProfileVisible) {
        ProfileNameDialog(
            title = stringResource(R.string.add_profile),
            initialName = "",
            onDismiss = { addProfileVisible = false },
            onSave = {
                addProfileVisible = false
                viewModel.addProfile(it)
                navigation.resetTo()
            },
        )
    }
}

private fun HealthSearchResult.toRoute(profileId: UUID) = when (val selected = target) {
    is HealthSearchTarget.Document -> DocumentDetailRoute(profileId.toString(), selected.id.toString())
    is HealthSearchTarget.Medication -> MedicationDetailRoute(profileId.toString(), selected.id.toString())
    is HealthSearchTarget.Appointment -> AppointmentDetailRoute(profileId.toString(), selected.id.toString())
    is HealthSearchTarget.EmergencyContact -> HealthInfoDetailRoute(
        profileId.toString(),
        "contact",
        selected.id.toString(),
    )
    is HealthSearchTarget.Vaccination -> HealthInfoDetailRoute(
        profileId.toString(),
        "vaccination",
        selected.id.toString(),
    )
    is HealthSearchTarget.HealthInfo -> HealthInfoDetailRoute(
        profileId.toString(),
        "health",
    )
}

@Composable
private fun HealthInfoDetailScreen(
    record: ProfileRecord,
    route: HealthInfoDetailRoute,
    onBack: () -> Unit,
    onProfileClick: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    val content = when (route.targetKind) {
        "contact" -> record.profile.emergencyContacts
            .firstOrNull { it.id.toString() == route.id }
            ?.let { it.name to listOf(it.relationship, it.phoneNumber, it.notes.orEmpty()).filter(String::isNotBlank) }
        "vaccination" -> record.vaccinations
            .firstOrNull { it.id.toString() == route.id }
            ?.let { it.name to listOf(it.dateAdministered.toString(), it.provider.orEmpty()).filter(String::isNotBlank) }
        else -> stringResource(R.string.health_info_tab) to listOf(
            record.profile.bloodType.orEmpty(),
            record.profile.allergies.joinToString(),
            record.profile.chronicConditions.joinToString(),
            record.profile.surgeries.joinToString(),
        ).filter(String::isNotBlank)
    }
    if (content == null) {
        MissingRecordScreen(onBack)
        return
    }
    AppScreenScaffold(
        title = content.first,
        onBack = onBack,
        profile = record.profile,
        onProfileClick = onProfileClick,
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(UiTokens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            SectionCard(content.first) { content.second.forEach { Text(it) } }
            Button(onClick = onOpenRecords) { Text(stringResource(R.string.open_in_health_records)) }
        }
    }
}

@Composable
internal fun MissingVaultScreen(onStart: () -> Unit, onRestore: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(UiTokens.ScreenPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = UiTokens.FormMaxWidth)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            Text(
                text = stringResource(R.string.no_vault_title),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.no_vault_body),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Button(onClick = onStart) { Text(stringResource(R.string.start_new)) }
            TextButton(onClick = onRestore) { Text(stringResource(R.string.restore_backup)) }
        }
    }
}

@Composable
private fun DetailPlaceholder(messageResource: Int) {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Text(stringResource(messageResource), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun MissingRecordScreen(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            Text(stringResource(R.string.record_unavailable))
            Button(onClick = onBack) { Text(stringResource(R.string.action_back)) }
        }
    }
}

@Composable
private fun ResetUnreadableDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val required = stringResource(R.string.delete_vault_confirmation_word)
    var confirmation by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vault_reset_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
                Text(stringResource(R.string.reset_unreadable_vault_message))
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = { Text(stringResource(R.string.delete_vault_confirmation_label, required)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = confirmation == required) {
                Text(stringResource(R.string.vault_reset_action))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

private fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

private fun Context.openNotificationSettings() {
    startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
    )
}

private const val NOTICE_DURATION_MILLIS = 4_000L
