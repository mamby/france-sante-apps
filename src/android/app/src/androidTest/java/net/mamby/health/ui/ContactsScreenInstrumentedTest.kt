package net.mamby.health.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.VaultContact
import net.mamby.health.feature.contacts.ContactDetailScreen
import net.mamby.health.feature.contacts.ContactEditorScreen
import net.mamby.health.ui.theme.HealthVaultTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContactsScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun contactFormCreatesVaultWideContactWithoutBlankValues() {
        var savedContact: VaultContact? = null
        composeRule.setContent {
            HealthVaultTheme {
                ContactEditorScreen(
                    existing = null,
                    onCancel = {},
                    onSave = { contact, onResult ->
                        savedContact = contact
                        onResult(true)
                    },
                )
            }
        }

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.contact_name))
            .performTextInput("  Samira Haddad  ")
        composeRule
            .onNode(hasText("Samira Haddad", substring = true) and hasSetTextAction())
            .assertTextContains("Samira Haddad", substring = true)
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.common_save))
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertNotNull(savedContact)
            assertEquals("Samira Haddad", savedContact?.name)
            assertEquals(emptyList<String>(), savedContact?.phoneNumbers)
            assertEquals(emptyList<String>(), savedContact?.emailAddresses)
            assertEquals(emptyList<String>(), savedContact?.websites)
            assertEquals(emptyList<String>(), savedContact?.addresses)
        }
    }

    @Test
    fun dirtyCancelKeepsTheDraftUntilDiscardIsConfirmed() {
        var canceled = false
        composeRule.setContent {
            HealthVaultTheme {
                ContactEditorScreen(
                    existing = null,
                    onCancel = { canceled = true },
                    onSave = { _, _ -> },
                )
            }
        }

        composeRule
            .onNode(hasText(composeRule.activity.getString(R.string.contact_name)) and hasSetTextAction())
            .performTextInput("Samira")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.common_cancel)).performClick()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.discard_changes_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.keep_editing_action)).performClick()
        composeRule.onNode(hasText("Samira") and hasSetTextAction()).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(false, canceled) }

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.discard_changes_action)).performClick()
        composeRule.runOnIdle { assertEquals(true, canceled) }
    }

    @Test
    fun cleanBackExitsWithoutDiscardConfirmation() {
        var canceled = false
        composeRule.setContent {
            HealthVaultTheme {
                ContactEditorScreen(
                    existing = null,
                    onCancel = { canceled = true },
                    onSave = { _, _ -> },
                )
            }
        }

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.runOnIdle { assertEquals(true, canceled) }
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.discard_changes_title))
            .assertDoesNotExist()
    }

    @Test
    fun failedSaveRetainsTheDraftAndReEnablesSave() {
        var completion: ((Boolean) -> Unit)? = null
        composeRule.setContent {
            HealthVaultTheme {
                ContactEditorScreen(
                    existing = null,
                    onCancel = {},
                    onSave = { _, onResult -> completion = onResult },
                )
            }
        }

        composeRule
            .onNode(hasText(composeRule.activity.getString(R.string.contact_name)) and hasSetTextAction())
            .performTextInput("Samira")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.common_save)).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.common_save)).assertDoesNotExist()

        composeRule.runOnIdle { requireNotNull(completion)(false) }

        composeRule.onNode(hasText("Samira") and hasSetTextAction()).assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.common_save))
            .assertIsEnabled()
    }

    @Test
    fun invalidWebsiteShowsInlineValidationAndDisablesSave() {
        composeRule.setContent {
            HealthVaultTheme {
                ContactEditorScreen(
                    existing = null,
                    onCancel = {},
                    onSave = { _, _ -> },
                )
            }
        }

        composeRule
            .onNode(hasText(composeRule.activity.getString(R.string.contact_name)) and hasSetTextAction())
            .performTextInput("Samira")
        composeRule
            .onNode(hasText(composeRule.activity.getString(R.string.contact_websites)) and hasSetTextAction())
            .performTextInput("ftp://example.test")

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.invalid_contact_website))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.common_save))
            .assertIsNotEnabled()
    }

    @Test
    fun hardwareTabTraversalFollowsTheVisualFieldOrder() {
        composeRule.setContent {
            HealthVaultTheme {
                ContactEditorScreen(
                    existing = null,
                    onCancel = {},
                    onSave = { _, _ -> },
                )
            }
        }
        val nameField = composeRule
            .onNode(hasText(composeRule.activity.getString(R.string.contact_name)) and hasSetTextAction())
        val phoneField = composeRule
            .onNode(hasText(composeRule.activity.getString(R.string.contact_phone_numbers)) and hasSetTextAction())

        nameField.performClick().assertIsFocused()
        nameField.performKeyInput { pressKey(Key.Tab) }

        phoneField.assertIsFocused()
    }

    @Test
    fun everySavedContactValueIsAnIndependentAccessibleAction() {
        val invokedActions = mutableListOf<String>()
        val contact = VaultContact(
            id = UUID.fromString("982c7e3f-68ce-43e8-b480-69d46b755a31"),
            name = "Samira Haddad",
            phoneNumbers = listOf("+33 1 23 45 67 89", "+33 6 12 34 56 78"),
            emailAddresses = listOf("samira@example.com"),
            websites = listOf("https://example.com"),
            addresses = listOf("10 rue de la Paix\n75002 Paris"),
            notes = "Family doctor",
            updatedAt = Instant.EPOCH,
        )
        composeRule.setContent {
            HealthVaultTheme {
                ContactDetailScreen(
                    contact = contact,
                    onBack = null,
                    onEdit = {},
                    onDelete = {},
                    onDialPhone = { invokedActions += "phone:$it" },
                    onComposeEmail = { invokedActions += "email:$it" },
                    onOpenWebsite = { invokedActions += "website:$it" },
                    onSearchAddress = { invokedActions += "address:$it" },
                )
            }
        }

        val actions = listOf(
            composeRule.activity.getString(R.string.contact_phone_action, contact.phoneNumbers[0]) to
                "phone:${contact.phoneNumbers[0]}",
            composeRule.activity.getString(R.string.contact_phone_action, contact.phoneNumbers[1]) to
                "phone:${contact.phoneNumbers[1]}",
            composeRule.activity.getString(R.string.contact_email_action, contact.emailAddresses.single()) to
                "email:${contact.emailAddresses.single()}",
            composeRule.activity.getString(R.string.contact_website_action, contact.websites.single()) to
                "website:${contact.websites.single()}",
            composeRule.activity.getString(R.string.contact_address_action, contact.addresses.single()) to
                "address:${contact.addresses.single()}",
        )
        actions.forEach { (contentDescription, _) ->
            composeRule
                .onNodeWithContentDescription(contentDescription)
                .performScrollTo()
                .assertIsDisplayed()
                .assertHasClickAction()
                .performClick()
        }

        composeRule.runOnIdle {
            assertEquals(actions.map(Pair<String, String>::second), invokedActions)
        }
    }
}
