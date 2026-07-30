package net.mamby.health.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

class AppNavigationState internal constructor(
    selectedDestination: MutableState<TopLevelDestination>,
    private val backStacks: Map<TopLevelDestination, NavBackStack<NavKey>>,
) {
    var selectedDestination by selectedDestination
        private set

    val currentBackStack: NavBackStack<NavKey>
        get() = backStacks.getValue(selectedDestination)

    fun select(destination: TopLevelDestination) {
        selectedDestination = destination
    }

    fun navigate(route: AppRoute) {
        currentBackStack.add(route)
    }

    fun navigate(destination: TopLevelDestination, route: AppRoute? = null) {
        select(destination)
        route?.let { backStacks.getValue(destination).add(it) }
    }

    fun goBack() {
        currentBackStack.removeLastOrNull()
    }

    fun resetTo(destination: TopLevelDestination = TopLevelDestination.Dashboard) {
        backStacks.values.forEach { backStack ->
            while (backStack.size > 1) backStack.removeLastOrNull()
        }
        selectedDestination = destination
    }
}

@Composable
fun rememberAppNavigationState(): AppNavigationState {
    val selected = rememberSaveable { androidx.compose.runtime.mutableStateOf(TopLevelDestination.Dashboard) }
    val dashboard = rememberNavBackStack(DashboardRoute)
    val vault = rememberNavBackStack(VaultRoute)
    val summary = rememberNavBackStack(SummaryRoute)
    val medications = rememberNavBackStack(MedicationsRoute)
    val appointments = rememberNavBackStack(AppointmentsRoute)
    return remember(dashboard, vault, summary, medications, appointments) {
        AppNavigationState(
            selected,
            mapOf(
                TopLevelDestination.Dashboard to dashboard,
                TopLevelDestination.Vault to vault,
                TopLevelDestination.Summary to summary,
                TopLevelDestination.Medications to medications,
                TopLevelDestination.Appointments to appointments,
            ),
        )
    }
}
