package net.mamby.health.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.app.ActivityOptionsCompat
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import net.mamby.health.R
import net.mamby.health.core.model.CustomDocumentCategory
import net.mamby.health.core.model.CustomMeasurementType
import net.mamby.health.core.model.HealthIdentifier
import net.mamby.health.core.model.HealthIdentifierKind
import net.mamby.health.core.model.HealthMeasurement
import net.mamby.health.core.model.HealthNote
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.VaultContact
import net.mamby.health.core.model.MeasurementReading
import net.mamby.health.feature.contacts.ContactDetailScreen
import net.mamby.health.feature.contacts.ContactEditorScreen
import net.mamby.health.feature.measurements.ManageMeasurementTypesScreen
import net.mamby.health.feature.measurements.MeasurementEditorScreen
import net.mamby.health.feature.notes.HealthNoteEditorScreen
import net.mamby.health.feature.records.HealthRecordsHubScreen
import net.mamby.health.feature.summary.HealthIdentifierDetailScreen
import net.mamby.health.feature.vault.ManageDocumentCategoriesScreen
import net.mamby.health.feature.vault.DocumentImportEditorScreen
import net.mamby.health.ui.theme.HealthVaultTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthRecordsComposeInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun healthRecordsHubExposesOnlyProfileOwnedHealthSections() {
        composeRule.setContent {
            HealthVaultTheme {
                HealthRecordsHubScreen(
                    records = listOf(record()),
                    onHealthInfo = {},
                    onMeasurements = {},
                    onDocuments = {},
                )
            }
        }

        listOf(
            R.string.health_info_title,
            R.string.measurements_title,
            R.string.documents_tab,
        ).forEach { resource ->
            composeRule.onNodeWithText(composeRule.activity.getString(resource)).assertIsDisplayed()
        }
    }

    @Test
    fun independentNoteFormCreatesTypedRecord() {
        var savedNote: HealthNote? = null
        composeRule.setContent {
            HealthVaultTheme {
                HealthNoteEditorScreen(
                    existing = null,
                    now = NOW,
                    zoneId = ZoneOffset.UTC,
                    onCancel = {},
                    onSave = { note, onResult ->
                        savedNote = note
                        onResult(true)
                    },
                )
            }
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.health_note_title)).performTextInput("Follow-up")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.health_note_body)).performTextInput("Independent context")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.common_save)).performClick()
        composeRule.runOnIdle { assertEquals("Follow-up", savedNote?.title) }
    }

    @Test
    fun measurementFormCreatesTypedRecord() {
        var savedMeasurement: HealthMeasurement? = null
        composeRule.setContent {
            val record = remember { record() }
            HealthVaultTheme {
                MeasurementEditorScreen(
                    records = listOf(record),
                    existingOwner = null,
                    existing = null,
                    initialProfileId = record.profile.id,
                    now = NOW,
                    zoneId = ZoneOffset.UTC,
                    onAddProfile = { _, _ -> },
                    onCancel = {},
                    onSave = { _, measurement, complete ->
                        savedMeasurement = measurement
                        complete(true)
                    },
                )
            }
        }
        composeRule
            .onNode(hasText(composeRule.activity.getString(R.string.measurement_value)) and hasSetTextAction())
            .performTextInput("72")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.common_save)).performClick()
        composeRule.runOnIdle {
            assertEquals(72.0, (savedMeasurement?.reading as MeasurementReading.Scalar).value, 0.0)
        }
    }

    @Test
    fun documentCategoryManagerCreatesCustomCatalogEntry() {
        var savedCategory: CustomDocumentCategory? = null
        composeRule.setContent {
            HealthVaultTheme {
                ManageDocumentCategoriesScreen(
                    record = record(),
                    onBack = {},
                    onUpdateBuiltIn = { _, _ -> },
                    onUpsertCustom = { savedCategory = it },
                    onDeleteCustom = { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.add_document_category)).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.category_name)).performTextInput("Imaging")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.common_save)).performClick()
        composeRule.runOnIdle { assertEquals("Imaging", savedCategory?.name) }
    }

    @Test
    fun cancelingDocumentPickerClosesTheCleanImportEditor() {
        var canceled = false
        val registry = object : ActivityResultRegistry() {
            override fun <I : Any?, O : Any?> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                dispatchResult(requestCode, Activity.RESULT_CANCELED, Intent())
            }
        }
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                val record = remember { record() }
                HealthVaultTheme {
                    DocumentImportEditorScreen(
                        records = listOf(record),
                        initialProfileId = record.profile.id,
                        today = LocalDate.of(2026, 7, 30),
                        onAddProfile = { _, _ -> },
                        onCancel = { canceled = true },
                        onImport = { _, _, _ -> },
                    )
                }
            }
        }

        composeRule.waitUntil { canceled }
        composeRule.runOnIdle { assertEquals(true, canceled) }
    }

    @Test
    fun contactFormCreatesVaultWideContactRecord() {
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
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.contact_name)).performTextInput("Dr Martin")
        fun enterFirstValue(labelResource: Int, value: String) {
            composeRule
                .onNode(hasText(composeRule.activity.getString(labelResource)) and hasSetTextAction())
                .performTextInput(value)
        }
        enterFirstValue(R.string.contact_phone_numbers, "+33 1 23 45")
        enterFirstValue(R.string.contact_email_addresses, "dr@example.test")
        enterFirstValue(R.string.contact_websites, "example.test:8443/path")
        enterFirstValue(R.string.contact_addresses, "1 rue Centrale\nParis")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.common_save)).performClick()
        composeRule.runOnIdle {
            assertEquals("Dr Martin", savedContact?.name)
            assertEquals(listOf("+33 1 23 45"), savedContact?.phoneNumbers)
            assertEquals(listOf("dr@example.test"), savedContact?.emailAddresses)
            assertEquals(listOf("https://example.test:8443/path"), savedContact?.websites)
            assertEquals(listOf("1 rue Centrale\nParis"), savedContact?.addresses)
        }
    }

    @Test
    fun measurementTypeManagerCreatesCustomScalarType() {
        var savedType: CustomMeasurementType? = null
        composeRule.setContent {
            HealthVaultTheme {
                ManageMeasurementTypesScreen(record(), {}, { savedType = it }, {})
            }
        }
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.add_measurement_type)).performClick()
        val nameLabel = composeRule.activity.getString(R.string.measurement_type_name)
        val unitLabel = composeRule.activity.getString(R.string.measurement_type_unit)
        composeRule.onNodeWithText(nameLabel).performClick()
        composeRule.onNodeWithText(nameLabel).performTextInput("Waist")
        composeRule.onNodeWithText(unitLabel).performClick()
        composeRule.onNodeWithText(unitLabel).performTextInput("cm")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.common_save)).performClick()
        composeRule.runOnIdle {
            assertEquals("Waist", savedType?.name)
            assertEquals("cm", savedType?.suggestedUnit)
        }
    }

    @Test
    fun identifierDetailMasksAndTemporarilyRevealsTheValue() {
        val identifier = HealthIdentifier(
            UUID.randomUUID(),
            HealthIdentifierKind.SOCIAL_SECURITY,
            "Social security",
            "123456789",
            updatedAt = NOW,
        )
        composeRule.setContent {
            HealthVaultTheme {
                HealthIdentifierDetailScreen(record(), identifier, {})
            }
        }

        composeRule.onNodeWithText("•••• 6789").assertIsDisplayed()
        composeRule.onNodeWithText("123456789").assertDoesNotExist()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.reveal_identifier_value)).performClick()
        composeRule.onNodeWithText("123456789").assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.hide_identifier_value)).performClick()
        composeRule.onNodeWithText("123456789").assertDoesNotExist()
    }

    @Test
    fun contactDetailValuesAreIndependentAccessibleActions() {
        val invoked = mutableListOf<String>()
        val contact = VaultContact(
            id = UUID.randomUUID(),
            name = "Dr Martin",
            phoneNumbers = listOf("+33 1 23 45"),
            emailAddresses = listOf("dr@example.test"),
            websites = listOf("https://example.test"),
            addresses = listOf("1 rue Centrale, Paris"),
            updatedAt = NOW,
        )
        composeRule.setContent {
            HealthVaultTheme {
                ContactDetailScreen(
                    contact = contact,
                    onBack = {},
                    onEdit = {},
                    onDelete = {},
                    onDialPhone = { invoked += "phone:$it" },
                    onComposeEmail = { invoked += "email:$it" },
                    onOpenWebsite = { invoked += "website:$it" },
                    onSearchAddress = { invoked += "address:$it" },
                )
            }
        }

        contact.phoneNumbers.plus(contact.emailAddresses).plus(contact.websites).plus(contact.addresses)
            .forEach { value -> composeRule.onNodeWithText(value).performClick() }
        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    "phone:+33 1 23 45",
                    "email:dr@example.test",
                    "website:https://example.test",
                    "address:1 rue Centrale, Paris",
                ),
                invoked,
            )
        }
    }

    private fun record() = HealthVault.empty(NOW, PROFILE_ID, "Owner").profiles.single()

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-30T08:00:00Z")
        val PROFILE_ID: UUID = UUID.fromString("bc79a12a-241b-4761-94ce-65c56376f3a5")
    }
}
