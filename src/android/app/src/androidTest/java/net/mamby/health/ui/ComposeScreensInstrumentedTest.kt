package net.mamby.health.ui

import android.content.res.Configuration
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.WindowInsets
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import net.mamby.health.MainActivity
import net.mamby.health.R
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.Schedule
import net.mamby.health.core.model.ScheduleTiming
import net.mamby.health.feature.dashboard.DashboardScreen
import net.mamby.health.feature.schedule.ScheduleScreen
import net.mamby.health.feature.search.SearchFilter
import net.mamby.health.feature.search.SearchScreen
import net.mamby.health.feature.settings.SettingsScreen
import net.mamby.health.feature.vault.VaultScreen
import net.mamby.health.navigation.AppNavigationState
import net.mamby.health.navigation.DirectoryEntryDetailRoute
import net.mamby.health.navigation.DirectoryRoute
import net.mamby.health.navigation.DocumentDetailRoute
import net.mamby.health.navigation.HealthRecordsRoute
import net.mamby.health.navigation.HomeRoute
import net.mamby.health.navigation.ManageProfilesRoute
import net.mamby.health.navigation.MedicationDetailRoute
import net.mamby.health.navigation.NoteDetailRoute
import net.mamby.health.navigation.NotesRoute
import net.mamby.health.navigation.TopLevelDestination
import net.mamby.health.navigation.rememberAppNavigationState
import net.mamby.health.settings.AppSettings
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.AppMoreSheet
import net.mamby.health.ui.components.AppNavigationSuite
import net.mamby.health.ui.components.RemovableInputChip
import net.mamby.health.ui.components.ProfileFilterChip
import net.mamby.health.ui.components.SwitchField
import net.mamby.health.ui.components.appContentWindowInsets
import net.mamby.health.ui.components.listDetailAwareBack
import net.mamby.health.ui.components.withScreenPadding
import net.mamby.health.ui.theme.HealthVaultTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3AdaptiveApi::class)
@RunWith(AndroidJUnit4::class)
class ComposeScreensInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyProfileShowsGettingStartedActionsWithoutSampleContent() {
        composeRule.setContent {
            HealthVaultTheme {
                DashboardScreen(
                    records = emptyVault("Amina").profiles,
                    notes = emptyList(),
                    schedules = emptyList(),
                    clock = FIXED_CLOCK,
                    zoneId = ZoneOffset.UTC,
                    onMedications = {},
                    onSchedule = {},
                    onDocumentSelected = { _, _ -> },
                    onNoteSelected = {},
                    onScheduleSelected = {},
                    onAddHealthInfo = {},
                    onImportDocument = {},
                    onAddMedication = {},
                    onAddSchedule = {},
                )
            }
        }

        listOf(
            R.string.no_scheduled_medication,
            R.string.no_upcoming_schedule,
            R.string.getting_started_title,
            R.string.add_medication,
        ).forEach { stringId ->
            composeRule
                .onNodeWithText(composeRule.activity.getString(stringId))
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    @Test
    fun homePreviewCardsOpenTheirRootDestinations() {
        var medicationsOpened = false
        var scheduleOpened = false
        composeRule.setContent {
            HealthVaultTheme {
                DashboardScreen(
                    records = emptyVault("Amina").profiles,
                    notes = emptyList(),
                    schedules = emptyList(),
                    clock = FIXED_CLOCK,
                    zoneId = ZoneOffset.UTC,
                    onMedications = { medicationsOpened = true },
                    onSchedule = { scheduleOpened = true },
                    onDocumentSelected = { _, _ -> },
                    onNoteSelected = {},
                    onScheduleSelected = {},
                    onAddHealthInfo = {},
                    onImportDocument = {},
                    onAddMedication = {},
                    onAddSchedule = {},
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.nav_medications)).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.schedule_title)).performClick()
        composeRule.runOnIdle {
            assertTrue(medicationsOpened)
            assertTrue(scheduleOpened)
        }
    }

