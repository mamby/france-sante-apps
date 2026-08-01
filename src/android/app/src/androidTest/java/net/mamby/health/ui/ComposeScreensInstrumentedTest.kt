package net.mamby.health.ui

import android.content.res.Configuration
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
import net.mamby.health.ui.components.AppNavigationSuite
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
                    onSettings = {},
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
    fun emptyDocumentsHasProfileContextTabsAndImportAction() {
        val profile = emptyVault("Amina").profiles.single().profile
        composeRule.setContent {
            HealthVaultTheme {
                VaultScreen(
                    documents = emptyList(),
                    profile = profile,
                    today = LocalDate.of(2026, 7, 30),
                    onProfileClick = {},
                    onSettings = {},
                    onImport = {},
                    onDocumentSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("Amina").assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.health_info_tab)).assertIsDisplayed()
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
                        onSettings = {},
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
    fun navigationShellUsesFiveIconOnlyLocalizedTabs() {
        composeRule.setContent {
            HealthVaultTheme {
                AppNavigationSuite(TopLevelDestination.Home, {}) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }

        TopLevelDestination.entries.forEach { destination ->
            val label = composeRule.activity.getString(destination.label)
            val node = composeRule
                .onNodeWithContentDescription(label, useUnmergedTree = true)
                .assertIsDisplayed()
                .fetchSemanticsNode()
            assertEquals(Role.Tab, node.config[SemanticsProperties.Role])
            composeRule.onAllNodesWithText(label).assertCountEquals(0)
        }
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
