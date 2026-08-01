package net.mamby.health.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
        selectedDestination = destination
        route?.let { backStacks.getValue(destination).add(it) }
    }

    fun goBack() {
        currentBackStack.removeLastOrNull()
    }

    fun trimToRoots(keepDestination: Boolean = true) {
        backStacks.values.forEach { backStack ->
            while (backStack.size > 1) backStack.removeLastOrNull()
        }
        if (!keepDestination) selectedDestination = TopLevelDestination.Home
    }

    fun resetTo(destination: TopLevelDestination = TopLevelDestination.Home) {
        trimToRoots()
        selectedDestination = destination
    }
}

@Composable
fun rememberAppNavigationState(): AppNavigationState {
    val selected = rememberSaveable("navigation_state_v2") {
        androidx.compose.runtime.mutableStateOf(TopLevelDestination.Home)
    }
    val backStacks = key("navigation_state_v2") {
        listOf(
            rememberNavBackStack(HomeRoute),
            rememberNavBackStack(HealthRecordsRoute),
            rememberNavBackStack(SearchRoute),
            rememberNavBackStack(MedicationsRoute),
            rememberNavBackStack(AppointmentsRoute),
        )
    }
    val (home, records, search, medications, appointments) = backStacks
    return remember(home, records, search, medications, appointments) {
        AppNavigationState(
            selected,
            mapOf(
                TopLevelDestination.Home to home,
                TopLevelDestination.HealthRecords to records,
                TopLevelDestination.Search to search,
                TopLevelDestination.Medications to medications,
                TopLevelDestination.Appointments to appointments,
            ),
        )
    }
}
