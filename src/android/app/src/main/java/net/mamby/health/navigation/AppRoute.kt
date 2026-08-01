package net.mamby.health.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import net.mamby.health.R

sealed interface AppRoute : NavKey

sealed interface TopLevelRoute : AppRoute

@Serializable data object HomeRoute : TopLevelRoute
@Serializable data object HealthRecordsRoute : TopLevelRoute
@Serializable data object SearchRoute : TopLevelRoute
@Serializable data object MedicationsRoute : TopLevelRoute
@Serializable data object AppointmentsRoute : TopLevelRoute

@Serializable data class DocumentDetailRoute(val profileId: String, val id: String) : AppRoute
@Serializable data class MedicationDetailRoute(val profileId: String, val id: String) : AppRoute
@Serializable data class AppointmentDetailRoute(val profileId: String, val id: String) : AppRoute
@Serializable data class HealthInfoDetailRoute(
    val profileId: String,
    val targetKind: String,
    val id: String? = null,
) : AppRoute

@Serializable data object RemindersRoute : AppRoute
@Serializable data object SettingsRoute : AppRoute
@Serializable data object ManageProfilesRoute : AppRoute

enum class TopLevelDestination(
    val route: TopLevelRoute,
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Home(HomeRoute, R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    HealthRecords(
        HealthRecordsRoute,
        R.string.nav_health_records,
        Icons.Outlined.FolderShared,
        Icons.Filled.FolderShared,
    ),
    Search(SearchRoute, R.string.nav_search, Icons.Outlined.Search, Icons.Filled.Search),
    Medications(
        MedicationsRoute,
        R.string.nav_medications,
        Icons.Outlined.Medication,
        Icons.Filled.Medication,
    ),
    Appointments(
        AppointmentsRoute,
        R.string.nav_appointments,
        Icons.Outlined.CalendarMonth,
        Icons.Filled.CalendarMonth,
    ),
}
