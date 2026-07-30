package net.mamby.health.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import net.mamby.health.R
import net.mamby.health.core.model.HealthVault
import net.mamby.health.feature.dashboard.DashboardScreen
import net.mamby.health.feature.vault.VaultScreen
import net.mamby.health.navigation.AppNavigationState
import net.mamby.health.navigation.DocumentDetailRoute
import net.mamby.health.navigation.MedicationDetailRoute
import net.mamby.health.navigation.TopLevelDestination
import net.mamby.health.navigation.VaultRoute
import net.mamby.health.navigation.rememberAppNavigationState
import net.mamby.health.ui.components.AppScreenScaffold
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
    fun demoDashboard_labelsSampleDataAndStartsRealVault() {
        val startRequested = AtomicBoolean(false)

        composeRule.setContent {
            HealthVaultTheme {
                DashboardScreen(
                    vault = emptyVault(displayName = "Sample person"),
                    isDemo = true,
                    clock = FIXED_CLOCK,
                    zoneId = ZoneOffset.UTC,
                    onStartVault = { startRequested.set(true) },
                    onSettings = {},
                    onReminders = {},
                    onDocumentSelected = {},
                )
            }
        }

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.sample_banner_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.start_vault)).performClick()
        assertTrue(startRequested.get())
    }

    @Test
    fun emptyVault_explainsHowToImportTheFirstDocument() {
        composeRule.setContent {
            HealthVaultTheme {
                VaultScreen(
                    documents = emptyList(),
                    isDemo = false,
                    today = LocalDate.of(2026, 7, 30),
                    onStartVault = {},
                    onSettings = {},
                    onImport = {},
                    onDocumentSelected = {},
                )
            }
        }

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.no_documents_title))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.no_documents_body))
            .assertIsDisplayed()
    }

    @Test
    fun rtlLayout_placesBackNavigationOnTheVisualRight() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                HealthVaultTheme {
                    AppScreenScaffold(title = "RTL", onBack = {}) { _ ->
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val backBounds = composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.action_back))
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Back navigation should be in the right half for RTL layouts",
            backBounds.center.x > rootBounds.center.x,
        )
    }

    @Test
    fun expandedWindow_keepsAllDashboardMetricsAccessible() {
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(900.dp, 1_000.dp)),
            ) {
                HealthVaultTheme {
                    DashboardScreen(
                        vault = emptyVault(displayName = "Expanded"),
                        isDemo = false,
                        clock = FIXED_CLOCK,
                        zoneId = ZoneOffset.UTC,
                        onStartVault = {},
                        onSettings = {},
                        onReminders = {},
                        onDocumentSelected = {},
                    )
                }
            }
        }

        listOf(
            R.string.documents_metric,
            R.string.medications_metric,
            R.string.appointments_metric,
            R.string.reminders_metric,
        ).forEach { label ->
            composeRule.onNodeWithText(composeRule.activity.getString(label)).assertIsDisplayed()
        }
    }

    @Test
    fun navigationState_preservesTopLevelBackStacksAndPopsTheCurrentDetail() {
        lateinit var navigation: AppNavigationState
        composeRule.setContent {
            navigation = rememberAppNavigationState()
        }

        composeRule.runOnIdle {
            navigation.navigate(TopLevelDestination.Vault, DocumentDetailRoute("document-id"))
            navigation.navigate(TopLevelDestination.Medications, MedicationDetailRoute("medication-id"))
            navigation.select(TopLevelDestination.Vault)

            assertEquals(DocumentDetailRoute("document-id"), navigation.currentBackStack.last())
            navigation.goBack()
            assertEquals(VaultRoute, navigation.currentBackStack.last())
            assertEquals(TopLevelDestination.Vault, navigation.selectedDestination)
        }
    }

    private fun emptyVault(displayName: String): HealthVault = HealthVault.empty(
        now = FIXED_INSTANT,
        profileId = UUID.fromString("44e15584-8158-4bf8-bf26-dd7358f392cf"),
        displayName = displayName,
    )

    private companion object {
        val FIXED_INSTANT: Instant = Instant.parse("2026-07-30T08:00:00Z")
        val FIXED_CLOCK: Clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC)
    }
}
