package net.mamby.health.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import net.mamby.androidkit.compose.navigation.AndroidKitFloatingNavigation
import net.mamby.androidkit.compose.navigation.AndroidKitFloatingNavigationItem
import net.mamby.health.navigation.TopLevelDestination

@Composable
fun AppNavigationSuite(
    selectedDestination: TopLevelDestination,
    onDestinationSelected: (TopLevelDestination) -> Unit,
    navigationVisible: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!navigationVisible) {
        content()
        return
    }

    val navigationItems = TopLevelDestination.entries.map { destination ->
        AndroidKitFloatingNavigationItem(
            key = destination,
            label = stringResource(destination.label),
            icon = ImageVector.vectorResource(destination.icon),
        )
    }
    AndroidKitFloatingNavigation(
        items = navigationItems,
        selectedKey = selectedDestination,
        onSelected = onDestinationSelected,
        modifier = Modifier.fillMaxSize(),
        compactVisibleDestinationCount = TopLevelDestination.compactPrimary.size,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun appNavigationSuiteType(adaptiveInfo: WindowAdaptiveInfo): NavigationSuiteType =
    when (val recommended = NavigationSuiteScaffoldDefaults.navigationSuiteType(adaptiveInfo)) {
        NavigationSuiteType.NavigationBar,
        NavigationSuiteType.ShortNavigationBarMedium -> NavigationSuiteType.ShortNavigationBarCompact
        NavigationSuiteType.WideNavigationRailCollapsed,
        NavigationSuiteType.WideNavigationRailExpanded -> NavigationSuiteType.NavigationRail
        else -> recommended
    }