    @Test
    fun scheduleIsVaultScopedAndUsesProfileNamesOnlyAsPeopleSuggestions() {
        val schedule = Schedule(
            id = UUID.randomUUID(),
            title = "School meeting",
            timing = ScheduleTiming.InstantTimed(FIXED_CLOCK.instant().plusSeconds(3_600)),
            people = listOf("Guest"),
            updatedAt = FIXED_CLOCK.instant(),
        )
        composeRule.setContent {
            HealthVaultTheme {
                ScheduleScreen(
                    schedules = listOf(schedule),
                    profileNames = listOf("Amina"),
                    today = LocalDate.of(2026, 7, 30),
                    now = FIXED_CLOCK.instant(),
                    zoneId = ZoneOffset.UTC,
                    notificationsBlocked = false,
                    onUpsert = {},
                    onSelected = {},
                    onOpenNotificationSettings = {},
                )
            }
        }

        composeRule.onNodeWithText("School meeting").assertIsDisplayed()
        composeRule.onAllNodesWithText(composeRule.activity.getString(R.string.all_profiles)).assertCountEquals(0)
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.add_schedule)).performClick()
        composeRule.onNodeWithText("Amina").assertExists()
        composeRule.onAllNodesWithText(composeRule.activity.getString(R.string.choose_profile)).assertCountEquals(0)
    }

    @Test
    fun emptyDocumentsDefaultsToAllProfilesAndShowsEmptyState() {
        val record = emptyVault("Amina").profiles.single()
        composeRule.setContent {
            HealthVaultTheme {
                VaultScreen(
                    records = listOf(record),
                    today = LocalDate.of(2026, 7, 30),
                    onBack = {},
                    onManageCategories = {},
                    onAddProfile = { _, _ -> },
                    onImport = { _, _ -> },
                    onDocumentSelected = { _, _ -> },
                )
            }
        }

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.all_profiles))
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.documents_tab)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.no_documents_title)).assertIsDisplayed()
    }

    @Test
    fun rtlLayoutPlacesBackNavigationOnVisualRight() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                HealthVaultTheme {
                    AppScreenScaffold(title = "RTL", onBack = {}) { padding ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(16.dp),
                        ) {
                            PageHeader()
                        }
                    }
                }
            }
        }

        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val back = composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.action_back))
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(back.center.x > root.center.x)
    }

    @Test
    fun pageTitleScrollsAwayWhileFloatingBackRemainsFixedAndClickable() {
        var backClicks = 0
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(400.dp, 800.dp))) {
                HealthVaultTheme {
                    AppScreenScaffold(
                        title = "Scrolling title",
                        onBack = { backClicks += 1 },
                    ) { padding ->
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .consumeWindowInsets(padding)
                                .testTag(SCROLLING_PAGE_TAG),
                            contentPadding = padding.withScreenPadding(),
                        ) {
                            item { PageHeader() }
                            item {
                                ProfileFilterChip(
                                    label = "All profiles",
                                    profileId = null,
                                    onClick = {},
                                    accessibleLabel = "Page profile filter",
                                )
                            }
                            items((0 until 60).toList()) { index ->
                                Text(
                                    text = "Row $index",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("Scrolling title").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Page profile filter").assertIsDisplayed()
        val back = composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.action_back),
        )
        val initialBackBounds = back.assertIsDisplayed().fetchSemanticsNode().boundsInRoot

        val list = composeRule.onNodeWithTag(SCROLLING_PAGE_TAG)
        list.performScrollToIndex(30)
        composeRule.onNodeWithText("Scrolling title").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Page profile filter").assertDoesNotExist()
        assertEquals(initialBackBounds, back.fetchSemanticsNode().boundsInRoot)

        list.performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, 50f),
                durationMillis = 100,
            )
        }
        composeRule.onNodeWithText("Scrolling title").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Page profile filter").assertDoesNotExist()
        back.performClick()
        composeRule.runOnIdle { assertEquals(1, backClicks) }

        list.performScrollToIndex(0)
        composeRule.onNodeWithText("Scrolling title").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Page profile filter").assertIsDisplayed()
    }

    @Test
    fun settingsRootHasNoVisualBackWhileRecoveryPresentationRetainsIt() {
        lateinit var showRecovery: () -> Unit
        composeRule.setContent {
            var recovery by remember { mutableStateOf(false) }
            showRecovery = { recovery = true }
            HealthVaultTheme {
                TestSettingsScreen(onBack = if (recovery) ({}) else null)
            }
        }

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.action_back))
            .assertDoesNotExist()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.settings_title)).assertIsDisplayed()

        composeRule.runOnIdle(showRecovery)
        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.action_back))
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun compactDetailSceneShowsVisualBack() {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(400.dp, 800.dp))) {
                HealthVaultTheme { ListDetailBackHarness() }
            }
        }

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.action_back))
            .assertIsDisplayed()
    }

    @Test
    fun expandedListDetailSceneSuppressesVisualBack() {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(900.dp, 1_000.dp))) {
                HealthVaultTheme { ListDetailBackHarness() }
            }
        }

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.action_back))
            .assertDoesNotExist()
    }

    @Test
    fun standaloneWideSubpageKeepsVisualBack() {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(900.dp, 1_000.dp))) {
                HealthVaultTheme {
                    AppScreenScaffold(
                        title = "Standalone detail",
                        onBack = listDetailAwareBack {},
                    ) { padding ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(16.dp),
                        ) {
                            PageHeader()
                        }
                    }
                }
            }
        }

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.action_back))
            .assertIsDisplayed()
    }

    @Test
    fun expandedWindowKeepsDashboardMetricsAccessible() {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(900.dp, 1_000.dp))) {
                HealthVaultTheme {
                    DashboardScreen(
                        records = emptyVault("Expanded").profiles,
                        notes = emptyList(),
                        schedules = emptyList(),
                        clock = FIXED_CLOCK,
                        zoneId = ZoneOffset.UTC,
                        onMedications = {},
                        onSchedule = {},
                        onDocumentSelected = { _, _ -> },
                        onNoteSelected = {},
                        onScheduleSelected = {},
                        onAddHealthInfo = {},
                        onImportDocument = {},
                        onAddMedication = {},
                        onAddSchedule = {},
                    )
                }
            }
        }

        listOf(R.string.documents_metric, R.string.medications_metric, R.string.schedules_metric)
            .forEach { composeRule.onNodeWithText(composeRule.activity.getString(it)).assertIsDisplayed() }
    }

    @Test
    fun navigationKeepsIndependentBackStacksAndProfilesAsTopLevel() {
        assertEquals(
            listOf(
                "Home",
                "Search",
                "HealthRecords",
                "Notes",
                "Medications",
                "Schedule",
                "Directory",
                "Settings",
                "Profiles",
            ),
            TopLevelDestination.entries.map(Enum<*>::name),
        )
        lateinit var navigation: AppNavigationState
        composeRule.setContent { navigation = rememberAppNavigationState() }
        val profileId = PROFILE_ID.toString()

        composeRule.runOnIdle {
            navigation.navigate(
                TopLevelDestination.HealthRecords,
                DocumentDetailRoute(profileId, "document-id"),
            )
            navigation.navigate(
                TopLevelDestination.Medications,
                MedicationDetailRoute(profileId, "medication-id"),
            )
            navigation.navigate(TopLevelDestination.Notes, NoteDetailRoute("note-id"))
            navigation.navigate(
                TopLevelDestination.Directory,
                DirectoryEntryDetailRoute(profileId, "directory-id"),
            )
            navigation.select(TopLevelDestination.HealthRecords)
            assertEquals(DocumentDetailRoute(profileId, "document-id"), navigation.currentBackStack.last())
            navigation.goBack()
            assertEquals(HealthRecordsRoute, navigation.currentBackStack.last())
            assertTrue(navigation.isAtSecondaryRoot)
            navigation.select(TopLevelDestination.Notes)
            assertEquals(NoteDetailRoute("note-id"), navigation.currentBackStack.last())
            navigation.goBack()
            assertEquals(NotesRoute, navigation.currentBackStack.last())
            navigation.select(TopLevelDestination.Directory)
            assertEquals(
                DirectoryEntryDetailRoute(profileId, "directory-id"),
                navigation.currentBackStack.last(),
            )
            navigation.goBack()
            assertEquals(DirectoryRoute, navigation.currentBackStack.last())
            navigation.select(TopLevelDestination.Profiles)
            assertEquals(ManageProfilesRoute, navigation.currentBackStack.last())
            assertTrue(navigation.isAtSecondaryRoot)
            navigation.goBack()
            assertEquals(TopLevelDestination.Home, navigation.selectedDestination)
            assertEquals(HomeRoute, navigation.currentBackStack.last())
        }
    }

    @Test
    fun compactNavigationUsesFourRootsAndMore() {
        assertEquals(
            listOf(
                TopLevelDestination.Medications,
                TopLevelDestination.Schedule,
                TopLevelDestination.Directory,
                TopLevelDestination.Settings,
                TopLevelDestination.Profiles,
            ),
            TopLevelDestination.compactOverflow,
        )
        var moreSelected = false
        composeRule.setContent {
            HealthVaultTheme {
                AppNavigationSuite(
                    selectedDestination = TopLevelDestination.Home,
                    layoutType = NavigationSuiteType.ShortNavigationBarCompact,
                    isMoreSelected = false,
                    onDestinationSelected = {},
                    onMoreSelected = { moreSelected = true },
                ) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }

        TopLevelDestination.compactPrimary.forEach { destination ->
            val label = composeRule.activity.getString(destination.label)
            val node = composeRule
                .onNodeWithContentDescription(label, useUnmergedTree = true)
                .assertIsDisplayed()
                .fetchSemanticsNode()
            assertEquals(Role.Tab, node.config[SemanticsProperties.Role])
            composeRule.onAllNodesWithText(label).assertCountEquals(0)
        }
        TopLevelDestination.compactOverflow.forEach { destination ->
            composeRule
                .onNodeWithContentDescription(composeRule.activity.getString(destination.label))
                .assertDoesNotExist()
        }
        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.action_more))
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertTrue(moreSelected) }
    }

    @Test
    fun compactNavigationOverlaysFullHeightContent() {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(400.dp, 800.dp))) {
                HealthVaultTheme {
                    AppNavigationSuite(
                        selectedDestination = TopLevelDestination.Home,
                        layoutType = NavigationSuiteType.ShortNavigationBarCompact,
                        isMoreSelected = false,
                        onDestinationSelected = {},
                        onMoreSelected = {},
                    ) {
                        Box(Modifier.fillMaxSize().testTag(NAVIGATION_CONTENT_TAG))
                    }
                }
            }
        }

        val rootBounds = composeRule
            .onAllNodes(isRoot())
            .fetchSemanticsNodes()
            .maxBy { it.boundsInRoot.width * it.boundsInRoot.height }
            .boundsInRoot
        val contentBounds = composeRule
            .onNodeWithTag(NAVIGATION_CONTENT_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val homeIconBounds = composeRule
            .onNodeWithContentDescription(
                composeRule.activity.getString(R.string.nav_home),
                useUnmergedTree = true,
            )
            .fetchSemanticsNode()
            .boundsInRoot

        assertEquals(rootBounds.bottom, contentBounds.bottom, 0.5f)
        assertTrue(contentBounds.bottom > homeIconBounds.bottom)
    }

    @Test
    fun compactNavigationBarCompositesOverUnderlyingPixels() {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(400.dp, 800.dp))) {
                HealthVaultTheme(darkTheme = false) {
                    AppNavigationSuite(
                        selectedDestination = TopLevelDestination.Home,
                        layoutType = NavigationSuiteType.ShortNavigationBarCompact,
                        isMoreSelected = false,
                        onDestinationSelected = {},
                        onMoreSelected = {},
                    ) {
                        Row(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f).fillMaxSize().background(Color.Red))
                            Box(Modifier.weight(1f).fillMaxSize().background(Color.Blue))
                        }
                    }
                }
            }
        }

        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        val sampleY = pixels.height - 2
        val overRed = pixels[2, sampleY]
        val overBlue = pixels[pixels.width - 3, sampleY]

        assertTrue(overRed.green > 0.5f && overBlue.green > 0.5f)
        assertTrue(overRed.red > overBlue.red + 0.1f)
        assertTrue(overBlue.blue > overRed.blue + 0.1f)
    }

    @Test
    fun finalListItemAndFloatingActionClearCompactNavigationBar() {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(400.dp, 800.dp))) {
                HealthVaultTheme {
                    AppNavigationSuite(
                        selectedDestination = TopLevelDestination.Home,
                        layoutType = NavigationSuiteType.ShortNavigationBarCompact,
                        isMoreSelected = false,
                        onDestinationSelected = {},
                        onMoreSelected = {},
                    ) {
                        AppScreenScaffold(
                            title = "Clearance",
                            floatingActionButton = {
                                FloatingActionButton(
                                    onClick = {},
                                    modifier = Modifier.testTag(FLOATING_ACTION_TAG),
                                ) { Text("+") }
                            },
                        ) { padding ->
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .consumeWindowInsets(padding)
                                    .testTag(CLEARANCE_LIST_TAG),
                                contentPadding = padding.withScreenPadding(),
                            ) {
                                item { PageHeader() }
                                items((0 until 30).toList()) { index ->
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .then(
                                                if (index == 29) {
                                                    Modifier.testTag(FINAL_ITEM_TAG)
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag(CLEARANCE_LIST_TAG).performScrollToIndex(30)
        val finalItemBounds = composeRule.onNodeWithTag(FINAL_ITEM_TAG).fetchSemanticsNode().boundsInRoot
        val floatingActionBounds = composeRule
            .onNodeWithTag(FLOATING_ACTION_TAG)
            .fetchSemanticsNode()
            .boundsInRoot
        val homeIconBounds = composeRule
            .onNodeWithContentDescription(
                composeRule.activity.getString(R.string.nav_home),
                useUnmergedTree = true,
            )
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(finalItemBounds.bottom <= floatingActionBounds.top)
        assertTrue(floatingActionBounds.bottom < homeIconBounds.top)
    }

    @Test
    fun rootSnackbarClearsCompactNavigationBar() {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(400.dp, 800.dp))) {
                HealthVaultTheme {
                    AppNavigationSuite(
                        selectedDestination = TopLevelDestination.Home,
                        layoutType = NavigationSuiteType.ShortNavigationBarCompact,
                        isMoreSelected = false,
                        onDestinationSelected = {},
                        onMoreSelected = {},
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            Snackbar(
                                modifier = Modifier
                                    .align(androidx.compose.ui.Alignment.BottomCenter)
                                    .windowInsetsPadding(appContentWindowInsets())
                                    .padding(16.dp)
                                    .testTag(SNACKBAR_TAG),
                            ) { Text("Notice") }
                        }
                    }
                }
            }
        }

        val snackbarBounds = composeRule.onNodeWithTag(SNACKBAR_TAG).fetchSemanticsNode().boundsInRoot
        val homeIconBounds = composeRule
            .onNodeWithContentDescription(
                composeRule.activity.getString(R.string.nav_home),
                useUnmergedTree = true,
            )
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(snackbarBounds.bottom < homeIconBounds.top)
    }

    @Test
    fun searchViewportFitsInsideImeInsetAndRemainsEditable() {
        var submittedQuery = ""
        var imeBottomPx = 0
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(400.dp, 800.dp))) {
                val imeBottom = with(LocalDensity.current) { 300.dp.roundToPx() }
                imeBottomPx = imeBottom
                DeviceConfigurationOverride(
                    DeviceConfigurationOverride.WindowInsets(
                        WindowInsetsCompat.Builder()
                            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imeBottom))
                            .setVisible(WindowInsetsCompat.Type.ime(), true)
                            .build(),
                    ),
                    ) {
                    HealthVaultTheme {
                        var query by remember { mutableStateOf("") }
                        SearchScreen(
                            records = emptyVault("Amina").profiles,
                            notes = emptyList(),
                            schedules = emptyList(),
                            onResultSelected = {},
                            query = query,
                            filter = SearchFilter.ALL,
                            onQueryChanged = {
                                query = it
                                submittedQuery = it
                            },
                            onFilterChanged = {},
                        )
                    }
                }
            }
        }

        val rootBounds = composeRule
            .onAllNodes(isRoot())
            .fetchSemanticsNodes()
            .maxBy { it.boundsInRoot.width * it.boundsInRoot.height }
            .boundsInRoot
        val searchField = composeRule
            .onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsActions.SetText),
                useUnmergedTree = true,
            )[0]
        val searchFieldBounds = searchField.fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Search field $searchFieldBounds does not clear the IME in root $rootBounds",
            searchFieldBounds.bottom <= rootBounds.bottom - imeBottomPx,
        )
        searchField.performTextInput("A")
        composeRule.runOnIdle { assertEquals("A", submittedQuery) }
    }

    @Test
    fun compactNavigationSelectsOnlyMoreForMoreDestinations() {
        composeRule.setContent {
            HealthVaultTheme {
                AppNavigationSuite(
                    selectedDestination = TopLevelDestination.Profiles,
                    layoutType = NavigationSuiteType.ShortNavigationBarCompact,
                    isMoreSelected = true,
                    onDestinationSelected = {},
                    onMoreSelected = {},
                ) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }

        composeRule
            .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assertCountEquals(1)
    }

    @Test
    fun expandedNavigationShowsAllRootsDirectly() {
        composeRule.setContent {
            HealthVaultTheme {
                AppNavigationSuite(
                    selectedDestination = TopLevelDestination.Home,
                    layoutType = NavigationSuiteType.NavigationRail,
                    isMoreSelected = false,
                    onDestinationSelected = {},
                    onMoreSelected = {},
                ) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }

        TopLevelDestination.entries.forEach { destination ->
            composeRule
                .onNodeWithContentDescription(composeRule.activity.getString(destination.label))
                .assertIsDisplayed()
        }
        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.action_more))
            .assertDoesNotExist()
    }

    @Test
    fun expandedNavigationRailReservesSpaceBesideContent() {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(900.dp, 1_000.dp))) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    HealthVaultTheme {
                        AppNavigationSuite(
                            selectedDestination = TopLevelDestination.Home,
                            layoutType = NavigationSuiteType.NavigationRail,
                            isMoreSelected = false,
                            onDestinationSelected = {},
                            onMoreSelected = {},
                        ) {
                            Box(Modifier.fillMaxSize().testTag(NAVIGATION_CONTENT_TAG))
                        }
                    }
                }
            }
        }

        val contentBounds = composeRule
            .onNodeWithTag(NAVIGATION_CONTENT_TAG, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        val homeIconBounds = composeRule
            .onNodeWithContentDescription(
                composeRule.activity.getString(R.string.nav_home),
                useUnmergedTree = true,
            )
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(contentBounds.left >= homeIconBounds.right)
    }

    @Test
    fun moreSheetShowsLocalizedDestinationsAndDispatchesActions() {
        var selectedAction: TopLevelDestination? = null
        lateinit var showSheet: () -> Unit
        composeRule.setContent {
            var visible by remember { mutableStateOf(true) }
            showSheet = { visible = true }
            HealthVaultTheme {
                if (visible) {
                    AppMoreSheet(
                        onDismissRequest = { visible = false },
                        onDestinationSelected = {
                            selectedAction = it
                            visible = false
                        },
                    )
                }
            }
        }

        TopLevelDestination.compactOverflow.forEach { destination ->
            composeRule
                .onNodeWithText(composeRule.activity.getString(destination.label))
                .assertIsDisplayed()
        }
        val schedule = composeRule.activity.getString(R.string.schedule_title)
        val settings = composeRule.activity.getString(R.string.settings_title)
        val profiles = composeRule.activity.getString(R.string.profiles_title)
        composeRule.onNodeWithText(schedule).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(TopLevelDestination.Schedule, selectedAction)
            showSheet()
        }
        composeRule.onNodeWithText(settings).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(TopLevelDestination.Settings, selectedAction)
            showSheet()
        }
        composeRule.onNodeWithText(profiles).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(TopLevelDestination.Profiles, selectedAction) }
    }

    @Test
    fun settingsDoesNotExposeProfileManagement() {
        composeRule.setContent {
            HealthVaultTheme { TestSettingsScreen(onBack = null) }
        }

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.profiles_title))
            .assertDoesNotExist()
    }

    @Test
    fun removableInputChipExposesAndPerformsItsRemovalAction() {
        val value = "Penicillin"
        composeRule.setContent {
            var visible by remember { mutableStateOf(true) }
            HealthVaultTheme {
                if (visible) {
                    RemovableInputChip(value, onRemove = { visible = false })
                }
            }
        }

        val node = composeRule.onNodeWithText(value).assertHasClickAction().fetchSemanticsNode()
        assertEquals(
            composeRule.activity.getString(R.string.a11y_delete_item, value),
            node.config[SemanticsActions.OnClick].label,
        )
        composeRule.onNodeWithText(value).performClick().assertDoesNotExist()
    }

    @Test
    fun switchFieldExposesItsLabelStateAndActionAsOneControl() {
        val label = composeRule.activity.getString(R.string.medication_reminders)
        composeRule.setContent {
            var checked by remember { mutableStateOf(false) }
            HealthVaultTheme {
                SwitchField(label, checked, onCheckedChange = { checked = it })
            }
        }

        val switch = composeRule.onNodeWithText(label)
            .assertHasClickAction()
            .assertIsOff()
        assertEquals(Role.Switch, switch.fetchSemanticsNode().config[SemanticsProperties.Role])
        switch.performClick()
        composeRule.onNodeWithText(label).assertIsOn()
    }

    @Test
    fun localeResourcesAndActivitySupportFrenchAndArabicRuntimeSwitching() {
        assertTrue(AppCompatActivity::class.java.isAssignableFrom(MainActivity::class.java))
        val base = composeRule.activity
        val french = Configuration(base.resources.configuration).apply {
            setLocales(LocaleList(Locale.FRENCH))
        }
        val arabic = Configuration(base.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag("ar")))
        }
        val frenchContext = base.createConfigurationContext(french)
        val arabicContext = base.createConfigurationContext(arabic)

        assertEquals("Accueil", frenchContext.getString(R.string.nav_home))
        assertEquals("Paramètres", frenchContext.getString(R.string.settings_title))
        assertEquals("الرئيسية", arabicContext.getString(R.string.nav_home))
        assertEquals(LayoutDirection.Rtl.ordinal, arabicContext.resources.configuration.layoutDirection)
    }

    private fun emptyVault(displayName: String): HealthVault = HealthVault.empty(
        now = FIXED_INSTANT,
        profileId = PROFILE_ID,
        displayName = displayName,
    )

    @Composable
    private fun TestSettingsScreen(onBack: (() -> Unit)?) {
        SettingsScreen(
            settings = AppSettings(),
            zoneId = ZoneOffset.UTC,
            restorePreview = null,
            message = null,
            onBack = onBack,
            onThemeChanged = {},
            onLocaleChanged = {},
            onAppLockChanged = {},
            onAppLockTimeoutChanged = {},
            onLockNow = {},
            onConfigureBackup = { _, _, _ -> },
            onBackupNow = {},
            onClearBackup = {},
            onPrepareRestore = { _, _ -> },
            onCommitRestore = { _, _ -> },
            onDiscardRestore = {},
            onDeleteVault = {},
        )
    }

    @Composable
    private fun ListDetailBackHarness() {
        val adaptiveInfo = currentWindowAdaptiveInfoV2()
        val directive = remember(adaptiveInfo) {
            calculatePaneScaffoldDirective(adaptiveInfo).copy(horizontalPartitionSpacerSize = 0.dp)
        }
        val strategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)
        val backStack = remember {
            mutableStateListOf<NavKey>(NotesRoute, NoteDetailRoute("detail"))
        }

        NavDisplay(
            backStack = backStack,
            onBack = {
                if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
            },
            sceneStrategies = listOf(strategy),
            entryProvider = entryProvider {
                entry<NotesRoute>(
                    metadata = ListDetailSceneStrategy.listPane(
                        detailPlaceholder = { Text("Select a detail") },
                    ),
                ) {
                    Text("List")
                }
                entry<NoteDetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) {
                    AppScreenScaffold(
                        title = "Detail",
                        onBack = listDetailAwareBack {},
                    ) { padding ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(16.dp),
                        ) {
                            PageHeader()
                        }
                    }
                }
            },
        )
    }

    private companion object {
        const val FINAL_ITEM_TAG = "final-item"
        const val CLEARANCE_LIST_TAG = "clearance-list"
        const val FLOATING_ACTION_TAG = "floating-action"
        const val NAVIGATION_CONTENT_TAG = "navigation-content"
        const val SCROLLING_PAGE_TAG = "scrolling-page"
        const val SNACKBAR_TAG = "root-snackbar"
        val PROFILE_ID: UUID = UUID.fromString("44e15584-8158-4bf8-bf26-dd7358f392cf")
        val FIXED_INSTANT: Instant = Instant.parse("2026-07-30T08:00:00Z")
        val FIXED_CLOCK: Clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC)
    }
}
