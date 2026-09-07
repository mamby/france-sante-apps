package net.mamby.health.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
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
import net.mamby.health.feature.medications.MedicationDetailScreen
import net.mamby.health.feature.medications.MedicationEditorScreen
import net.mamby.health.feature.medications.MedicationsScreen
import net.mamby.health.feature.measurements.ManageMeasurementTypesScreen
import net.mamby.health.feature.measurements.MeasurementDetailScreen
import net.mamby.health.feature.measurements.MeasurementEditorScreen
import net.mamby.health.feature.measurements.MeasurementsScreen
import net.mamby.health.feature.notes.NoteDetailScreen
import net.mamby.health.feature.notes.NotesScreen
import net.mamby.health.feature.notes.HealthNoteEditorScreen
import net.mamby.health.feature.contacts.ContactDetailScreen
import net.mamby.health.feature.contacts.ContactEditorScreen
import net.mamby.health.feature.contacts.ContactsScreen
import net.mamby.health.feature.records.HealthRecordsHubScreen
import net.mamby.health.feature.profiles.ProfileManagementScreen
import net.mamby.health.feature.profiles.ProfileOwnerGateScreen
import net.mamby.health.feature.schedule.ScheduleScreen
import net.mamby.health.feature.schedule.ScheduleDetailScreen
import net.mamby.health.feature.schedule.ScheduleEditorScreen
import net.mamby.health.feature.search.SearchScreen
import net.mamby.health.feature.search.SearchFilter
import net.mamby.health.feature.settings.SettingsScreen
import net.mamby.health.feature.summary.SummaryScreen
import net.mamby.health.feature.summary.CareDirectiveDetailScreen
import net.mamby.health.feature.summary.CareDirectiveEditorScreen
import net.mamby.health.feature.summary.EmergencyContactDetailScreen
import net.mamby.health.feature.summary.EmergencyContactEditorScreen
import net.mamby.health.feature.summary.FamilyHistoryDetailScreen
import net.mamby.health.feature.summary.FamilyHistoryEditorScreen
import net.mamby.health.feature.summary.HealthIdentifierDetailScreen
import net.mamby.health.feature.summary.HealthIdentifierEditorScreen
import net.mamby.health.feature.summary.HealthProfileEditorScreen
import net.mamby.health.feature.summary.VaccinationDetailScreen
import net.mamby.health.feature.summary.VaccinationEditorScreen
import net.mamby.health.feature.vault.DocumentDetailScreen
import net.mamby.health.feature.vault.DocumentEditorScreen
import net.mamby.health.feature.vault.DocumentImportEditorScreen
import net.mamby.health.feature.vault.ManageDocumentCategoriesScreen
import net.mamby.health.feature.vault.DocumentPreviewState
import net.mamby.health.feature.vault.VaultScreen
import net.mamby.health.navigation.AppRoute
import net.mamby.health.navigation.AppNavigationState
import net.mamby.health.navigation.DeepLinkTarget
import net.mamby.health.navigation.DocumentDetailRoute
import net.mamby.health.navigation.DocumentEditorRoute
import net.mamby.health.navigation.DocumentImportEditorRoute
import net.mamby.health.navigation.EditorRoute
import net.mamby.health.navigation.CareDirectiveEditorRoute
import net.mamby.health.navigation.CareDirectiveDetailRoute
import net.mamby.health.navigation.ContactDetailRoute
import net.mamby.health.navigation.ContactEditorRoute
import net.mamby.health.navigation.EmergencyContactDetailRoute
import net.mamby.health.navigation.EmergencyContactEditorRoute
import net.mamby.health.navigation.FamilyHistoryDetailRoute
import net.mamby.health.navigation.FamilyHistoryEditorRoute
import net.mamby.health.navigation.HealthIdentifierDetailRoute
import net.mamby.health.navigation.HealthIdentifierEditorRoute
import net.mamby.health.navigation.HealthInfoRoute
import net.mamby.health.navigation.HealthNoteEditorRoute
import net.mamby.health.navigation.HealthProfileEditorRoute
import net.mamby.health.navigation.HealthRecordsRoute
import net.mamby.health.navigation.HomeRoute
import net.mamby.health.navigation.ManageProfilesRoute
import net.mamby.health.navigation.ManageDocumentCategoriesRoute
import net.mamby.health.navigation.ManageMeasurementTypesRoute
import net.mamby.health.navigation.MedicationDetailRoute
import net.mamby.health.navigation.MedicationEditorRoute
import net.mamby.health.navigation.MedicationsRoute
import net.mamby.health.navigation.MeasurementDetailRoute
import net.mamby.health.navigation.MeasurementEditorRoute
import net.mamby.health.navigation.MeasurementsRoute
import net.mamby.health.navigation.NoteDetailRoute
import net.mamby.health.navigation.NotesRoute
import net.mamby.health.navigation.ContactsRoute
import net.mamby.health.navigation.DocumentsRoute
import net.mamby.health.navigation.ScheduleRoute
import net.mamby.health.navigation.ScheduleDetailRoute
import net.mamby.health.navigation.ScheduleEditorRoute
import net.mamby.health.navigation.SearchRoute
import net.mamby.health.navigation.SettingsRoute
import net.mamby.health.navigation.TopLevelDestination
import net.mamby.health.navigation.ProfileOwnedCreateTarget
import net.mamby.health.navigation.ProfileOwnerGateRoute
import net.mamby.health.navigation.VaccinationDetailRoute
import net.mamby.health.navigation.VaccinationEditorRoute
import net.mamby.health.navigation.rememberAppNavigationState
import net.mamby.health.security.AppLockState
import net.mamby.health.settings.AppSettings
import net.mamby.health.settings.ThemeMode
import net.mamby.androidkit.compose.layout.AndroidKitPage
import net.mamby.androidkit.compose.layout.AndroidKitLockPage
import net.mamby.health.ui.components.AppNavigationSuite
import net.mamby.health.ui.components.EditorBackgroundPane
import net.mamby.health.ui.components.LocalProfileDisplayLabels
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.appContentWindowInsets
import net.mamby.health.ui.components.disambiguatedProfileLabels
import net.mamby.health.ui.components.listDetailAwareBack
import net.mamby.health.ui.components.withPagePadding
import net.mamby.health.ui.theme.HealthVaultTheme
import net.mamby.health.ui.theme.UiTokens

