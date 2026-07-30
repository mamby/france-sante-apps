package net.mamby.health.ui

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import java.time.Instant
import kotlinx.coroutines.delay
import net.mamby.health.R
import net.mamby.health.core.model.Appointment
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.Medication
import net.mamby.health.core.model.Reminder
import net.mamby.health.data.VaultState
import net.mamby.health.feature.appointments.AppointmentDetailScreen
import net.mamby.health.feature.appointments.AppointmentsScreen
import net.mamby.health.feature.dashboard.DashboardScreen
import net.mamby.health.feature.error.VaultLoadingScreen
import net.mamby.health.feature.error.VaultUnreadableScreen
import net.mamby.health.feature.lock.LockScreen
import net.mamby.health.feature.medications.MedicationDetailScreen
import net.mamby.health.feature.medications.MedicationsScreen
import net.mamby.health.feature.reminders.RemindersScreen
import net.mamby.health.feature.settings.SettingsScreen
import net.mamby.health.feature.summary.SummaryScreen
import net.mamby.health.feature.vault.DocumentDetailScreen
import net.mamby.health.feature.vault.VaultScreen
import net.mamby.health.navigation.AppointmentDetailRoute
import net.mamby.health.navigation.AppointmentsRoute
import net.mamby.health.navigation.DashboardRoute
import net.mamby.health.navigation.DeepLinkCoordinator
import net.mamby.health.navigation.DeepLinkKind
import net.mamby.health.navigation.DocumentDetailRoute
import net.mamby.health.navigation.MedicationDetailRoute
import net.mamby.health.navigation.MedicationsRoute
import net.mamby.health.navigation.RemindersRoute
import net.mamby.health.navigation.SettingsRoute
import net.mamby.health.navigation.SummaryRoute
import net.mamby.health.navigation.TopLevelDestination
import net.mamby.health.navigation.VaultRoute
import net.mamby.health.navigation.rememberAppNavigationState
import net.mamby.health.security.AppLockState
import net.mamby.health.settings.ThemeMode
import net.mamby.health.ui.components.FormDialog
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
    var startVaultDialog by remember { mutableStateOf(false) }
    var resetUnreadableDialog by remember { mutableStateOf(false) }
    var recoverySettingsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(settings.localeTag) {
        val requested = LocaleListCompat.forLanguageTags(settings.localeTag)
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != requested.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(requested)
        }
        viewModel.refreshDemo()
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(NOTICE_DURATION_MILLIS)
            viewModel.clearNotice()
        }
    }
    LaunchedEffect(vaultState) {
        if (vaultState !is VaultState.Unreadable) recoverySettingsVisible = false
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
                AppLockState.Locked,
                AppLockState.Authenticating,
                -> LockScreen(
                    state = lockState,
                    message = notice?.let { stringResource(it.resourceId) },
                    onUnlock = { activity?.let(viewModel::unlock) },
                )
                AppLockState.Disabled,
                AppLockState.Unlocked,
                -> when (val state = vaultState) {
                    VaultState.Loading -> VaultLoadingScreen()
                    is VaultState.Unreadable -> {
                        if (recoverySettingsVisible) {
                            SettingsScreen(
                                settings = settings,
                                zoneId = viewModel.zoneId,
                                restorePreview = restorePreview,
                                message = notice?.let { stringResource(it.resourceId) },
                                onBack = { recoverySettingsVisible = false },
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
                                onCommitRestore = viewModel::commitRestore,
                                onDiscardRestore = viewModel::discardRestore,
                                onDeleteVault = viewModel::deleteVault,
                            )
                        } else {
                            VaultUnreadableScreen(
                                reason = state.reason,
                                onRestore = { recoverySettingsVisible = true },
                                onReset = { resetUnreadableDialog = true },
                            )
                        }
                    }
                    is VaultState.Demo -> VaultNavigation(
                        vault = state.vault,
                        isDemo = true,
                        settings = settings,
                        preview = preview,
                        restorePreview = restorePreview,
                        notice = notice,
                        viewModel = viewModel,
                        onStartVault = { startVaultDialog = true },
                        activity = activity,
                    )
                    is VaultState.Ready -> VaultNavigation(
                        vault = state.vault,
                        isDemo = false,
                        settings = settings,
                        preview = preview,
                        restorePreview = restorePreview,
                        notice = notice,
                        viewModel = viewModel,
                        onStartVault = { startVaultDialog = true },
                        activity = activity,
                    )
                }
            }
        }
    }

    if (startVaultDialog) {
        StartVaultDialog(
            onDismiss = { startVaultDialog = false },
            onCreate = { name ->
                startVaultDialog = false
                viewModel.createVault(name)
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

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun VaultNavigation(
    vault: HealthVault,
    isDemo: Boolean,
    settings: net.mamby.health.settings.AppSettings,
    preview: net.mamby.health.feature.vault.DocumentPreviewState,
    restorePreview: net.mamby.health.backup.RestorePreview?,
    notice: UiNotice?,
    viewModel: AppViewModel,
    onStartVault: () -> Unit,
    activity: FragmentActivity?,
) {
    val navigation = rememberAppNavigationState()
    val context = LocalContext.current
    var notificationsBlocked by remember { mutableStateOf(viewModel.notificationsBlocked()) }
    var pendingNotificationAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        pendingNotificationAction?.invoke()
        pendingNotificationAction = null
        notificationsBlocked = viewModel.notificationsBlocked()
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsBlocked = viewModel.notificationsBlocked()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun withNotificationPermission(enabled: Boolean, action: () -> Unit) {
        val needsRequest = enabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsRequest) {
            pendingNotificationAction = action
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.deepLinkTargets().collect { target ->
            when (target.kind) {
                DeepLinkKind.Dashboard -> navigation.select(TopLevelDestination.Dashboard)
                DeepLinkKind.Medication -> navigation.navigate(
                    TopLevelDestination.Medications,
                    target.recordId?.let(::MedicationDetailRoute),
                )
                DeepLinkKind.Appointment -> navigation.navigate(
                    TopLevelDestination.Appointments,
                    target.recordId?.let(::AppointmentDetailRoute),
                )
                DeepLinkKind.Reminder -> navigation.navigate(
                    TopLevelDestination.Dashboard,
                    RemindersRoute,
                )
            }
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
    val navigateReminders = dropUnlessResumed { navigation.navigate(RemindersRoute) }
    val message = notice?.let { stringResource(it.resourceId) }

    Box(Modifier.fillMaxSize()) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                TopLevelDestination.entries.forEach { destination ->
                    item(
                        selected = navigation.selectedDestination == destination,
                        onClick = { navigation.select(destination) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            },
        ) {
            NavDisplay(
                backStack = navigation.currentBackStack,
                onBack = navigation::goBack,
                sceneStrategies = listOf(listDetailStrategy),
                entryProvider = entryProvider {
                    entry<DashboardRoute> {
                        DashboardScreen(
                            vault = vault,
                            isDemo = isDemo,
                            clock = viewModel.clock,
                            zoneId = zoneId,
                            onStartVault = onStartVault,
                            onSettings = navigateSettings,
                            onReminders = navigateReminders,
                            onDocumentSelected = { id ->
                                navigation.navigate(TopLevelDestination.Vault, DocumentDetailRoute(id))
                                viewModel.resetPreview()
                            },
                        )
                    }
                    entry<VaultRoute>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { DetailPlaceholder(R.string.no_documents_body) },
                        ),
                    ) {
                        VaultScreen(
                            documents = vault.documents,
                            isDemo = isDemo,
                            today = today,
                            onStartVault = onStartVault,
                            onSettings = navigateSettings,
                            onImport = viewModel::importDocument,
                            onDocumentSelected = { id ->
                                navigation.navigate(DocumentDetailRoute(id))
                                viewModel.resetPreview()
                            },
                        )
                    }
                    entry<DocumentDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val document = vault.documents.firstOrNull { it.id.toString() == route.id }
                        if (document == null) {
                            MissingRecordScreen(navigation::goBack)
                        } else {
                            DocumentDetailScreen(
                                document = document,
                                preview = preview,
                                onBack = navigation::goBack,
                                onLoadPreview = { page -> viewModel.loadPreview(document, page) },
                                onEdit = { if (isDemo) onStartVault() else viewModel.updateDocument(it) },
                                onDelete = {
                                    if (isDemo) onStartVault() else {
                                        viewModel.deleteDocument(document.id)
                                        navigation.goBack()
                                    }
                                },
                            )
                        }
                    }
                    entry<SummaryRoute> {
                        SummaryScreen(
                            profile = vault.profile,
                            vaccinations = vault.vaccinations,
                            isDemo = isDemo,
                            today = today,
                            onStartVault = onStartVault,
                            onSettings = navigateSettings,
                            onUpdateProfile = { if (isDemo) onStartVault() else viewModel.updateProfile(it) },
                            onUpsertVaccination = { if (isDemo) onStartVault() else viewModel.upsertVaccination(it) },
                            onDeleteVaccination = { if (isDemo) onStartVault() else viewModel.deleteVaccination(it) },
                        )
                    }
                    entry<MedicationsRoute>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { DetailPlaceholder(R.string.no_medications_body) },
                        ),
                    ) {
                        MedicationsScreen(
                            medications = vault.medications,
                            isDemo = isDemo,
                            today = today,
                            onStartVault = onStartVault,
                            onSettings = navigateSettings,
                            onUpsert = { medication ->
                                if (isDemo) onStartVault()
                                else withNotificationPermission(medication.remindersEnabled) {
                                    viewModel.upsertMedication(medication)
                                }
                            },
                            onSelected = { navigation.navigate(MedicationDetailRoute(it)) },
                        )
                    }
                    entry<MedicationDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val medication = vault.medications.firstOrNull { it.id.toString() == route.id }
                        if (medication == null) {
                            MissingRecordScreen(navigation::goBack)
                        } else {
                            MedicationDetailScreen(
                                medication = medication,
                                today = today,
                                onBack = navigation::goBack,
                                onUpsert = { updated ->
                                    if (isDemo) onStartVault()
                                    else withNotificationPermission(updated.remindersEnabled) {
                                        viewModel.upsertMedication(updated)
                                    }
                                },
                                onDelete = {
                                    if (isDemo) onStartVault() else {
                                        viewModel.deleteMedication(medication.id)
                                        navigation.goBack()
                                    }
                                },
                            )
                        }
                    }
                    entry<AppointmentsRoute>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { DetailPlaceholder(R.string.no_appointments_body) },
                        ),
                    ) {
                        AppointmentsScreen(
                            appointments = vault.appointments,
                            documents = vault.documents,
                            isDemo = isDemo,
                            zoneId = zoneId,
                            now = now,
                            onStartVault = onStartVault,
                            onSettings = navigateSettings,
                            onUpsert = { appointment ->
                                if (isDemo) onStartVault()
                                else withNotificationPermission(appointment.reminderLeadMinutes != null) {
                                    viewModel.upsertAppointment(appointment)
                                }
                            },
                            onSelected = { navigation.navigate(AppointmentDetailRoute(it)) },
                        )
                    }
                    entry<AppointmentDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val appointment = vault.appointments.firstOrNull { it.id.toString() == route.id }
                        if (appointment == null) {
                            MissingRecordScreen(navigation::goBack)
                        } else {
                            AppointmentDetailScreen(
                                appointment = appointment,
                                documents = vault.documents,
                                zoneId = zoneId,
                                today = today,
                                onBack = navigation::goBack,
                                onUpsert = { updated ->
                                    if (isDemo) onStartVault()
                                    else withNotificationPermission(updated.reminderLeadMinutes != null) {
                                        viewModel.upsertAppointment(updated)
                                    }
                                },
                                onDelete = {
                                    if (isDemo) onStartVault() else {
                                        viewModel.deleteAppointment(appointment.id)
                                        navigation.goBack()
                                    }
                                },
                                onDocumentSelected = { id ->
                                    navigation.navigate(TopLevelDestination.Vault, DocumentDetailRoute(id))
                                    viewModel.resetPreview()
                                },
                            )
                        }
                    }
                    entry<RemindersRoute> {
                        RemindersScreen(
                            reminders = vault.reminders,
                            isDemo = isDemo,
                            today = today,
                            notificationsBlocked = notificationsBlocked,
                            now = now,
                            zoneId = zoneId,
                            onBack = navigation::goBack,
                            onStartVault = onStartVault,
                            onUpsert = { reminder ->
                                if (isDemo) onStartVault()
                                else withNotificationPermission(reminder.isEnabled) {
                                    viewModel.upsertReminder(reminder)
                                }
                            },
                            onDelete = { id -> if (isDemo) onStartVault() else viewModel.deleteReminder(id) },
                            onOpenNotificationSettings = { context.openNotificationSettings() },
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
                            onCommitRestore = { restore, crossFlavorConfirmed ->
                                viewModel.commitRestore(restore, crossFlavorConfirmed)
                                navigation.resetTo()
                            },
                            onDiscardRestore = viewModel::discardRestore,
                            onDeleteVault = {
                                viewModel.deleteVault()
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
            Text(stringResource(R.string.error_generic))
            Button(onClick = onBack) { Text(stringResource(R.string.action_back)) }
        }
    }
}

@Composable
private fun StartVaultDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var displayName by remember { mutableStateOf("") }
    FormDialog(
        title = stringResource(R.string.start_vault_title),
        saveEnabled = displayName.isNotBlank(),
        onDismiss = onDismiss,
        onSave = { onCreate(displayName.trim()) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
            Text(stringResource(R.string.start_vault_message))
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text(stringResource(R.string.display_name)) },
                singleLine = true,
            )
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
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
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
