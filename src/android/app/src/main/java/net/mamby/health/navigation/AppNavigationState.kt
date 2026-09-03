package net.mamby.health.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import net.mamby.androidkit.navigation3.MultiBackStackNavigationState
import net.mamby.androidkit.navigation3.rememberMultiBackStackNavigationState

class AppNavigationState internal constructor(
    private val navigation: MultiBackStackNavigationState<TopLevelRoute>,
) {
    val selectedDestination: TopLevelDestination
        get() = TopLevelDestination.entries.first { it.route == navigation.selectedRoot }

    val currentBackStack: NavBackStack<NavKey>
        get() = navigation.currentBackStack

    val isAtSecondaryRoot: Boolean
        get() = selectedDestination != TopLevelDestination.Home && navigation.isAtRoot

    fun select(destination: TopLevelDestination) {
        navigation.selectRoot(destination.route, popToRootOnReselect = false)
    }

    fun selectRoot(destination: TopLevelDestination) {
        navigation.openRoot(destination.route)
    }

    fun navigate(route: AppRoute) {
        navigation.navigate(route)
    }

    fun navigate(destination: TopLevelDestination, route: AppRoute? = null) {
        select(destination)
        route?.let(navigation::navigate)
    }

    fun navigate(destination: TopLevelDestination, vararg routes: AppRoute) {
        select(destination)
        routes.forEach(navigation::navigate)
    }

    fun replaceTop(vararg routes: AppRoute) {
        require(routes.isNotEmpty()) { "At least one replacement route is required." }
        check(currentBackStack.size > 1) { "A top-level root cannot be replaced." }
        navigation.replaceTop(routes.first())
        routes.drop(1).forEach(navigation::navigate)
    }

    fun goBack() {
        navigation.goBack()
    }

    fun trimToRoots(keepDestination: Boolean = true) {
        navigation.reset(
            if (keepDestination) navigation.selectedRoot else TopLevelDestination.Home.route,
        )
    }

    fun resetTo(destination: TopLevelDestination = TopLevelDestination.Home) {
        navigation.reset(destination.route)
    }
}

@Composable
fun rememberAppNavigationState(): AppNavigationState {
    val roots = remember { TopLevelDestination.entries.map(TopLevelDestination::route) }
    val navigation = rememberMultiBackStackNavigationState(
        roots = roots,
        startRoot = TopLevelDestination.Home.route,
    )
    return remember(navigation) {
        AppNavigationState(navigation)
    }
}
