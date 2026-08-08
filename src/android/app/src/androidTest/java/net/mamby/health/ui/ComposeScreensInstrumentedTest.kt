package net.mamby.health.ui

import android.content.res.Configuration
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.UUID
import net.mamby.health.MainActivity
import net.mamby.health.R
import net.mamby.health.core.model.HealthVault
import net.mamby.health.feature.dashboard.DashboardScreen
import net.mamby.health.feature.vault.VaultScreen
import net.mamby.health.navigation.AppNavigationState
import net.mamby.health.navigation.DocumentDetailRoute
import net.mamby.health.navigation.HealthRecordsRoute
import net.mamby.health.navigation.HomeRoute
import net.mamby.health.navigation.MedicationDetailRoute
import net.mamby.health.navigation.TopLevelDestination
import net.mamby.health.navigation.rememberAppNavigationState
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.AppMoreSheet
import net.mamby.health.ui.components.AppNavigationSuite
import net.mamby.health.ui.components.RemovableInputChip
import net.mamby.health.ui.components.SwitchField
import net.mamby.health.ui.theme.HealthVaultTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ComposeScreensInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun emptyProfileShowsGettingStartedActionsWithoutSampleContent() {
        composeRule.setContent {
            HealthVaultTheme {
                DashboardScreen(
                    record = emptyVault("Amina").profiles.single(),
                    clock = FIXED_CLOCK,
                    zoneId = ZoneOffset.UTC,
                    onProfileClick = {},
                    onReminders = {},
                    onDocumentSelected = {},
                    onAddHealthInfo = {},
                    onImportDocument = {},
                    onAddMedication = {},
                    onAddAppointment = {},
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.getting_started_title)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.add_medication)).assertIsDisplayed()
    }

    @Test
    fun emptyDocumentsHasProfileContextAndImportAction() {
        val record = emptyVault("Amina").profiles.single()
        composeRule.setContent {
            HealthVaultTheme {
                VaultScreen(
                    record = record,
                    today = LocalDate.of(2026, 7, 30),
                    onBack = {},
                    onProfileClick = {},
                    onManageCategories = {},
                    onImport = {},
                    onDocumentSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("Amina").assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.documents_tab)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.no_documents_title)).assertIsDisplayed()
    }

    @Test
    fun rtlLayoutPlacesBackNavigationOnVisualRight() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                HealthVaultTheme {
                    AppScreenScaffold(title = "RTL", onBack = {}) { Box(Modifier.fillMaxSize()) }
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
    fun expandedWindowKeepsDashboardMetricsAccessible() {
        composeRule.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(900.dp, 1_000.dp))) {
                HealthVaultTheme {
                    DashboardScreen(
                        record = emptyVault("Expanded").profiles.single(),
                        clock = FIXED_CLOCK,
                        zoneId = ZoneOffset.UTC,
                        onProfileClick = {},
                        onReminders = {},
                        onDocumentSelected = {},
                        onAddHealthInfo = {},
                        onImportDocument = {},
                        onAddMedication = {},
                        onAddAppointment = {},
                    )
                }
            }
        }

        listOf(R.string.documents_metric, R.string.medications_metric, R.string.appointments_metric)
            .forEach { composeRule.onNodeWithText(composeRule.activity.getString(it)).assertIsDisplayed() }
    }

    @Test
    fun navigationHasExactFiveRootsAndProfileAwareBackStacks() {
        assertEquals(
            listOf("Home", "HealthRecords", "Search", "Medications", "Appointments"),
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
            navigation.select(TopLevelDestination.HealthRecords)
            assertEquals(DocumentDetailRoute(profileId, "document-id"), navigation.currentBackStack.last())
            navigation.goBack()
            assertEquals(HealthRecordsRoute, navigation.currentBackStack.last())
            assertTrue(navigation.isAtSecondaryRoot)
            navigation.goBack()
            assertEquals(TopLevelDestination.Home, navigation.selectedDestination)
            assertEquals(HomeRoute, navigation.currentBackStack.last())
        }
    }

    @Test
    fun compactNavigationUsesFourRootsAndMore() {
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

        TopLevelDestination.entries.dropLast(1).forEach { destination ->
            val label = composeRule.activity.getString(destination.label)
            val node = composeRule
                .onNodeWithContentDescription(label, useUnmergedTree = true)
                .assertIsDisplayed()
                .fetchSemanticsNode()
            assertEquals(Role.Tab, node.config[SemanticsProperties.Role])
            composeRule.onAllNodesWithText(label).assertCountEquals(0)
        }
        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.nav_appointments))
            .assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.action_more))
            .assertIsDisplayed()
            .performClick()
        composeRule.runOnIdle { assertTrue(moreSelected) }
    }

    @Test
    fun compactNavigationSelectsOnlyMoreForMoreDestinations() {
        composeRule.setContent {
            HealthVaultTheme {
                AppNavigationSuite(
                    selectedDestination = TopLevelDestination.Home,
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
    fun expandedNavigationKeepsAppointmentsAsDirectRoot() {
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
    fun moreSheetShowsLocalizedDestinationsAndDispatchesActions() {
        var selectedAction: String? = null
        lateinit var showSheet: () -> Unit
        composeRule.setContent {
            var visible by remember { mutableStateOf(true) }
            showSheet = { visible = true }
            HealthVaultTheme {
                if (visible) {
                    AppMoreSheet(
                        onDismissRequest = { visible = false },
                        onAppointments = {
                            selectedAction = "appointments"
                            visible = false
                        },
                        onReminders = {
                            selectedAction = "reminders"
                            visible = false
                        },
                        onSettings = {
                            selectedAction = "settings"
                            visible = false
                        },
                    )
                }
            }
        }

        val appointments = composeRule.activity.getString(R.string.nav_appointments)
        val reminders = composeRule.activity.getString(R.string.reminders_title)
        val settings = composeRule.activity.getString(R.string.settings_title)
        composeRule.onNodeWithText(appointments).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals("appointments", selectedAction)
            showSheet()
        }
        composeRule.onNodeWithText(reminders).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals("reminders", selectedAction)
            showSheet()
        }
        composeRule.onNodeWithText(settings).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("settings", selectedAction) }
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

    private companion object {
        val PROFILE_ID: UUID = UUID.fromString("44e15584-8158-4bf8-bf26-dd7358f392cf")
        val FIXED_INSTANT: Instant = Instant.parse("2026-07-30T08:00:00Z")
        val FIXED_CLOCK: Clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC)
    }
}
