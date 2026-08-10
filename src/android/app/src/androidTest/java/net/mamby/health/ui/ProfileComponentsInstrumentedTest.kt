package net.mamby.health.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.util.UUID
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.ui.components.LocalProfileDisplayLabels
import net.mamby.health.ui.components.ProfileFilterChip
import net.mamby.health.ui.components.ProfileOwnerHeader
import net.mamby.health.ui.components.disambiguatedProfileLabels
import net.mamby.health.ui.theme.HealthVaultTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileComponentsInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun filterChipsExposeAllAndSingleProfileIdentityAndDispatchClicks() {
        var allClicks = 0
        var profileClicks = 0

        composeRule.setContent {
            HealthVaultTheme {
                Column {
                    ProfileFilterChip(
                        label = "All profiles",
                        profileId = null,
                        onClick = { allClicks += 1 },
                        accessibleLabel = "All profile data",
                        actionLabel = "Change profile filter",
                    )
                    ProfileFilterChip(
                        label = "Amina Said",
                        profileId = PROFILE_ID,
                        onClick = { profileClicks += 1 },
                        accessibleLabel = "Profile: Amina Said",
                        actionLabel = "Change profile filter",
                    )
                }
            }
        }

        val allProfiles = composeRule
            .onNodeWithContentDescription("All profile data")
            .assertIsDisplayed()
            .assertHasClickAction()
        val selectedProfile = composeRule
            .onNodeWithContentDescription("Profile: Amina Said")
            .assertIsDisplayed()
            .assertHasClickAction()

        assertEquals(false, allProfiles.fetchSemanticsNode().config[SemanticsProperties.Selected])
        assertEquals(true, selectedProfile.fetchSemanticsNode().config[SemanticsProperties.Selected])

        listOf(allProfiles, selectedProfile).forEach { header ->
            val semantics = header.fetchSemanticsNode().config
            assertEquals("Change profile filter", semantics[SemanticsActions.OnClick].label)
        }

        allProfiles.performClick()
        selectedProfile.performClick()
        composeRule.runOnIdle {
            assertEquals(1, allClicks)
            assertEquals(1, profileClicks)
        }
    }

    @Test
    fun inlineProfileFilterChipOpensChooserAndUpdatesSelection() {
        val amina = profile(PROFILE_ID, "Amina Said")
        val noor = profile(UUID.fromString("6da42279-10f6-4e3a-8e10-73cb60417661"), "Noor Said")
        val records = listOf(ProfileRecord(amina), ProfileRecord(noor))
        var selected: UUID? = null

        composeRule.setContent {
            var selectedProfileId by remember { mutableStateOf<UUID?>(null) }
            CompositionLocalProvider(
                LocalProfileDisplayLabels provides records.associate { it.profile.id to it.profile.displayName },
            ) {
                HealthVaultTheme {
                    ProfileFilterChip(
                        records = records,
                        selectedProfileId = selectedProfileId,
                        onSelected = {
                            selectedProfileId = it
                            selected = it
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("All profiles").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Amina Said").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("Amina Said").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(PROFILE_ID, selected) }
    }

    @Test
    fun ownerHeaderExposesIdentityWithoutAnInteractiveAction() {
        val profile = profile(PROFILE_ID, "Alex Martin")

        composeRule.setContent {
            HealthVaultTheme {
                ProfileOwnerHeader(
                    profile = profile,
                    displayLabel = "Alex Martin · 2",
                    accessibleLabel = "Profile: Alex Martin, 2 of 2",
                )
            }
        }

        val owner = composeRule
            .onNodeWithContentDescription("Profile: Alex Martin, 2 of 2")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Alex Martin · 2").assertIsDisplayed()

        val semantics = owner.fetchSemanticsNode().config
        assertFalse(SemanticsActions.OnClick in semantics)
        assertFalse(SemanticsProperties.Role in semantics)
    }

    @Test
    fun duplicateProfileLabelsUseStableCaseInsensitiveOrdinals() {
        val first = profile(UUID.fromString("11111111-1111-1111-1111-111111111111"), "Alex")
        val second = profile(UUID.fromString("22222222-2222-2222-2222-222222222222"), "alex")
        val third = profile(UUID.fromString("33333333-3333-3333-3333-333333333333"), "ALEX")
        val unique = profile(UUID.fromString("44444444-4444-4444-4444-444444444444"), "Noor")
        val composed = profile(UUID.fromString("55555555-5555-5555-5555-555555555555"), "Émile")
        val decomposed = profile(UUID.fromString("66666666-6666-6666-6666-666666666666"), "E\u0301mile")

        val labels = disambiguatedProfileLabels(
            listOf(first, second, third, unique, composed, decomposed),
        ) { profile, ordinal, total ->
            "${profile.displayName} · $ordinal/$total"
        }

        assertEquals("Alex · 1/3", labels[first.id])
        assertEquals("alex · 2/3", labels[second.id])
        assertEquals("ALEX · 3/3", labels[third.id])
        assertEquals("Noor", labels[unique.id])
        assertEquals("Émile · 1/2", labels[composed.id])
        assertEquals("E\u0301mile · 2/2", labels[decomposed.id])
    }

    @Test
    fun filterChipRemainsAccessibleInRtlAtLargeText() {
        var clicked = false

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density.density, fontScale = 1.5f),
            ) {
                HealthVaultTheme {
                    ProfileFilterChip(
                        label = "كل الملفات الشخصية",
                        profileId = null,
                        onClick = { clicked = true },
                        accessibleLabel = "تصفية: كل الملفات الشخصية",
                        actionLabel = "تغيير تصفية الملف الشخصي",
                    )
                }
            }
        }

        val header = composeRule
            .onNodeWithContentDescription("تصفية: كل الملفات الشخصية")
            .assertIsDisplayed()
            .assertHasClickAction()
        val bounds = header.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width > 0f)
        assertTrue(bounds.height > 0f)
        header.performClick()
        composeRule.runOnIdle { assertTrue(clicked) }
    }

    private fun profile(id: UUID, displayName: String) = HealthProfile(
        id = id,
        displayName = displayName,
        lastUpdatedAt = NOW,
    )

    private companion object {
        val PROFILE_ID: UUID = UUID.fromString("44e15584-8158-4bf8-bf26-dd7358f392cf")
        val NOW: Instant = Instant.parse("2026-07-30T08:00:00Z")
    }
}
