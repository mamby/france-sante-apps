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
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import net.mamby.health.R
import net.mamby.health.core.model.HealthSearchResult
import net.mamby.health.core.model.HealthSearchScope
import net.mamby.health.core.model.HealthSearchTarget
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.core.model.VaultItemKind
import net.mamby.health.core.model.profileRecord
import net.mamby.health.data.VaultState
import net.mamby.health.feature.dashboard.DashboardScreen
import net.mamby.health.feature.error.VaultUnreadableScreen
import net.mamby.health.feature.lock.LockScreen
import net.mamby.health.feature.medications.MedicationDetailScreen
import net.mamby.health.feature.medications.MedicationsScreen
import net.mamby.health.feature.measurements.ManageMeasurementTypesScreen
import net.mamby.health.feature.measurements.MeasurementDetailScreen
import net.mamby.health.feature.measurements.MeasurementsScreen
import net.mamby.health.feature.notes.NoteDetailScreen
import net.mamby.health.feature.notes.NotesScreen
import net.mamby.health.feature.directory.DirectoryEntryDetailScreen
import net.mamby.health.feature.directory.DirectoryScreen
import net.mamby.health.feature.records.HealthRecordsHubScreen
import net.mamby.health.feature.profiles.ProfileManagementScreen
import net.mamby.health.feature.profiles.ProfileNameDialog
import net.mamby.health.feature.schedule.ScheduleScreen
import net.mamby.health.feature.schedule.ScheduleDetailScreen
import net.mamby.health.feature.search.SearchScreen
import net.mamby.health.feature.search.SearchFilter
import net.mamby.health.feature.settings.SettingsScreen
import net.mamby.health.feature.summary.SummaryScreen
import net.mamby.health.feature.summary.CareDirectiveDetailScreen
import net.mamby.health.feature.summary.EmergencyContactDetailScreen
import net.mamby.health.feature.summary.FamilyHistoryDetailScreen
import net.mamby.health.feature.summary.HealthIdentifierDetailScreen
import net.mamby.health.feature.summary.HealthProfileDialog
import net.mamby.health.feature.summary.VaccinationDetailScreen
import net.mamby.health.feature.vault.DocumentDetailScreen
import net.mamby.health.feature.vault.ManageDocumentCategoriesScreen
import net.mamby.health.feature.vault.DocumentPreviewState
import net.mamby.health.feature.vault.VaultScreen
import net.mamby.health.navigation.AppRoute
import net.mamby.health.navigation.DeepLinkTarget
import net.mamby.health.navigation.DocumentDetailRoute
import net.mamby.health.navigation.CareDirectiveDetailRoute
import net.mamby.health.navigation.DirectoryEntryDetailRoute
import net.mamby.health.navigation.EmergencyContactDetailRoute
import net.mamby.health.navigation.FamilyHistoryDetailRoute
import net.mamby.health.navigation.HealthIdentifierDetailRoute
import net.mamby.health.navigation.HealthInfoRoute
import net.mamby.health.navigation.HealthRecordsRoute
import net.mamby.health.navigation.HomeRoute
import net.mamby.health.navigation.ManageProfilesRoute
import net.mamby.health.navigation.ManageDocumentCategoriesRoute
import net.mamby.health.navigation.ManageMeasurementTypesRoute
import net.mamby.health.navigation.MedicationDetailRoute
import net.mamby.health.navigation.MedicationsRoute
import net.mamby.health.navigation.MeasurementDetailRoute
import net.mamby.health.navigation.MeasurementsRoute
import net.mamby.health.navigation.NoteDetailRoute
import net.mamby.health.navigation.NotesRoute
import net.mamby.health.navigation.DirectoryRoute
import net.mamby.health.navigation.DocumentsRoute
import net.mamby.health.navigation.ScheduleRoute
import net.mamby.health.navigation.ScheduleDetailRoute
import net.mamby.health.navigation.SearchRoute
import net.mamby.health.navigation.SettingsRoute
import net.mamby.health.navigation.TopLevelDestination
import net.mamby.health.navigation.VaccinationDetailRoute
import net.mamby.health.navigation.rememberAppNavigationState
import net.mamby.health.security.AppLockState
import net.mamby.health.settings.AppSettings
import net.mamby.health.settings.ThemeMode
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.AppMoreSheet
import net.mamby.health.ui.components.AppNavigationSuite
import net.mamby.health.ui.components.FormDialog
import net.mamby.health.ui.components.LocalProfileDisplayLabels
import net.mamby.health.ui.components.ProfileMarker
import net.mamby.health.ui.components.ProfilePickerField
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.appContentWindowInsets
import net.mamby.health.ui.components.appNavigationSuiteType
import net.mamby.health.ui.components.disambiguatedProfileLabels
import net.mamby.health.ui.components.listDetailAwareBack
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
    var nextLocaleRequestId by remember { mutableLongStateOf(0L) }
    var localeRequest by remember { mutableStateOf<LocaleTransitionRequest?>(null) }
    val localeOverlayAlpha = remember { Animatable(1f) }
    val outgoingContentLayer = rememberGraphicsLayer()
    val localeOverlayFrames = remember { Channel<Long>(Channel.CONFLATED) }
    val localizedContentFrames = remember { Channel<LocalizedContentFrame>(Channel.CONFLATED) }
    val configuration = LocalConfiguration.current
    val currentLocaleTag = configuration.locales[0].language
    val selectedLocaleTag = remember(configuration) {
        AppCompatDelegate.getApplicationLocales()
            .get(0)
            ?.language
            ?.takeIf { it in AppSettings.supportedLocaleTags }
            ?: AppSettings.DEFAULT_LOCALE_TAG
    }
    val requestLocaleChange: (String) -> Unit = { localeTag ->
        if (localeRequest == null && localeTag != selectedLocaleTag) {
            nextLocaleRequestId += 1
            localeRequest = LocaleTransitionRequest(nextLocaleRequestId, localeTag)
        }
    }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(NOTICE_DURATION_MILLIS)
            viewModel.clearNotice()
        }
    }
    LaunchedEffect(lockState) {
        if (lockState == AppLockState.Locked || lockState == AppLockState.Authenticating) {
            localeRequest = null
            viewModel.resetPreview()
        }
    }
    LaunchedEffect(vaultState) {
        if (vaultState !is VaultState.Unreadable && vaultState !is VaultState.Missing) {
            recoverySettingsVisible = false
        }
    }
    LaunchedEffect(localeRequest, selectedLocaleTag) {
        val request = localeRequest ?: return@LaunchedEffect
        if (selectedLocaleTag != request.localeTag) {
            localeOverlayAlpha.snapTo(1f)
            while (localeOverlayFrames.receive() != request.id) {
                // Ignore frames from an earlier transition.
            }
            withFrameNanos { }
            viewModel.setLocaleTag(request.localeTag)
        } else {
            while (localizedContentFrames.receive() != LocalizedContentFrame(request.id, currentLocaleTag)) {
                // Keep the outgoing frame visible until the localized content has drawn.
            }
            withFrameNanos { }
            localeOverlayAlpha.animateTo(0f, animationSpec = tween())
            localeRequest = null
        }
    }

    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    HealthVaultTheme(darkTheme = darkTheme) {
        Box(Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val request = localeRequest
                        if (request == null) {
                            outgoingContentLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(outgoingContentLayer)
                        } else {
                            drawContent()
                            localizedContentFrames.trySend(
                                LocalizedContentFrame(request.id, currentLocaleTag),
                            )
                        }
                    },
            ) {
                when (lockState) {
                    AppLockState.Initializing -> Unit
                    AppLockState.Locked, AppLockState.Authenticating -> LockScreen(
                        state = lockState,
                        message = notice?.let { stringResource(it.resourceId) },
                        onUnlock = { activity?.let(viewModel::unlock) },
                    )
                    AppLockState.Disabled, AppLockState.Unlocked -> when (val state = vaultState) {
                        VaultState.Loading -> Unit
                        VaultState.Missing -> if (recoverySettingsVisible) {
                            RecoverySettings(
                                settings = settings,
                                viewModel = viewModel,
                                restorePreview = restorePreview,
                                notice = notice,
                                activity = activity,
                                onLocaleChanged = requestLocaleChange,
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
                                onLocaleChanged = requestLocaleChange,
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
                            settings = settings,
                            preview = preview,
                            restorePreview = restorePreview,
                            notice = notice,
                            viewModel = viewModel,
                            activity = activity,
                            onLocaleChanged = requestLocaleChange,
                        )
                    }
                }
            }
            localeRequest?.let { request ->
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        }
                        .graphicsLayer { alpha = localeOverlayAlpha.value },
                ) {
                    drawLayer(outgoingContentLayer)
                    if (localeOverlayAlpha.value == 1f) {
                        localeOverlayFrames.trySend(request.id)
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
}

@Composable
private fun RecoverySettings(
    settings: AppSettings,
    viewModel: AppViewModel,
    restorePreview: net.mamby.health.backup.RestorePreview?,
    notice: UiNotice?,
    activity: FragmentActivity?,
    onLocaleChanged: (String) -> Unit,
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
        onLocaleChanged = onLocaleChanged,
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
    settings: AppSettings,
    preview: DocumentPreviewState,
    restorePreview: net.mamby.health.backup.RestorePreview?,
    notice: UiNotice?,
    viewModel: AppViewModel,
    activity: FragmentActivity?,
    onLocaleChanged: (String) -> Unit,
) {
    val navigation = rememberAppNavigationState()
    val currentVault by rememberUpdatedState(vault)
    val context = LocalContext.current
    val resources = LocalResources.current
    val profileLabels = disambiguatedProfileLabels(vault.profiles.map { it.profile }) { profile, ordinal, _ ->
        resources.getString(R.string.profile_disambiguated_name, profile.displayName, ordinal)
    }
    var moreSheetVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchFilter by remember { mutableStateOf(SearchFilter.ALL) }
    var healthCreationVisible by remember { mutableStateOf(false) }
    var healthCreationProfileId by remember { mutableStateOf<UUID?>(null) }
    var documentCreation by remember { mutableLongStateOf(0) }
    var noteCreation by remember { mutableLongStateOf(0) }
    var measurementCreation by remember { mutableLongStateOf(0) }
    var directoryCreation by remember { mutableLongStateOf(0) }
    var medicationCreation by remember { mutableLongStateOf(0) }
    var scheduleCreation by remember { mutableLongStateOf(0) }
    var pendingOwnerAction by remember { mutableStateOf<ProfileOwnerAction?>(null) }
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

    LaunchedEffect(pendingDeepLink) {
        val target = pendingDeepLink ?: return@LaunchedEffect
        when (target) {
            DeepLinkTarget.Dashboard -> navigation.select(TopLevelDestination.Home)
            is DeepLinkTarget.Medication -> navigation.navigate(
                TopLevelDestination.Medications,
                target.medicationId?.let { MedicationDetailRoute(target.profileId, it) },
            )
            is DeepLinkTarget.Schedule -> navigation.navigate(
                TopLevelDestination.Schedule,
                target.scheduleId?.let(::ScheduleDetailRoute),
            )
        }
        pendingDeepLink = null
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
            val available = when (target) {
                DeepLinkTarget.Dashboard -> true
                is DeepLinkTarget.Medication -> runCatching { UUID.fromString(target.profileId) }.getOrNull()
                    ?.let { ownerId -> currentVault.profiles.any { it.profile.id == ownerId } } == true
                is DeepLinkTarget.Schedule -> target.scheduleId == null ||
                    currentVault.schedules.any { it.id.toString() == target.scheduleId }
            }
            if (!available) {
                navigation.resetTo()
                viewModel.showUnavailable()
                return@collect
            }
            pendingDeepLink = target
        }
    }

    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val navigationSuiteType = remember(adaptiveInfo) { appNavigationSuiteType(adaptiveInfo) }
    val usesMore = navigationSuiteType == NavigationSuiteType.ShortNavigationBarCompact
    val isMoreSelected = usesMore && navigation.selectedDestination in TopLevelDestination.compactOverflow
    val directive = remember(adaptiveInfo) {
        calculatePaneScaffoldDirective(adaptiveInfo).copy(horizontalPartitionSpacerSize = 0.dp)
    }
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)
    val now = viewModel.clock.instant()
    val zoneId = viewModel.zoneId
    val today = now.atZone(zoneId).toLocalDate()
    val message = notice?.let { stringResource(it.resourceId) }

    fun openHealthRecordsCollection(route: AppRoute) {
        if (
            navigation.selectedDestination == TopLevelDestination.HealthRecords &&
            navigation.currentBackStack.lastOrNull() == route
        ) return
        navigation.selectRoot(TopLevelDestination.HealthRecords)
        navigation.navigate(route)
    }

    fun startOwnerAction(action: ProfileOwnerAction, profileId: UUID) {
        pendingOwnerAction = null
        when (action) {
            ProfileOwnerAction.DOCUMENT_CATEGORIES -> navigation.navigate(
                ManageDocumentCategoriesRoute(profileId.toString()),
            )
            ProfileOwnerAction.MEASUREMENT_TYPES -> navigation.navigate(
                ManageMeasurementTypesRoute(profileId.toString()),
            )
        }
    }

    fun requestOwner(action: ProfileOwnerAction, profileId: UUID? = null) {
        val owner = profileId ?: vault.profiles.singleOrNull()?.profile?.id
        if (owner == null) pendingOwnerAction = action else startOwnerAction(action, owner)
    }

    LaunchedEffect(usesMore) {
        if (!usesMore) moreSheetVisible = false
    }

    Box(Modifier.fillMaxSize()) {
        BackHandler(enabled = navigation.isAtSecondaryRoot, onBack = navigation::goBack)
        AppNavigationSuite(
            selectedDestination = navigation.selectedDestination,
            layoutType = navigationSuiteType,
            isMoreSelected = isMoreSelected,
            onDestinationSelected = navigation::selectRoot,
            onMoreSelected = { moreSheetVisible = true },
        ) {
            Box(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalProfileDisplayLabels provides profileLabels) {
                    NavDisplay(
                        backStack = navigation.currentBackStack,
                        onBack = navigation::goBack,
                        sceneStrategies = listOf(listDetailStrategy),
                        entryProvider = entryProvider {
                    entry<HomeRoute> {
                        DashboardScreen(
                            records = vault.profiles,
                            notes = vault.notes,
                            schedules = vault.schedules,
                            clock = viewModel.clock,
                            zoneId = zoneId,
                            onMedications = { navigation.selectRoot(TopLevelDestination.Medications) },
                            onSchedule = { navigation.selectRoot(TopLevelDestination.Schedule) },
                            onDocumentSelected = { profileId, id ->
                                viewModel.resetPreview()
                                navigation.navigate(
                                    TopLevelDestination.HealthRecords,
                                    DocumentsRoute,
                                    DocumentDetailRoute(profileId.toString(), id),
                                )
                            },
                            onRecentItem = { profileId, item ->
                                when (item.kind) {
                                    VaultItemKind.DOCUMENT -> {
                                        viewModel.resetPreview()
                                        navigation.navigate(
                                            TopLevelDestination.HealthRecords,
                                            DocumentsRoute,
                                            DocumentDetailRoute(profileId.toString(), item.id.toString()),
                                        )
                                    }
                                    VaultItemKind.MEDICATION -> navigation.navigate(
                                        TopLevelDestination.Medications,
                                        MedicationDetailRoute(profileId.toString(), item.id.toString()),
                                    )
                                    VaultItemKind.SCHEDULE -> navigation.navigate(
                                        TopLevelDestination.Schedule,
                                        ScheduleDetailRoute(item.id.toString()),
                                    )
                                    VaultItemKind.VACCINATION -> navigation.navigate(
                                        TopLevelDestination.HealthRecords,
                                        HealthInfoRoute(profileId.toString()),
                                        VaccinationDetailRoute(profileId.toString(), item.id.toString()),
                                    )
                                    VaultItemKind.NOTE -> navigation.navigate(
                                        TopLevelDestination.Notes,
                                        NoteDetailRoute(item.id.toString()),
                                    )
                                    VaultItemKind.MEASUREMENT -> navigation.navigate(
                                        TopLevelDestination.HealthRecords,
                                        MeasurementsRoute,
                                        MeasurementDetailRoute(profileId.toString(), item.id.toString()),
                                    )
                                    VaultItemKind.DIRECTORY_ENTRY -> navigation.navigate(
                                        TopLevelDestination.Directory,
                                        DirectoryEntryDetailRoute(profileId.toString(), item.id.toString()),
                                    )
                                    VaultItemKind.FAMILY_HISTORY -> navigation.navigate(
                                        TopLevelDestination.HealthRecords,
                                        HealthInfoRoute(profileId.toString()),
                                        FamilyHistoryDetailRoute(profileId.toString(), item.id.toString()),
                                    )
                                    VaultItemKind.DIRECTIVE -> navigation.navigate(
                                        TopLevelDestination.HealthRecords,
                                        HealthInfoRoute(profileId.toString()),
                                        CareDirectiveDetailRoute(profileId.toString(), item.id.toString()),
                                    )
                                    VaultItemKind.IDENTIFIER -> navigation.navigate(
                                        TopLevelDestination.HealthRecords,
                                        HealthInfoRoute(profileId.toString()),
                                        HealthIdentifierDetailRoute(profileId.toString(), item.id.toString()),
                                    )
                                }
                            },
                            onNoteSelected = { noteId ->
                                navigation.navigate(
                                    TopLevelDestination.Notes,
                                    NoteDetailRoute(noteId.toString()),
                                )
                            },
                            onScheduleSelected = { scheduleId ->
                                navigation.navigate(
                                    TopLevelDestination.Schedule,
                                    ScheduleDetailRoute(scheduleId.toString()),
                                )
                            },
                            onAddHealthInfo = {
                                healthCreationProfileId = vault.profiles.singleOrNull()?.profile?.id
                                healthCreationVisible = true
                            },
                            onImportDocument = {
                                documentCreation++
                                openHealthRecordsCollection(DocumentsRoute)
                            },
                            onAddMedication = {
                                medicationCreation++
                                navigation.selectRoot(TopLevelDestination.Medications)
                            },
                            onAddSchedule = {
                                scheduleCreation++
                                navigation.selectRoot(TopLevelDestination.Schedule)
                            },
                        )
                    }
                    entry<HealthRecordsRoute> {
                        HealthRecordsHubScreen(
                            records = vault.profiles,
                            onHealthInfo = { navigation.navigate(HealthInfoRoute(it.toString())) },
                            onMeasurements = { navigation.navigate(MeasurementsRoute) },
                            onDocuments = { navigation.navigate(DocumentsRoute) },
                        )
                    }
                    entry<HealthInfoRoute> { route ->
                        val record = vault.profileRecordOrNull(route.profileId)
                        if (record == null) MissingRecordScreen(navigation::goBack) else SummaryScreen(
                            record = record,
                            today = today,
                            onBack = navigation::goBack,
                            onUpdateProfile = { viewModel.updateProfile(record.profile.id, it) },
                            onUpsertVaccination = { viewModel.upsertVaccination(record.profile.id, it) },
                            onDeleteVaccination = { viewModel.deleteVaccination(record.profile.id, it) },
                            onSetPrimaryDoctor = { viewModel.setPrimaryDoctor(record.profile.id, it) },
                            onUpsertFamilyHistory = { viewModel.upsertFamilyHistoryEntry(record.profile.id, it) },
                            onDeleteFamilyHistory = { viewModel.deleteFamilyHistoryEntry(record.profile.id, it) },
                            onUpsertDirective = { viewModel.upsertCareDirective(record.profile.id, it) },
                            onDeleteDirective = { viewModel.deleteCareDirective(record.profile.id, it) },
                            onUpsertIdentifier = { viewModel.upsertHealthIdentifier(record.profile.id, it) },
                            onDeleteIdentifier = { viewModel.deleteHealthIdentifier(record.profile.id, it) },
                            onEmergencyContactSelected = {
                                navigation.navigate(EmergencyContactDetailRoute(record.profile.id.toString(), it.toString()))
                            },
                            onVaccinationSelected = {
                                navigation.navigate(VaccinationDetailRoute(record.profile.id.toString(), it.toString()))
                            },
                            onFamilyHistorySelected = {
                                navigation.navigate(FamilyHistoryDetailRoute(record.profile.id.toString(), it.toString()))
                            },
                            onDirectiveSelected = {
                                navigation.navigate(CareDirectiveDetailRoute(record.profile.id.toString(), it.toString()))
                            },
                            onIdentifierSelected = {
                                navigation.navigate(HealthIdentifierDetailRoute(record.profile.id.toString(), it.toString()))
                            },
                        )
                    }
                    entry<DocumentsRoute>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { DetailPlaceholder(R.string.no_documents_body) },
                        ),
                    ) {
                        VaultScreen(
                            records = vault.profiles,
                            today = today,
                            onBack = navigation::goBack,
                            onManageCategories = { profileId ->
                                requestOwner(ProfileOwnerAction.DOCUMENT_CATEGORIES, profileId)
                            },
                            onAddProfile = viewModel::addProfile,
                            onImport = viewModel::importDocument,
                            onDocumentSelected = { profileId, id ->
                                navigation.navigate(DocumentDetailRoute(profileId.toString(), id))
                                viewModel.resetPreview()
                            },
                            creationRequest = documentCreation,
                        )
                    }
                    entry<NotesRoute>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { DetailPlaceholder(R.string.no_health_notes_body) },
                        ),
                    ) {
                        NotesScreen(
                            notes = vault.notes,
                            now = now,
                            zoneId = zoneId,
                            onUpsert = viewModel::upsertHealthNote,
                            onSelected = { id ->
                                navigation.navigate(NoteDetailRoute(id.toString()))
                            },
                            creationRequest = noteCreation,
                        )
                    }
                    entry<MeasurementsRoute>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { DetailPlaceholder(R.string.no_measurements_body) },
                        ),
                    ) {
                        MeasurementsScreen(
                            records = vault.profiles,
                            now = now,
                            zoneId = zoneId,
                            onBack = navigation::goBack,
                            onManageTypes = { profileId ->
                                requestOwner(ProfileOwnerAction.MEASUREMENT_TYPES, profileId)
                            },
                            onAddProfile = viewModel::addProfile,
                            onUpsert = viewModel::upsertMeasurement,
                            onSelected = { profileId, id ->
                                navigation.navigate(
                                    MeasurementDetailRoute(profileId.toString(), id.toString()),
                                )
                            },
                            creationRequest = measurementCreation,
                        )
                    }
                    entry<DirectoryRoute>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { DetailPlaceholder(R.string.no_directory_entries_body) },
                        ),
                    ) {
                        DirectoryScreen(
                            records = vault.profiles,
                            onAddProfile = viewModel::addProfile,
                            onUpsert = viewModel::upsertCareDirectoryEntry,
                            onSelected = { profileId, id ->
                                navigation.navigate(
                                    DirectoryEntryDetailRoute(profileId.toString(), id.toString()),
                                )
                            },
                            creationRequest = directoryCreation,
                        )
                    }
                    entry<SearchRoute>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { DetailPlaceholder(R.string.search_initial_body) },
                        ),
                    ) {
                        SearchScreen(
                            records = vault.profiles,
                            notes = vault.notes,
                            schedules = vault.schedules,
                            onResultSelected = { result ->
                                navigation.navigate(result.toRoute())
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
                            records = vault.profiles,
                            today = today,
                            onAddProfile = viewModel::addProfile,
                            onUpsert = { profileId, medication ->
                                withNotificationPermission(medication.remindersEnabled) {
                                    viewModel.upsertMedication(profileId, medication)
                                }
                            },
                            onSelected = { profileId, id ->
                                navigation.navigate(MedicationDetailRoute(profileId.toString(), id))
                            },
                            creationRequest = medicationCreation,
                        )
                    }
                    entry<ScheduleRoute>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { DetailPlaceholder(R.string.schedule_intro) },
                        ),
                    ) {
                        ScheduleScreen(
                            schedules = vault.schedules,
                            profileNames = vault.profiles.map { it.profile.displayName },
                            today = today,
                            zoneId = zoneId,
                            now = now,
                            notificationsBlocked = notificationsBlocked,
                            onUpsert = { schedule ->
                                withNotificationPermission(schedule.alert != null) {
                                    viewModel.upsertSchedule(schedule)
                                }
                            },
                            onSelected = { id -> navigation.navigate(ScheduleDetailRoute(id)) },
                            onOpenNotificationSettings = context::openNotificationSettings,
                            creationRequest = scheduleCreation,
                        )
                    }
                    entry<DocumentDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val detailBack = listDetailAwareBack(navigation::goBack)
                        val record = vault.profileRecordOrNull(route.profileId)
                        val document = record?.documents?.firstOrNull { it.id.toString() == route.id }
                        if (record == null || document == null) MissingRecordScreen(detailBack) else DocumentDetailScreen(
                            document = document,
                            record = record,
                            preview = preview,
                            onBack = detailBack,
                            onLoadPreview = { viewModel.loadPreview(record.profile.id, document, it) },
                            onEdit = { viewModel.updateDocument(record.profile.id, it) },
                            onDelete = {
                                viewModel.deleteDocument(record.profile.id, document.id)
                                navigation.goBack()
                            },
                        )
                    }
                    entry<NoteDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val detailBack = listDetailAwareBack(navigation::goBack)
                        val note = vault.notes.firstOrNull { it.id.toString() == route.id }
                        if (note == null) MissingRecordScreen(detailBack) else NoteDetailScreen(
                            note = note,
                            now = now,
                            zoneId = zoneId,
                            onBack = detailBack,
                            onUpsert = viewModel::upsertHealthNote,
                            onDelete = {
                                viewModel.deleteHealthNote(note.id)
                                navigation.goBack()
                            },
                        )
                    }
                    entry<MeasurementDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val detailBack = listDetailAwareBack(navigation::goBack)
                        val record = vault.profileRecordOrNull(route.profileId)
                        val measurement = record?.measurements?.firstOrNull { it.id.toString() == route.id }
                        if (record == null || measurement == null) MissingRecordScreen(detailBack) else MeasurementDetailScreen(
                            record = record,
                            measurement = measurement,
                            now = now,
                            zoneId = zoneId,
                            onBack = detailBack,
                            onUpsert = { viewModel.upsertMeasurement(record.profile.id, it) },
                            onDelete = {
                                viewModel.deleteMeasurement(record.profile.id, measurement.id)
                                navigation.goBack()
                            },
                        )
                    }
                    entry<DirectoryEntryDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val detailBack = listDetailAwareBack(navigation::goBack)
                        val record = vault.profileRecordOrNull(route.profileId)
                        val directoryEntry = record?.careDirectory?.firstOrNull { it.id.toString() == route.id }
                        if (record == null || directoryEntry == null) MissingRecordScreen(detailBack) else DirectoryEntryDetailScreen(
                            record = record,
                            entry = directoryEntry,
                            onBack = detailBack,
                            onUpsert = { viewModel.upsertCareDirectoryEntry(record.profile.id, it) },
                            onSetPrimaryDoctor = { viewModel.setPrimaryDoctor(record.profile.id, it) },
                            onDelete = {
                                viewModel.deleteCareDirectoryEntry(record.profile.id, directoryEntry.id)
                                navigation.goBack()
                            },
                        )
                    }
                    entry<EmergencyContactDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val detailBack = listDetailAwareBack(navigation::goBack)
                        val record = vault.profileRecordOrNull(route.profileId)
                        val contact = record?.profile?.emergencyContacts?.firstOrNull { it.id.toString() == route.id }
                        if (record == null || contact == null) MissingRecordScreen(detailBack) else EmergencyContactDetailScreen(
                            record, contact, detailBack,
                        )
                    }
                    entry<VaccinationDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val detailBack = listDetailAwareBack(navigation::goBack)
                        val record = vault.profileRecordOrNull(route.profileId)
                        val vaccination = record?.vaccinations?.firstOrNull { it.id.toString() == route.id }
                        if (record == null || vaccination == null) MissingRecordScreen(detailBack) else VaccinationDetailScreen(
                            record, vaccination, detailBack,
                        )
                    }
                    entry<FamilyHistoryDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val detailBack = listDetailAwareBack(navigation::goBack)
                        val record = vault.profileRecordOrNull(route.profileId)
                        val history = record?.familyHistory?.firstOrNull { it.id.toString() == route.id }
                        if (record == null || history == null) MissingRecordScreen(detailBack) else FamilyHistoryDetailScreen(
                            record, history, detailBack,
                        )
                    }
                    entry<CareDirectiveDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val detailBack = listDetailAwareBack(navigation::goBack)
                        val record = vault.profileRecordOrNull(route.profileId)
                        val careDirective = record?.directives?.firstOrNull { it.id.toString() == route.id }
                        if (record == null || careDirective == null) MissingRecordScreen(detailBack) else CareDirectiveDetailScreen(
                            record, careDirective, detailBack,
                        )
                    }
                    entry<HealthIdentifierDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val detailBack = listDetailAwareBack(navigation::goBack)
                        val record = vault.profileRecordOrNull(route.profileId)
                        val identifier = record?.healthIdentifiers?.firstOrNull { it.id.toString() == route.id }
                        if (record == null || identifier == null) MissingRecordScreen(detailBack) else HealthIdentifierDetailScreen(
                            record, identifier, detailBack,
                        )
                    }
                    entry<ManageMeasurementTypesRoute> { route ->
                        val record = vault.profileRecordOrNull(route.profileId)
                        if (record == null) MissingRecordScreen(navigation::goBack) else ManageMeasurementTypesScreen(
                            record = record,
                            onBack = navigation::goBack,
                            onUpsert = { viewModel.upsertCustomMeasurementType(record.profile.id, it) },
                            onDelete = { viewModel.deleteCustomMeasurementType(record.profile.id, it) },
                        )
                    }
                    entry<ManageDocumentCategoriesRoute> { route ->
                        val record = vault.profileRecordOrNull(route.profileId)
                        if (record == null) MissingRecordScreen(navigation::goBack) else ManageDocumentCategoriesScreen(
                            record = record,
                            onBack = navigation::goBack,
                            onUpdateBuiltIn = { preference, replacement ->
                                viewModel.updateBuiltInDocumentCategoryPreference(record.profile.id, preference, replacement)
                            },
                            onUpsertCustom = { viewModel.upsertCustomDocumentCategory(record.profile.id, it) },
                            onDeleteCustom = { id, replacement ->
                                viewModel.deleteCustomDocumentCategory(record.profile.id, id, replacement)
                            },
                        )
                    }
                    entry<MedicationDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val detailBack = listDetailAwareBack(navigation::goBack)
                        val record = vault.profileRecordOrNull(route.profileId)
                        val medication = record?.medications?.firstOrNull { it.id.toString() == route.id }
                        if (record == null || medication == null) MissingRecordScreen(detailBack) else MedicationDetailScreen(
                            medication = medication,
                            directory = record.careDirectory,
                            profile = record.profile,
                            today = today,
                            onBack = detailBack,
                            onUpsert = { updated ->
                                withNotificationPermission(updated.remindersEnabled) {
                                    viewModel.upsertMedication(record.profile.id, updated)
                                }
                            },
                            onDelete = {
                                viewModel.deleteMedication(record.profile.id, medication.id)
                                navigation.goBack()
                            },
                        )
                    }
                    entry<ScheduleDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val detailBack = listDetailAwareBack(navigation::goBack)
                        val schedule = vault.schedules.firstOrNull { it.id.toString() == route.id }
                        if (schedule == null) MissingRecordScreen(detailBack) else ScheduleDetailScreen(
                            schedule = schedule,
                            profileNames = vault.profiles.map { it.profile.displayName },
                            zoneId = zoneId,
                            today = today,
                            onBack = detailBack,
                            onUpsert = { updated ->
                                withNotificationPermission(updated.alert != null) {
                                    viewModel.upsertSchedule(updated)
                                }
                            },
                            onDelete = {
                                viewModel.deleteSchedule(schedule.id)
                                navigation.goBack()
                            },
                        )
                    }
                    entry<SettingsRoute> {
                        SettingsScreen(
                            settings = settings,
                            zoneId = zoneId,
                            restorePreview = restorePreview,
                            message = null,
                            onThemeChanged = viewModel::setThemeMode,
                            onLocaleChanged = onLocaleChanged,
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
                        )
                    }
                    entry<ManageProfilesRoute> {
                        ProfileManagementScreen(
                            profiles = vault.profiles,
                            onAdd = {
                                viewModel.addProfile(it)
                            },
                            onRename = viewModel::updateProfile,
                            onDelete = {
                                viewModel.deleteProfile(it)
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
                            .windowInsetsPadding(appContentWindowInsets())
                            .padding(UiTokens.ScreenPadding),
                    ) { Text(message) }
                }
            }
        }
    }

    if (moreSheetVisible) {
        AppMoreSheet(
            onDismissRequest = { moreSheetVisible = false },
            onDestinationSelected = { destination ->
                moreSheetVisible = false
                navigation.selectRoot(destination)
            },
        )
    }

    pendingOwnerAction?.let { action ->
        ModalBottomSheet(onDismissRequest = { pendingOwnerAction = null }) {
            LazyColumn(Modifier.fillMaxWidth()) {
                item {
                    Text(
                        stringResource(R.string.choose_profile),
                        modifier = Modifier.padding(UiTokens.ScreenPadding),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                vault.profiles.forEach { candidate ->
                    item(key = candidate.profile.id) {
                        ListItem(
                            headlineContent = {
                                ProfileMarker(
                                    profile = candidate.profile,
                                    displayLabel = profileLabels.getValue(candidate.profile.id),
                                )
                            },
                            modifier = Modifier.clickable {
                                startOwnerAction(action, candidate.profile.id)
                            },
                        )
                    }
                }
            }
        }
    }
    if (healthCreationVisible) {
        val owner = vault.profiles.firstOrNull { it.profile.id == healthCreationProfileId }
        HealthProfileDialog(
            profile = owner?.profile ?: vault.profiles.first().profile,
            ownerSelected = owner != null,
            profilePicker = {
                ProfilePickerField(
                    records = vault.profiles,
                    selectedProfileId = healthCreationProfileId,
                    onSelected = { healthCreationProfileId = it },
                    onAddProfile = viewModel::addProfile,
                )
            },
            onDismiss = { healthCreationVisible = false },
            onSave = {
                viewModel.updateProfile(requireNotNull(healthCreationProfileId), it)
                healthCreationVisible = false
            },
        )
    }
}

private fun HealthSearchResult.toRoute(): AppRoute {
    val profileId = (scope as? HealthSearchScope.Profile)?.profileId
    fun requireProfileId(): String = requireNotNull(profileId) {
        "A profile-scoped search result must include its owner."
    }.toString()

    return when (val selected = target) {
        is HealthSearchTarget.Document -> DocumentDetailRoute(requireProfileId(), selected.id.toString())
        is HealthSearchTarget.Medication -> MedicationDetailRoute(requireProfileId(), selected.id.toString())
        is HealthSearchTarget.Schedule -> ScheduleDetailRoute(selected.id.toString())
        is HealthSearchTarget.EmergencyContact -> EmergencyContactDetailRoute(requireProfileId(), selected.id.toString())
        is HealthSearchTarget.Vaccination -> VaccinationDetailRoute(requireProfileId(), selected.id.toString())
        is HealthSearchTarget.HealthInfo -> HealthInfoRoute(requireProfileId())
        is HealthSearchTarget.Note -> NoteDetailRoute(selected.id.toString())
        is HealthSearchTarget.Measurement -> MeasurementDetailRoute(requireProfileId(), selected.id.toString())
        is HealthSearchTarget.DirectoryEntry -> DirectoryEntryDetailRoute(requireProfileId(), selected.id.toString())
        is HealthSearchTarget.FamilyHistory -> FamilyHistoryDetailRoute(requireProfileId(), selected.id.toString())
        is HealthSearchTarget.Directive -> CareDirectiveDetailRoute(requireProfileId(), selected.id.toString())
        is HealthSearchTarget.Identifier -> HealthIdentifierDetailRoute(requireProfileId(), selected.id.toString())
    }
}

@Composable
internal fun MissingVaultScreen(onStart: () -> Unit, onRestore: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
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
}

@Composable
private fun DetailPlaceholder(messageResource: Int) {
    Box(
        Modifier.fillMaxSize().windowInsetsPadding(appContentWindowInsets()),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(messageResource), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun MissingRecordScreen(onBack: (() -> Unit)?) {
    AppScreenScaffold(
        title = stringResource(R.string.record_unavailable),
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .padding(UiTokens.ScreenPadding),
        ) {
            PageHeader()
        }
    }
}

@Composable
private fun ResetUnreadableDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val required = stringResource(R.string.delete_vault_confirmation_word)
    var confirmation by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = UiTokens.DialogTonalElevation,
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

private data class LocaleTransitionRequest(val id: Long, val localeTag: String)

private data class LocalizedContentFrame(val requestId: Long, val localeTag: String)

private enum class ProfileOwnerAction {
    DOCUMENT_CATEGORIES,
    MEASUREMENT_TYPES,
}

private fun HealthVault.profileRecordOrNull(profileId: String): ProfileRecord? =
    runCatching { UUID.fromString(profileId) }.getOrNull()?.let { id ->
        profiles.firstOrNull { it.profile.id == id }
    }

private const val NOTICE_DURATION_MILLIS = 4_000L