@Composable
fun HealthVaultApp(viewModel: AppViewModel = viewModel()) {
    val navigation = rememberAppNavigationState()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val vaultState by viewModel.vaultState.collectAsStateWithLifecycle()
    val lockState by viewModel.lockState.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val restorePreview by viewModel.restorePreview.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
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
            viewModel.clearEditorSessions()
        }
    }
    LaunchedEffect(vaultState) {
        if (vaultState !is VaultState.Unreadable && vaultState !is VaultState.Missing) {
            recoverySettingsVisible = false
        }
        if (vaultState !is VaultState.Ready) viewModel.clearEditorSessions()
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
    HealthVaultTheme(darkTheme = darkTheme, floatingSurfaceOpacityLevel = settings.floatingSurfaceOpacityLevel) {
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
                    AppLockState.Locked, AppLockState.Authenticating -> AndroidKitLockPage(
                        message = stringResource(R.string.lock_body),
                        unlockLabel = stringResource(R.string.unlock_action),
                        isUnlocking = lockState == AppLockState.Authenticating,
                        errorMessage = notice?.let { stringResource(it.resourceId) },
                        onUnlock = { activity?.let(viewModel::unlock) },
                    )
                    AppLockState.Disabled, AppLockState.Unlocked -> when (val state = vaultState) {
                        VaultState.Loading -> Unit
                        // Missing is transient: initialization immediately persists a valid empty data set.
                        VaultState.Missing -> Unit
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
                            navigation = navigation,
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
        onOpacityChanged = viewModel::previewFloatingSurfaceOpacityLevel,
        onOpacityChangeFinished = { viewModel.saveFloatingSurfaceOpacityLevel() },
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
    navigation: AppNavigationState,
    vault: HealthVault,
    settings: AppSettings,
    preview: DocumentPreviewState,
    restorePreview: net.mamby.health.backup.RestorePreview?,
    notice: UiNotice?,
    viewModel: AppViewModel,
    activity: FragmentActivity?,
    onLocaleChanged: (String) -> Unit,
) {
    val currentVault by rememberUpdatedState(vault)
    val context = LocalContext.current
    val resources = LocalResources.current
    val profileLabels = disambiguatedProfileLabels(vault.profiles.map { it.profile }) { profile, ordinal, _ ->
        resources.getString(R.string.profile_disambiguated_name, profile.displayName, ordinal)
    }
    var searchQuery by remember { mutableStateOf("") }
    var searchFilter by remember { mutableStateOf(SearchFilter.ALL) }
    var restoreRequestId by rememberSaveable { mutableLongStateOf(0L) }
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

    fun discardActiveEditorForNavigation() {
        val editorRoute = navigation.currentBackStack.lastOrNull() as? EditorRoute ?: return
        viewModel.closeEditorSession(editorRoute.sessionId)
        navigation.goBack()
        viewModel.showEditorDraftDiscarded()
    }

    LaunchedEffect(pendingDeepLink) {
        val target = pendingDeepLink ?: return@LaunchedEffect
        discardActiveEditorForNavigation()
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
                discardActiveEditorForNavigation()
                navigation.resetTo()
                viewModel.showUnavailable()
                return@collect
            }
            pendingDeepLink = target
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
    val message = notice?.let { stringResource(it.resourceId) }
    val activeEditorRoute = navigation.currentBackStack.lastOrNull() as? EditorRoute
    val activeOwnerGateRoute = navigation.currentBackStack.lastOrNull() as? ProfileOwnerGateRoute
    val focusedFlowActive = activeEditorRoute != null || activeOwnerGateRoute != null
    val editorSessionValid = activeEditorRoute?.let {
        viewModel.isEditorSessionActive(it.sessionId)
    } ?: true

    LaunchedEffect(activeEditorRoute, editorSessionValid) {
        if (activeEditorRoute != null && !editorSessionValid) {
            navigation.goBack()
            viewModel.showEditorDraftDiscarded()
        }
    }

    fun closeEditor(sessionId: String) {
        viewModel.closeEditorSession(sessionId)
        val activeRoute = navigation.currentBackStack.lastOrNull() as? EditorRoute
        if (activeRoute?.sessionId == sessionId) navigation.goBack()
    }

    fun continueProfileOwnedCreate(
        target: ProfileOwnedCreateTarget,
        profileId: UUID,
        replaceGate: Boolean = false,
    ) {
        val owner = profileId.toString()
        val routes = when (target) {
            ProfileOwnedCreateTarget.HEALTH_INFO -> arrayOf(
                HealthInfoRoute(owner),
                HealthProfileEditorRoute(viewModel.createEditorSession(), owner),
            )
            ProfileOwnedCreateTarget.DOCUMENT_IMPORT -> arrayOf(
                DocumentImportEditorRoute(viewModel.createEditorSession(), owner),
            )
            ProfileOwnedCreateTarget.MEDICATION -> arrayOf(
                MedicationEditorRoute(viewModel.createEditorSession(), owner),
            )
            ProfileOwnedCreateTarget.MEASUREMENT -> arrayOf(
                MeasurementEditorRoute(viewModel.createEditorSession(), owner),
            )
            ProfileOwnedCreateTarget.DOCUMENT_CATEGORIES -> arrayOf(
                ManageDocumentCategoriesRoute(owner),
            )
            ProfileOwnedCreateTarget.MEASUREMENT_TYPES -> arrayOf(
                ManageMeasurementTypesRoute(owner),
            )
        }
        if (replaceGate) navigation.replaceTop(*routes) else routes.forEach(navigation::navigate)
    }

    fun requestProfileOwner(target: ProfileOwnedCreateTarget, suggestedProfileId: UUID? = null) {
        val validSuggestion = suggestedProfileId?.takeIf { suggested ->
            vault.profiles.any { it.profile.id == suggested }
        }
        val owner = validSuggestion ?: vault.profiles.singleOrNull()?.profile?.id
        if (owner != null) {
            continueProfileOwnedCreate(target, owner)
        } else {
            navigation.navigate(ProfileOwnerGateRoute(target, UUID.randomUUID().toString()))
        }
    }

    LaunchedEffect(activeOwnerGateRoute, vault.profiles) {
        val route = activeOwnerGateRoute ?: return@LaunchedEffect
        val proposedId = runCatching { UUID.fromString(route.proposedProfileId) }.getOrNull()
            ?: return@LaunchedEffect
        if (vault.profiles.any { it.profile.id == proposedId }) {
            continueProfileOwnedCreate(route.target, proposedId, replaceGate = true)
        }
    }

    Box(Modifier.fillMaxSize()) {
        BackHandler(enabled = navigation.isAtSecondaryRoot, onBack = navigation::goBack)
        AppNavigationSuite(
            selectedDestination = navigation.selectedDestination,
            onDestinationSelected = navigation::selectRoot,
            navigationVisible = !focusedFlowActive,
        ) {
            Box(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalProfileDisplayLabels provides profileLabels) {
                    if (editorSessionValid) NavDisplay(
                        backStack = navigation.currentBackStack,
                        onBack = navigation::goBack,
                        sceneStrategies = listOf(listDetailStrategy),
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        entryProvider = entryProvider {
                    entry<HomeRoute> {
                        DashboardScreen(
                            records = vault.profiles,
                            notes = vault.notes,
                            schedules = vault.schedules,
                            contacts = vault.contacts,
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
                                    VaultItemKind.CONTACT -> navigation.navigate(
                                        TopLevelDestination.Contacts,
                                        ContactDetailRoute(item.id.toString()),
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
                            onContactSelected = { contactId ->
                                navigation.navigate(
                                    TopLevelDestination.Contacts,
                                    ContactDetailRoute(contactId.toString()),
                                )
                            },
                            onAddHealthInfo = {
                                navigation.selectRoot(TopLevelDestination.HealthRecords)
                                requestProfileOwner(ProfileOwnedCreateTarget.HEALTH_INFO)
                            },
                            onImportDocument = {
                                navigation.selectRoot(TopLevelDestination.HealthRecords)
                                navigation.navigate(DocumentsRoute)
                                requestProfileOwner(ProfileOwnedCreateTarget.DOCUMENT_IMPORT)
                            },
                            onAddMedication = {
                                navigation.selectRoot(TopLevelDestination.Medications)
                                requestProfileOwner(ProfileOwnedCreateTarget.MEDICATION)
                            },
                            onAddSchedule = {
                                navigation.selectRoot(TopLevelDestination.Schedule)
                                navigation.navigate(
                                    ScheduleEditorRoute(viewModel.createEditorSession()),
                                )
                            },
                            onAddNote = {
                                navigation.selectRoot(TopLevelDestination.Notes)
                                navigation.navigate(
                                    HealthNoteEditorRoute(viewModel.createEditorSession()),
                                )
                            },
                            onAddContact = {
                                navigation.selectRoot(TopLevelDestination.Contacts)
                                navigation.navigate(
                                    ContactEditorRoute(viewModel.createEditorSession()),
                                )
                            },
                            restorePrompt = if (
                                vault.profiles.isEmpty() && vault.notes.isEmpty() &&
                                vault.schedules.isEmpty() && vault.contacts.isEmpty()
                            ) {
                                {
                                    FreshRestorePrompt {
                                        restoreRequestId += 1
                                        navigation.selectRoot(TopLevelDestination.Settings)
                                    }
                                }
                            } else {
                                null
                            },
                        )
                    }
                    entry<HealthRecordsRoute> {
                        HealthRecordsHubScreen(
                            records = vault.profiles,
                            onHealthInfo = { navigation.navigate(HealthInfoRoute(it.toString())) },
                            onAddHealthInfo = {
                                requestProfileOwner(ProfileOwnedCreateTarget.HEALTH_INFO)
                            },
                            onMeasurements = { navigation.navigate(MeasurementsRoute) },
                            onDocuments = { navigation.navigate(DocumentsRoute) },
                        )
                    }
                    entry<HealthInfoRoute>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { DetailPlaceholder(R.string.health_info_hub_body) },
                        ),
                    ) { route ->
                        val record = vault.profileRecordOrNull(route.profileId)
                        if (record == null) MissingRecordScreen(navigation::goBack) else {
                            val profileId = record.profile.id.toString()
                            EditorBackgroundPane(focusedFlowActive) {
                                SummaryScreen(
                                    record = record,
                                    onBack = navigation::goBack,
                                    onEditProfile = {
                                        navigation.navigate(
                                            HealthProfileEditorRoute(
                                                viewModel.createEditorSession(),
                                                profileId,
                                            ),
                                        )
                                    },
                                    onAddEmergencyContact = {
                                        navigation.navigate(EmergencyContactEditorRoute(
                                            viewModel.createEditorSession(), profileId,
                                        ))
                                    },
                                    onEditEmergencyContact = { id ->
                                        navigation.navigate(EmergencyContactEditorRoute(
                                            viewModel.createEditorSession(), profileId, id.toString(),
                                        ))
                                    },
                                    onAddVaccination = {
                                        navigation.navigate(VaccinationEditorRoute(
                                            viewModel.createEditorSession(), profileId,
                                        ))
                                    },
                                    onEditVaccination = { id ->
                                        navigation.navigate(VaccinationEditorRoute(
                                            viewModel.createEditorSession(), profileId, id.toString(),
                                        ))
                                    },
                                    onAddFamilyHistory = {
                                        navigation.navigate(FamilyHistoryEditorRoute(
                                            viewModel.createEditorSession(), profileId,
                                        ))
                                    },
                                    onEditFamilyHistory = { id ->
                                        navigation.navigate(FamilyHistoryEditorRoute(
                                            viewModel.createEditorSession(), profileId, id.toString(),
                                        ))
                                    },
                                    onAddDirective = {
                                        navigation.navigate(CareDirectiveEditorRoute(
                                            viewModel.createEditorSession(), profileId,
                                        ))
                                    },
                                    onEditDirective = { id ->
                                        navigation.navigate(CareDirectiveEditorRoute(
                                            viewModel.createEditorSession(), profileId, id.toString(),
                                        ))
                                    },
                                    onAddIdentifier = {
                                        navigation.navigate(HealthIdentifierEditorRoute(
                                            viewModel.createEditorSession(), profileId,
                                        ))
                                    },
                                    onEditIdentifier = { id ->
                                        navigation.navigate(HealthIdentifierEditorRoute(
                                            viewModel.createEditorSession(), profileId, id.toString(),
                                        ))
                                    },
                                    onEmergencyContactSelected = { id ->
                                        navigation.navigate(EmergencyContactDetailRoute(profileId, id.toString()))
                                    },
                                    onVaccinationSelected = { id ->
                                        navigation.navigate(VaccinationDetailRoute(profileId, id.toString()))
                                    },
                                    onFamilyHistorySelected = { id ->
                                        navigation.navigate(FamilyHistoryDetailRoute(profileId, id.toString()))
                                    },
                                    onDirectiveSelected = { id ->
                                        navigation.navigate(CareDirectiveDetailRoute(profileId, id.toString()))
                                    },
                                    onIdentifierSelected = { id ->
                                        navigation.navigate(HealthIdentifierDetailRoute(profileId, id.toString()))
                                    },
                                )
                            }
                        }
                    }
                    entry<DocumentsRoute>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { DetailPlaceholder(R.string.no_documents_body) },
                        ),
                    ) {
                        EditorBackgroundPane(focusedFlowActive) {
                            VaultScreen(
                                records = vault.profiles,
                                onBack = navigation::goBack,
                                onManageCategories = { profileId ->
                                    requestProfileOwner(ProfileOwnedCreateTarget.DOCUMENT_CATEGORIES, profileId)
                                },
                                onImportRequested = { profileId ->
                                    requestProfileOwner(ProfileOwnedCreateTarget.DOCUMENT_IMPORT, profileId)
                                },
                                onDocumentSelected = { profileId, id ->
                                    navigation.navigate(DocumentDetailRoute(profileId.toString(), id))
                                    viewModel.resetPreview()
                                },
                            )
                        }
                    }
                    entry<NotesRoute>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { DetailPlaceholder(R.string.no_health_notes_body) },
                        ),
                    ) {
                        EditorBackgroundPane(activeEditorRoute is HealthNoteEditorRoute) {
                            NotesScreen(
                                notes = vault.notes,
                                zoneId = zoneId,
                                onAdd = {
                                    navigation.navigate(
                                        HealthNoteEditorRoute(viewModel.createEditorSession()),
                                    )
                                },
                                onSelected = { id ->
                                    navigation.navigate(NoteDetailRoute(id.toString()))
                                },
                            )
                        }
                    }
                    entry<MeasurementsRoute>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { DetailPlaceholder(R.string.no_measurements_body) },
                        ),
                    ) {
                        EditorBackgroundPane(focusedFlowActive) {
                            MeasurementsScreen(
                                records = vault.profiles,
                                zoneId = zoneId,
                                onBack = navigation::goBack,
                                onManageTypes = { profileId ->
                                    requestProfileOwner(ProfileOwnedCreateTarget.MEASUREMENT_TYPES, profileId)
                                },
                                onAdd = { profileId ->
                                    requestProfileOwner(ProfileOwnedCreateTarget.MEASUREMENT, profileId)
                                },
                                onSelected = { profileId, id ->
                                    navigation.navigate(
                                        MeasurementDetailRoute(profileId.toString(), id.toString()),
                                    )
                                },
                            )
                        }
                    }
                    entry<ContactsRoute>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { DetailPlaceholder(R.string.no_contacts_body) },
                        ),
                    ) {
                        EditorBackgroundPane(activeEditorRoute is ContactEditorRoute) {
                            ContactsScreen(
                                contacts = vault.contacts,
                                onAdd = {
                                    navigation.navigate(
                                        ContactEditorRoute(viewModel.createEditorSession()),
                                    )
                                },
                                onSelected = { id -> navigation.navigate(ContactDetailRoute(id.toString())) },
                            )
                        }
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
                            contacts = vault.contacts,
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
                        EditorBackgroundPane(focusedFlowActive) {
                            MedicationsScreen(
                                records = vault.profiles,
                                onAdd = { profileId ->
                                    requestProfileOwner(ProfileOwnedCreateTarget.MEDICATION, profileId)
                                },
                                onSelected = { profileId, id ->
                                    navigation.navigate(MedicationDetailRoute(profileId.toString(), id))
                                },
                            )
                        }
                    }
                    entry<ScheduleRoute>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { DetailPlaceholder(R.string.no_schedules_body) },
                        ),
                    ) {
                        EditorBackgroundPane(focusedFlowActive) {
                            ScheduleScreen(
                                schedules = vault.schedules,
                                now = now,
                                zoneId = zoneId,
                                notificationsBlocked = notificationsBlocked,
                                onAdd = {
                                    navigation.navigate(
                                        ScheduleEditorRoute(viewModel.createEditorSession()),
                                    )
                                },
                                onSelected = { id -> navigation.navigate(ScheduleDetailRoute(id)) },
                                onOpenNotificationSettings = context::openNotificationSettings,
                            )
                        }
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
                            onEdit = {
                                navigation.navigate(
                                    DocumentEditorRoute(
                                        viewModel.createEditorSession(),
                                        record.profile.id.toString(),
                                        document.id.toString(),
                                    ),
                                )
                            },
                            onDelete = {
                                viewModel.deleteDocument(record.profile.id, document.id)
                                navigation.goBack()
                            },
                        )
                    }
                    entry<DocumentImportEditorRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val initialProfileId = runCatching { UUID.fromString(route.profileId) }.getOrNull()
                        if (initialProfileId == null || vault.profiles.none { it.profile.id == initialProfileId }) {
                            MissingRecordScreen { closeEditor(route.sessionId) }
                        } else {
                            DocumentImportEditorScreen(
                                records = vault.profiles,
                                initialProfileId = initialProfileId,
                                today = today,
                                onCancel = { closeEditor(route.sessionId) },
                                onImport = { profileId, draft, onResult ->
                                    viewModel.importDocument(profileId, draft, onResult)
                                },
                            )
                        }
                    }
                    entry<DocumentEditorRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val record = vault.profileRecordOrNull(route.profileId)
                        val document = record?.documents?.firstOrNull { it.id.toString() == route.id }
                        if (record == null || document == null) {
                            MissingRecordScreen { closeEditor(route.sessionId) }
                        } else {
                            DocumentEditorScreen(
                                document = document,
                                record = record,
                                onCancel = { closeEditor(route.sessionId) },
                                onSave = { updated, onResult ->
                                    viewModel.updateDocument(record.profile.id, updated, onResult)
                                },
                            )
                        }
                    }
                    entry<NoteDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val detailBack = listDetailAwareBack(navigation::goBack)
                        val note = vault.notes.firstOrNull { it.id.toString() == route.id }
                        if (note == null) MissingRecordScreen(detailBack) else NoteDetailScreen(
                            note = note,
                            zoneId = zoneId,
                            onBack = detailBack,
                            onEdit = {
                                navigation.navigate(
                                    HealthNoteEditorRoute(
                                        sessionId = viewModel.createEditorSession(),
                                        id = note.id.toString(),
                                    ),
                                )
                            },
                            onDelete = {
                                viewModel.deleteHealthNote(note.id)
                                navigation.goBack()
                            },
                        )
                    }
                    entry<HealthNoteEditorRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val note = route.id?.let { id ->
                            vault.notes.firstOrNull { it.id.toString() == id }
                        }
                        if (route.id != null && note == null) {
                            MissingRecordScreen { closeEditor(route.sessionId) }
                        } else {
                            HealthNoteEditorScreen(
                                existing = note,
                                now = now,
                                zoneId = zoneId,
                                onCancel = { closeEditor(route.sessionId) },
                                onSave = { updated, onResult ->
                                    viewModel.upsertHealthNote(updated, onResult)
                                },
                            )
                        }
                    }
                    entry<MeasurementDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val detailBack = listDetailAwareBack(navigation::goBack)
                        val record = vault.profileRecordOrNull(route.profileId)
                        val measurement = record?.measurements?.firstOrNull { it.id.toString() == route.id }
                        if (record == null || measurement == null) MissingRecordScreen(detailBack) else MeasurementDetailScreen(
                            record = record,
                            measurement = measurement,
                            zoneId = zoneId,
                            onBack = detailBack,
                            onEdit = {
                                navigation.navigate(
                                    MeasurementEditorRoute(
                                        viewModel.createEditorSession(),
                                        record.profile.id.toString(),
                                        measurement.id.toString(),
                                    ),
                                )
                            },
                            onDelete = {
                                viewModel.deleteMeasurement(record.profile.id, measurement.id)
                                navigation.goBack()
                            },
                        )
                    }
                    entry<MeasurementEditorRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val initialProfileId = runCatching { UUID.fromString(route.profileId) }.getOrNull()
                        val owner = vault.profileRecordOrNull(route.profileId)
                        val measurement = route.id?.let { id ->
                            owner?.measurements?.firstOrNull { it.id.toString() == id }
                        }
                        if (initialProfileId == null || owner == null || (route.id != null && measurement == null)) {
                            MissingRecordScreen { closeEditor(route.sessionId) }
                        } else {
                            MeasurementEditorScreen(
                                records = vault.profiles,
                                existingOwner = owner,
                                existing = measurement,
                                initialProfileId = initialProfileId,
                                now = now,
                                zoneId = zoneId,
                                onCancel = { closeEditor(route.sessionId) },
                                onSave = { profileId, updated, onResult ->
                                    viewModel.upsertMeasurement(profileId, updated, onResult)
                                },
                            )
                        }
                    }
                    entry<ContactDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val detailBack = listDetailAwareBack(navigation::goBack)
                        val contact = vault.contacts.firstOrNull { it.id.toString() == route.id }
                        if (contact == null) MissingRecordScreen(detailBack) else ContactDetailScreen(
                            contact = contact,
                            onBack = detailBack,
                            onEdit = {
                                navigation.navigate(
                                    ContactEditorRoute(
                                        sessionId = viewModel.createEditorSession(),
                                        id = contact.id.toString(),
                                    ),
                                )
                            },
                            onDelete = {
                                viewModel.deleteContact(contact.id)
                                navigation.goBack()
                            },
                            onDialPhone = { context.launchContactAction(
                                Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", it, null)),
                                viewModel::showContactActionUnavailable,
                            ) },
                            onComposeEmail = { context.launchContactAction(
                                Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", it, null)),
                                viewModel::showContactActionUnavailable,
                            ) },
                            onOpenWebsite = { context.launchContactAction(
                                Intent(Intent.ACTION_VIEW, Uri.parse(it)),
                                viewModel::showContactActionUnavailable,
                            ) },
                            onSearchAddress = { context.launchContactAction(
                                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(it)}")),
                                viewModel::showContactActionUnavailable,
                            ) },
                        )
                    }
                    entry<ContactEditorRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val contact = route.id?.let { id ->
                            vault.contacts.firstOrNull { it.id.toString() == id }
                        }
                        if (route.id != null && contact == null) {
                            MissingRecordScreen { closeEditor(route.sessionId) }
                        } else {
                            ContactEditorScreen(
                                existing = contact,
                                onCancel = { closeEditor(route.sessionId) },
                                onSave = { updated, onResult ->
                                    viewModel.upsertContact(updated, onResult)
                                },
                            )
                        }
                    }
                    entry<EmergencyContactDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val detailBack = listDetailAwareBack(navigation::goBack)
                        val record = vault.profileRecordOrNull(route.profileId)
                        val contact = record?.profile?.emergencyContacts?.firstOrNull { it.id.toString() == route.id }
                        if (record == null || contact == null) MissingRecordScreen(detailBack) else EmergencyContactDetailScreen(
                            record = record,
                            contact = contact,
                            onBack = detailBack,
                            onEdit = {
                                navigation.navigate(EmergencyContactEditorRoute(
                                    viewModel.createEditorSession(), route.profileId, route.id,
                                ))
                            },
                        )
                    }
                    entry<VaccinationDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val detailBack = listDetailAwareBack(navigation::goBack)
                        val record = vault.profileRecordOrNull(route.profileId)
                        val vaccination = record?.vaccinations?.firstOrNull { it.id.toString() == route.id }
                        if (record == null || vaccination == null) MissingRecordScreen(detailBack) else VaccinationDetailScreen(
                            record = record,
                            vaccination = vaccination,
                            onBack = detailBack,
                            onEdit = {
                                navigation.navigate(VaccinationEditorRoute(
                                    viewModel.createEditorSession(), route.profileId, route.id,
                                ))
                            },
                        )
                    }
                    entry<FamilyHistoryDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val detailBack = listDetailAwareBack(navigation::goBack)
                        val record = vault.profileRecordOrNull(route.profileId)
                        val history = record?.familyHistory?.firstOrNull { it.id.toString() == route.id }
                        if (record == null || history == null) MissingRecordScreen(detailBack) else FamilyHistoryDetailScreen(
                            record = record,
                            entry = history,
                            onBack = detailBack,
                            onEdit = {
                                navigation.navigate(FamilyHistoryEditorRoute(
                                    viewModel.createEditorSession(), route.profileId, route.id,
                                ))
                            },
                        )
                    }
                    entry<CareDirectiveDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val detailBack = listDetailAwareBack(navigation::goBack)
                        val record = vault.profileRecordOrNull(route.profileId)
                        val careDirective = record?.directives?.firstOrNull { it.id.toString() == route.id }
                        if (record == null || careDirective == null) MissingRecordScreen(detailBack) else CareDirectiveDetailScreen(
                            record = record,
                            directive = careDirective,
                            onBack = detailBack,
                            onEdit = {
                                navigation.navigate(CareDirectiveEditorRoute(
                                    viewModel.createEditorSession(), route.profileId, route.id,
                                ))
                            },
                        )
                    }
                    entry<HealthIdentifierDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val detailBack = listDetailAwareBack(navigation::goBack)
                        val record = vault.profileRecordOrNull(route.profileId)
                        val identifier = record?.healthIdentifiers?.firstOrNull { it.id.toString() == route.id }
                        if (record == null || identifier == null) MissingRecordScreen(detailBack) else HealthIdentifierDetailScreen(
                            record = record,
                            identifier = identifier,
                            onBack = detailBack,
                            onEdit = {
                                navigation.navigate(HealthIdentifierEditorRoute(
                                    viewModel.createEditorSession(), route.profileId, route.id,
                                ))
                            },
                        )
                    }
                    entry<HealthProfileEditorRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val initialProfileId = runCatching { UUID.fromString(route.profileId) }.getOrNull()
                        val record = vault.profileRecordOrNull(route.profileId)
                        if (initialProfileId == null || record == null) {
                            MissingRecordScreen { closeEditor(route.sessionId) }
                        } else {
                            HealthProfileEditorScreen(
                                records = vault.profiles,
                                initialProfileId = initialProfileId,
                                onCancel = { closeEditor(route.sessionId) },
                                onSave = { profileId, profile, onResult ->
                                    viewModel.updateProfile(profileId, profile, onResult)
                                },
                            )
                        }
                    }
                    entry<EmergencyContactEditorRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val record = vault.profileRecordOrNull(route.profileId)
                        val contact = route.id?.let { id ->
                            record?.profile?.emergencyContacts?.firstOrNull { it.id.toString() == id }
                        }
                        if (record == null || (route.id != null && contact == null)) {
                            MissingRecordScreen { closeEditor(route.sessionId) }
                        } else {
                            EmergencyContactEditorScreen(
                                existing = contact,
                                onCancel = { closeEditor(route.sessionId) },
                                onSave = { updated, onResult ->
                                    viewModel.upsertEmergencyContact(record.profile.id, updated, onResult)
                                },
                                onDelete = contact?.let {
                                    { onResult ->
                                        viewModel.deleteEmergencyContact(
                                            record.profile.id,
                                            it.id,
                                        ) { succeeded ->
                                            onResult(succeeded)
                                            val detail = navigation.currentBackStack.lastOrNull()
                                                as? EmergencyContactDetailRoute
                                            if (succeeded && detail?.let {
                                                it.profileId == route.profileId && it.id == route.id
                                            } == true) navigation.goBack()
                                        }
                                    }
                                },
                            )
                        }
                    }
                    entry<VaccinationEditorRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val record = vault.profileRecordOrNull(route.profileId)
                        val vaccination = route.id?.let { id ->
                            record?.vaccinations?.firstOrNull { it.id.toString() == id }
                        }
                        if (record == null || (route.id != null && vaccination == null)) {
                            MissingRecordScreen { closeEditor(route.sessionId) }
                        } else {
                            VaccinationEditorScreen(
                                existing = vaccination,
                                today = today,
                                onCancel = { closeEditor(route.sessionId) },
                                onSave = { updated, onResult ->
                                    viewModel.upsertVaccination(record.profile.id, updated, onResult)
                                },
                                onDelete = vaccination?.let {
                                    { onResult ->
                                        viewModel.deleteVaccination(record.profile.id, it.id) { succeeded ->
                                            onResult(succeeded)
                                            val detail = navigation.currentBackStack.lastOrNull()
                                                as? VaccinationDetailRoute
                                            if (succeeded && detail?.let {
                                                it.profileId == route.profileId && it.id == route.id
                                            } == true) navigation.goBack()
                                        }
                                    }
                                },
                            )
                        }
                    }
                    entry<FamilyHistoryEditorRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val record = vault.profileRecordOrNull(route.profileId)
                        val history = route.id?.let { id ->
                            record?.familyHistory?.firstOrNull { it.id.toString() == id }
                        }
                        if (record == null || (route.id != null && history == null)) {
                            MissingRecordScreen { closeEditor(route.sessionId) }
                        } else {
                            FamilyHistoryEditorScreen(
                                existing = history,
                                onCancel = { closeEditor(route.sessionId) },
                                onSave = { updated, onResult ->
                                    viewModel.upsertFamilyHistoryEntry(record.profile.id, updated, onResult)
                                },
                                onDelete = history?.let {
                                    { onResult ->
                                        viewModel.deleteFamilyHistoryEntry(record.profile.id, it.id) { succeeded ->
                                            onResult(succeeded)
                                            val detail = navigation.currentBackStack.lastOrNull()
                                                as? FamilyHistoryDetailRoute
                                            if (succeeded && detail?.let {
                                                it.profileId == route.profileId && it.id == route.id
                                            } == true) navigation.goBack()
                                        }
                                    }
                                },
                            )
                        }
                    }
                    entry<CareDirectiveEditorRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val record = vault.profileRecordOrNull(route.profileId)
                        val directive = route.id?.let { id ->
                            record?.directives?.firstOrNull { it.id.toString() == id }
                        }
                        if (record == null || (route.id != null && directive == null)) {
                            MissingRecordScreen { closeEditor(route.sessionId) }
                        } else {
                            CareDirectiveEditorScreen(
                                existing = directive,
                                today = today,
                                documents = record.documents,
                                onCancel = { closeEditor(route.sessionId) },
                                onSave = { updated, onResult ->
                                    viewModel.upsertCareDirective(record.profile.id, updated, onResult)
                                },
                                onDelete = directive?.let {
                                    { onResult ->
                                        viewModel.deleteCareDirective(record.profile.id, it.id) { succeeded ->
                                            onResult(succeeded)
                                            val detail = navigation.currentBackStack.lastOrNull()
                                                as? CareDirectiveDetailRoute
                                            if (succeeded && detail?.let {
                                                it.profileId == route.profileId && it.id == route.id
                                            } == true) navigation.goBack()
                                        }
                                    }
                                },
                            )
                        }
                    }
                    entry<HealthIdentifierEditorRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val record = vault.profileRecordOrNull(route.profileId)
                        val identifier = route.id?.let { id ->
                            record?.healthIdentifiers?.firstOrNull { it.id.toString() == id }
                        }
                        if (record == null || (route.id != null && identifier == null)) {
                            MissingRecordScreen { closeEditor(route.sessionId) }
                        } else {
                            HealthIdentifierEditorScreen(
                                existing = identifier,
                                onCancel = { closeEditor(route.sessionId) },
                                onSave = { updated, onResult ->
                                    viewModel.upsertHealthIdentifier(record.profile.id, updated, onResult)
                                },
                                onDelete = identifier?.let {
                                    { onResult ->
                                        viewModel.deleteHealthIdentifier(record.profile.id, it.id) { succeeded ->
                                            onResult(succeeded)
                                            val detail = navigation.currentBackStack.lastOrNull()
                                                as? HealthIdentifierDetailRoute
                                            if (succeeded && detail?.let {
                                                it.profileId == route.profileId && it.id == route.id
                                            } == true) navigation.goBack()
                                        }
                                    }
                                },
                            )
                        }
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
                            profile = record.profile,
                            onBack = detailBack,
                            onEdit = {
                                navigation.navigate(
                                    MedicationEditorRoute(
                                        viewModel.createEditorSession(),
                                        record.profile.id.toString(),
                                        medication.id.toString(),
                                    ),
                                )
                            },
                            onDelete = {
                                viewModel.deleteMedication(record.profile.id, medication.id)
                                navigation.goBack()
                            },
                        )
                    }
                    entry<MedicationEditorRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val initialProfileId = runCatching { UUID.fromString(route.profileId) }.getOrNull()
                        val owner = vault.profileRecordOrNull(route.profileId)
                        val medication = route.id?.let { id ->
                            owner?.medications?.firstOrNull { it.id.toString() == id }
                        }
                        if (initialProfileId == null || owner == null || (route.id != null && medication == null)) {
                            MissingRecordScreen { closeEditor(route.sessionId) }
                        } else {
                            MedicationEditorScreen(
                                records = vault.profiles,
                                existingOwner = owner,
                                existing = medication,
                                initialProfileId = initialProfileId,
                                today = today,
                                onCancel = { closeEditor(route.sessionId) },
                                onSave = { profileId, updated, onResult ->
                                    withNotificationPermission(updated.remindersEnabled) {
                                        viewModel.upsertMedication(profileId, updated, onResult)
                                    }
                                },
                            )
                        }
                    }
                    entry<ScheduleDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val detailBack = listDetailAwareBack(navigation::goBack)
                        val schedule = vault.schedules.firstOrNull { it.id.toString() == route.id }
                        if (schedule == null) MissingRecordScreen(detailBack) else ScheduleDetailScreen(
                            schedule = schedule,
                            zoneId = zoneId,
                            onBack = detailBack,
                            onEdit = {
                                navigation.navigate(
                                    ScheduleEditorRoute(
                                        viewModel.createEditorSession(),
                                        schedule.id.toString(),
                                    ),
                                )
                            },
                            onDelete = {
                                viewModel.deleteSchedule(schedule.id)
                                navigation.goBack()
                            },
                        )
                    }
                    entry<ScheduleEditorRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val schedule = route.id?.let { id ->
                            vault.schedules.firstOrNull { it.id.toString() == id }
                        }
                        if (route.id != null && schedule == null) {
                            MissingRecordScreen { closeEditor(route.sessionId) }
                        } else {
                            ScheduleEditorScreen(
                                existing = schedule,
                                profileNames = vault.profiles.map { it.profile.displayName },
                                today = today,
                                zoneId = zoneId,
                                onCancel = { closeEditor(route.sessionId) },
                                onSave = { updated, onResult ->
                                    withNotificationPermission(updated.alert != null) {
                                        viewModel.upsertSchedule(updated, onResult)
                                    }
                                },
                            )
                        }
                    }
                    entry<ProfileOwnerGateRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                        val proposedProfileId = runCatching { UUID.fromString(route.proposedProfileId) }.getOrNull()
                        if (proposedProfileId == null) {
                            MissingRecordScreen(navigation::goBack)
                        } else {
                            ProfileOwnerGateScreen(
                                profiles = vault.profiles,
                                proposedProfileId = proposedProfileId,
                                onBack = navigation::goBack,
                                onProfileSelected = { selected ->
                                    continueProfileOwnedCreate(route.target, selected, replaceGate = true)
                                },
                                onCreateProfile = { name, id, onResult ->
                                    viewModel.addProfile(
                                        displayName = name,
                                        profileId = id,
                                        onResult = onResult,
                                    )
                                },
                            )
                        }
                    }
                    entry<SettingsRoute> {
                        SettingsScreen(
                            settings = settings,
                            zoneId = zoneId,
                            restorePreview = restorePreview,
                            message = null,
                            onThemeChanged = viewModel::setThemeMode,
                            onOpacityChanged = viewModel::previewFloatingSurfaceOpacityLevel,
                            onOpacityChangeFinished = { viewModel.saveFloatingSurfaceOpacityLevel() },
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
                            restoreRequestId = restoreRequestId,
                            onRestoreRequestHandled = { restoreRequestId = 0L },
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
        is HealthSearchTarget.Contact -> ContactDetailRoute(selected.id.toString())
        is HealthSearchTarget.FamilyHistory -> FamilyHistoryDetailRoute(requireProfileId(), selected.id.toString())
        is HealthSearchTarget.Directive -> CareDirectiveDetailRoute(requireProfileId(), selected.id.toString())
        is HealthSearchTarget.Identifier -> HealthIdentifierDetailRoute(requireProfileId(), selected.id.toString())
    }
}

@Composable
internal fun FreshRestorePrompt(onRestore: () -> Unit) {
    SectionCard(stringResource(R.string.fresh_restore_title)) {
        Text(stringResource(R.string.fresh_restore_body))
        Button(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.restore_backup))
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
    AndroidKitPage(
        title = stringResource(R.string.record_unavailable),
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .withPagePadding(),
        ) {
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

private fun Context.launchContactAction(intent: Intent, onUnavailable: () -> Unit) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        onUnavailable()
    }
}

private data class LocaleTransitionRequest(val id: Long, val localeTag: String)

private data class LocalizedContentFrame(val requestId: Long, val localeTag: String)

private fun HealthVault.profileRecordOrNull(profileId: String): ProfileRecord? =
    runCatching { UUID.fromString(profileId) }.getOrNull()?.let { id ->
        profiles.firstOrNull { it.profile.id == id }
    }

private const val NOTICE_DURATION_MILLIS = 4_000L
