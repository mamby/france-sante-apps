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

    val isAtSecondaryRoot: Boolean
        get() = selectedDestination != TopLevelDestination.Home && currentBackStack.size == 1

    fun select(destination: TopLevelDestination) {
        selectedDestination = destination
    }

    fun selectRoot(destination: TopLevelDestination) {
        val backStack = backStacks.getValue(destination)
        while (backStack.size > 1) backStack.removeLastOrNull()
        selectedDestination = destination
    }

    fun navigate(route: AppRoute) {
        currentBackStack.add(route)
    }

    fun navigate(destination: TopLevelDestination, route: AppRoute? = null) {
        selectedDestination = destination
        route?.let { backStacks.getValue(destination).add(it) }
    }

    fun navigate(destination: TopLevelDestination, vararg routes: AppRoute) {
        selectedDestination = destination
        backStacks.getValue(destination).addAll(routes)
    }

    fun goBack() {
        if (currentBackStack.size > 1) {
            currentBackStack.removeLastOrNull()
        } else if (selectedDestination != TopLevelDestination.Home) {
            selectedDestination = TopLevelDestination.Home
        }
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
    val selected = rememberSaveable("navigation_state_v5") {
        androidx.compose.runtime.mutableStateOf(TopLevelDestination.Home)
    }
    val backStacks = key("navigation_state_v5") {
        listOf(
            rememberNavBackStack(HomeRoute),
            rememberNavBackStack(SearchRoute),
            rememberNavBackStack(HealthRecordsRoute),
            rememberNavBackStack(NotesRoute),
            rememberNavBackStack(MedicationsRoute),
            rememberNavBackStack(ScheduleRoute),
            rememberNavBackStack(ContactsRoute),
            rememberNavBackStack(SettingsRoute),
            rememberNavBackStack(ManageProfilesRoute),
        )
    }
    val home = backStacks[0]
    val search = backStacks[1]
    val records = backStacks[2]
    val notes = backStacks[3]
    val medications = backStacks[4]
    val schedule = backStacks[5]
    val contacts = backStacks[6]
    val settings = backStacks[7]
    val profiles = backStacks[8]
    return remember(home, search, records, notes, medications, schedule, contacts, settings, profiles) {
        AppNavigationState(
            selected,
            mapOf(
                TopLevelDestination.Home to home,
                TopLevelDestination.Search to search,
                TopLevelDestination.HealthRecords to records,
                TopLevelDestination.Notes to notes,
                TopLevelDestination.Medications to medications,
                TopLevelDestination.Schedule to schedule,
                TopLevelDestination.Contacts to contacts,
                TopLevelDestination.Settings to settings,
                TopLevelDestination.Profiles to profiles,
            ),
        )
    }
}
