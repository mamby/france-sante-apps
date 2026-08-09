package net.mamby.health.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import net.mamby.health.core.model.CareDirectoryKind
import net.mamby.health.R
import net.mamby.health.core.model.CustomDocumentCategory
import net.mamby.health.core.model.CustomMeasurementType
import net.mamby.health.core.model.CareDirectoryEntry
import net.mamby.health.core.model.HealthIdentifier
import net.mamby.health.core.model.HealthIdentifierKind
import net.mamby.health.core.model.HealthMeasurement
import net.mamby.health.core.model.HealthNote
import net.mamby.health.core.model.HealthVault
import net.mamby.health.core.model.MeasurementReading
import net.mamby.health.feature.directory.DirectoryScreen
import net.mamby.health.feature.measurements.ManageMeasurementTypesScreen
import net.mamby.health.feature.measurements.MeasurementsScreen
import net.mamby.health.feature.notes.NotesScreen
import net.mamby.health.feature.records.HealthRecordsHubScreen
import net.mamby.health.feature.summary.HealthIdentifierDetailScreen
import net.mamby.health.feature.summary.SummaryScreen
import net.mamby.health.feature.vault.ManageDocumentCategoriesScreen
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
            var creationRequest by remember { mutableLongStateOf(0) }
            HealthVaultTheme {
                NotesScreen(
                    notes = emptyList(),
                    now = NOW,
                    zoneId = ZoneOffset.UTC,
                    onUpsert = { note -> savedNote = note },
                    onSelected = {},
                    creationRequest = creationRequest,
                )
            }
        }
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.add_health_note)).performClick()
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
            var creationRequest by remember { mutableLongStateOf(0) }
            HealthVaultTheme {
                MeasurementsScreen(
                    records = listOf(record),
                    now = NOW,
                    zoneId = ZoneOffset.UTC,
                    onBack = {},
                    onManageTypes = {},
                    onAddProfile = { _, _ -> },
                    onUpsert = { _, measurement -> savedMeasurement = measurement },
                    onSelected = { _, _ -> },
                    creationRequest = creationRequest,
                )
            }
        }
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.add_measurement)).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.measurement_value)).performTextInput("72")
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
    fun directoryFormCreatesInternationalContactRecord() {
        var savedEntry: CareDirectoryEntry? = null
        composeRule.setContent {
            val record = remember { record() }
            var creationRequest by remember { mutableLongStateOf(0) }
            HealthVaultTheme {
                DirectoryScreen(
                    records = listOf(record),
                    onBack = {},
                    onAddProfile = { _, _ -> },
                    onUpsert = { _, entry -> savedEntry = entry },
                    onSelected = { _, _ -> },
                    creationRequest = creationRequest,
                )
            }
        }
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.add_directory_entry)).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.directory_name)).performTextInput("Dr Martin")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.common_save)).performClick()
        composeRule.runOnIdle { assertEquals("Dr Martin", savedEntry?.name) }
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
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.measurement_type_name)).performTextInput("Waist")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.measurement_type_unit)).performTextInput("cm")
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
    fun healthInformationSelectsPrimaryDoctorFromTheCurrentProfile() {
        val doctorId = UUID.fromString("54f530e2-1975-43b0-9e8b-11b5ef0e3f2a")
        var selectedDoctorId: UUID? = null
        val record = record().copy(
            careDirectory = listOf(
                CareDirectoryEntry(
                    id = doctorId,
                    kind = CareDirectoryKind.DOCTOR,
                    name = "Dr Martin",
                    specialty = "General medicine",
                    updatedAt = NOW,
                ),
            ),
        )
        composeRule.setContent {
            HealthVaultTheme {
                SummaryScreen(
                    record = record,
                    today = LocalDate.of(2026, 7, 30),
                    onBack = {},
                    onUpdateProfile = {},
                    onUpsertVaccination = {},
                    onDeleteVaccination = {},
                    onSetPrimaryDoctor = { selectedDoctorId = it },
                    onUpsertFamilyHistory = {},
                    onDeleteFamilyHistory = {},
                    onUpsertDirective = {},
                    onDeleteDirective = {},
                    onUpsertIdentifier = {},
                    onDeleteIdentifier = {},
                    onEmergencyContactSelected = {},
                    onVaccinationSelected = {},
                    onFamilyHistorySelected = {},
                    onDirectiveSelected = {},
                    onIdentifierSelected = {},
                )
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToIndex(1)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.choose_primary_doctor)).performClick()
        composeRule.onNodeWithText("Dr Martin").performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.common_save)).performClick()
        composeRule.runOnIdle { assertEquals(doctorId, selectedDoctorId) }
    }

    private fun record() = HealthVault.empty(NOW, PROFILE_ID, "Owner").profiles.single()

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-30T08:00:00Z")
        val PROFILE_ID: UUID = UUID.fromString("bc79a12a-241b-4761-94ce-65c56376f3a5")
    }
}
