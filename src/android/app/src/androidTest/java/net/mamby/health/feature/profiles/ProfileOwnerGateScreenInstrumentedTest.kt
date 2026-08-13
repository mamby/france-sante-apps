package net.mamby.health.feature.profiles

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.core.model.ProfileRecord
import net.mamby.health.ui.theme.HealthVaultTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileOwnerGateScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun zeroPeopleRequiresNameAndRequestsStableCreationWithoutContinuingLocally() {
        var createdName: String? = null
        var createdId: UUID? = null
        var selectedId: UUID? = null

        composeRule.setContent {
            val state = remember { ProfileOwnerGateViewModel() }
            HealthVaultTheme {
                ProfileOwnerGateScreen(
                    profiles = emptyList(),
                    proposedProfileId = PROPOSED_PROFILE_ID,
                    onBack = {},
                    onProfileSelected = { selectedId = it },
                    onCreateProfile = { name, id, complete ->
                        createdName = name
                        createdId = id
                        complete(true)
                    },
                    state = state,
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.add_profile)).assertIsDisplayed()
        composeRule
            .onNodeWithText(string(R.string.profile_owner_gate_empty_body))
            .assertIsDisplayed()

        val continueButton = composeRule.onNodeWithText(
            string(R.string.profile_owner_gate_add_continue),
        )
        continueButton.assertIsNotEnabled()
        composeRule
            .onNodeWithText(string(R.string.display_name))
            .performTextInput("  Noor Said  ")
        continueButton.assertIsEnabled().performClick()

        composeRule.runOnIdle {
            assertEquals("Noor Said", createdName)
            assertEquals(PROPOSED_PROFILE_ID, createdId)
            assertNull(
                "The gate must wait for the persisted profile list before continuing",
                selectedId,
            )
        }
    }

    @Test
    fun multiplePeopleSelectsExistingOwnerAndOffersAddPersonFlow() {
        val profiles = listOf(
            profile(FIRST_PROFILE_ID, "Amina Said"),
            profile(SECOND_PROFILE_ID, "Noor Said"),
        )
        var selectedId: UUID? = null

        composeRule.setContent {
            val state = remember { ProfileOwnerGateViewModel() }
            HealthVaultTheme {
                ProfileOwnerGateScreen(
                    profiles = profiles,
                    proposedProfileId = PROPOSED_PROFILE_ID,
                    onBack = {},
                    onProfileSelected = { selectedId = it },
                    onCreateProfile = { _, _, _ -> },
                    state = state,
                )
            }
        }

        composeRule
            .onNodeWithText(string(R.string.profile_owner_gate_title))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(string(R.string.profile_owner_gate_choose_body))
            .assertIsDisplayed()

        composeRule.onNodeWithText("Noor Said").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(SECOND_PROFILE_ID, selectedId) }

        composeRule.onNodeWithText(string(R.string.add_profile)).performClick()
        composeRule
            .onNodeWithText(string(R.string.profile_owner_gate_new_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.display_name)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.common_cancel)).performClick()
        composeRule.onNodeWithText("Amina Said").assertIsDisplayed()
        composeRule.onNodeWithText("Noor Said").assertIsDisplayed()
    }

    private fun string(id: Int): String = composeRule.activity.getString(id)

    private fun profile(id: UUID, name: String) = ProfileRecord(
        profile = HealthProfile(
            id = id,
            displayName = name,
            lastUpdatedAt = NOW,
        ),
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-13T08:00:00Z")
        val PROPOSED_PROFILE_ID: UUID = UUID.fromString("11111111-1111-4111-8111-111111111111")
        val FIRST_PROFILE_ID: UUID = UUID.fromString("22222222-2222-4222-8222-222222222222")
        val SECOND_PROFILE_ID: UUID = UUID.fromString("33333333-3333-4333-8333-333333333333")
    }
}
