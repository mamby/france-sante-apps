package net.mamby.health.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import net.mamby.health.R

sealed interface AppRoute : NavKey

sealed interface TopLevelRoute : AppRoute

@Serializable data object HomeRoute : TopLevelRoute
@Serializable data object SearchRoute : TopLevelRoute
@Serializable data object HealthRecordsRoute : TopLevelRoute
@Serializable data object NotesRoute : TopLevelRoute
@Serializable data object MedicationsRoute : TopLevelRoute
@Serializable data object ScheduleRoute : TopLevelRoute
@Serializable data object DirectoryRoute : TopLevelRoute

@Serializable data class DocumentDetailRoute(val profileId: String, val id: String) : AppRoute
@Serializable data class MedicationDetailRoute(val profileId: String, val id: String) : AppRoute
@Serializable data class ScheduleDetailRoute(val id: String) : AppRoute
@Serializable data class HealthInfoRoute(val profileId: String) : AppRoute
@Serializable data object DocumentsRoute : AppRoute
@Serializable data object MeasurementsRoute : AppRoute
@Serializable data class ManageDocumentCategoriesRoute(val profileId: String) : AppRoute
@Serializable data class ManageMeasurementTypesRoute(val profileId: String) : AppRoute
@Serializable data class EmergencyContactDetailRoute(val profileId: String, val id: String) : AppRoute
@Serializable data class VaccinationDetailRoute(val profileId: String, val id: String) : AppRoute
@Serializable data class NoteDetailRoute(val id: String) : AppRoute
@Serializable data class MeasurementDetailRoute(val profileId: String, val id: String) : AppRoute
@Serializable data class DirectoryEntryDetailRoute(val profileId: String, val id: String) : AppRoute
@Serializable data class FamilyHistoryDetailRoute(val profileId: String, val id: String) : AppRoute
@Serializable data class CareDirectiveDetailRoute(val profileId: String, val id: String) : AppRoute
@Serializable data class HealthIdentifierDetailRoute(val profileId: String, val id: String) : AppRoute

@Serializable data object SettingsRoute : TopLevelRoute
@Serializable data object ManageProfilesRoute : AppRoute

enum class TopLevelDestination(
    val route: TopLevelRoute,
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Home(HomeRoute, R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    Search(SearchRoute, R.string.nav_search, Icons.Outlined.Search, Icons.Filled.Search),
    HealthRecords(
        HealthRecordsRoute,
        R.string.nav_health_records,
        Icons.Outlined.FolderShared,
        Icons.Filled.FolderShared,
    ),
    Notes(
        NotesRoute,
        R.string.nav_notes,
        Icons.AutoMirrored.Outlined.Notes,
        Icons.AutoMirrored.Filled.Notes,
    ),
    Medications(
        MedicationsRoute,
        R.string.nav_medications,
        Icons.Outlined.Medication,
        Icons.Filled.Medication,
    ),
    Schedule(
        ScheduleRoute,
        R.string.schedule_title,
        Icons.Outlined.CalendarMonth,
        Icons.Filled.CalendarMonth,
    ),
    Directory(
        DirectoryRoute,
        R.string.care_directory_title,
        Icons.Outlined.ContactPage,
        Icons.Filled.ContactPage,
    ),
    Settings(
        SettingsRoute,
        R.string.settings_title,
        Icons.Outlined.Settings,
        Icons.Filled.Settings,
    ),
    ;

    companion object {
        val compactPrimary = listOf(Home, Search, HealthRecords, Notes)
        val compactOverflow = listOf(Medications, Schedule, Directory, Settings)
    }
}
