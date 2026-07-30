package net.mamby.health.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import net.mamby.health.R

sealed interface AppRoute : NavKey

sealed interface TopLevelRoute : AppRoute

@Serializable
data object DashboardRoute : TopLevelRoute

@Serializable
data object VaultRoute : TopLevelRoute

@Serializable
data object SummaryRoute : TopLevelRoute

@Serializable
data object MedicationsRoute : TopLevelRoute

@Serializable
data object AppointmentsRoute : TopLevelRoute

@Serializable
data class DocumentDetailRoute(val id: String) : AppRoute

@Serializable
data class MedicationDetailRoute(val id: String) : AppRoute

@Serializable
data class AppointmentDetailRoute(val id: String) : AppRoute

@Serializable
data object RemindersRoute : AppRoute

@Serializable
data object SettingsRoute : AppRoute

enum class TopLevelDestination(
    val route: TopLevelRoute,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    Dashboard(DashboardRoute, R.string.nav_dashboard, Icons.Outlined.Home),
    Vault(VaultRoute, R.string.nav_vault, Icons.Outlined.Description),
    Summary(SummaryRoute, R.string.nav_summary, Icons.Outlined.HealthAndSafety),
    Medications(MedicationsRoute, R.string.nav_medications, Icons.Outlined.Medication),
    Appointments(AppointmentsRoute, R.string.nav_appointments, Icons.Outlined.CalendarMonth),
}
