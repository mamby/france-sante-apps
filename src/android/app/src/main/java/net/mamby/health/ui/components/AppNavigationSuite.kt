package net.mamby.health.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.GridTrackSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItemColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import net.mamby.health.R
import net.mamby.health.navigation.TopLevelDestination
import net.mamby.health.ui.theme.UiTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigationSuite(
    selectedDestination: TopLevelDestination,
    layoutType: NavigationSuiteType,
    isMoreSelected: Boolean,
    onDestinationSelected: (TopLevelDestination) -> Unit,
    onMoreSelected: () -> Unit,
    content: @Composable () -> Unit,
) {
    val usesMore = layoutType == NavigationSuiteType.ShortNavigationBarCompact
    val compactNavigationContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(
        alpha = UiTokens.CompactNavigationContainerAlpha,
    )
    val navigationSuiteColors = NavigationSuiteDefaults.colors(
        shortNavigationBarContainerColor = compactNavigationContainerColor,
        shortNavigationBarContentColor = MaterialTheme.colorScheme.onSurface,
        navigationBarContainerColor = Color.Transparent,
        navigationRailContainerColor = Color.Transparent,
        navigationDrawerContainerColor = Color.Transparent,
    )
    val navigationSuiteItemColors = navigationSuiteItemColors()
    val navigationItems: NavigationSuiteScope.() -> Unit = {
        AppNavigationItems(
            selectedDestination = selectedDestination,
            usesMore = usesMore,
            isMoreSelected = isMoreSelected,
            onDestinationSelected = onDestinationSelected,
            onMoreSelected = onMoreSelected,
            itemColors = navigationSuiteItemColors,
        )
    }
    val compactNavigationItems: @Composable () -> Unit = {
        AppShortNavigationItems(
            selectedDestination = selectedDestination,
            isMoreSelected = isMoreSelected,
            onDestinationSelected = onDestinationSelected,
            onMoreSelected = onMoreSelected,
        )
    }

    if (usesMore) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                            ),
                        )
                        .padding(UiTokens.CompactSpacing),
                    contentAlignment = Alignment.Center,
                ) {
                    ShortNavigationBar(
                        modifier = Modifier
                            .shadow(
                                elevation = UiTokens.FloatingNavigationBarElevation,
                                shape = MaterialTheme.shapes.extraLarge,
                                clip = true,
                            ),
                        containerColor = compactNavigationContainerColor,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        arrangement = ShortNavigationBarArrangement.EqualWeight,
                        content = compactNavigationItems,
                    )
                }
            },
        ) { padding ->
            CompositionLocalProvider(
                LocalBottomTabBarInsets provides WindowInsets(
                    bottom = padding.calculateBottomPadding(),
                ),
            ) {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    } else {
        NavigationSuiteScaffold(
            navigationSuiteItems = navigationItems,
            layoutType = layoutType,
            containerColor = Color.Transparent,
            navigationSuiteColors = navigationSuiteColors,
            content = content,
        )
    }
}

@Composable
private fun AppShortNavigationItems(
    selectedDestination: TopLevelDestination,
    isMoreSelected: Boolean,
    onDestinationSelected: (TopLevelDestination) -> Unit,
    onMoreSelected: () -> Unit,
) {
    val itemColors = ShortNavigationBarItemDefaults.colors(
        selectedIndicatorColor = Color.Transparent,
    )
    TopLevelDestination.compactPrimary.forEach { destination ->
        val selected = !isMoreSelected && selectedDestination == destination
        ShortNavigationBarItem(
            selected = selected,
            onClick = { onDestinationSelected(destination) },
            icon = {
                NavigationIndicatorIcon(
                    icon = destination.icon,
                    label = stringResource(destination.label),
                    selected = selected,
                )
            },
            label = null,
            colors = itemColors,
        )
    }
    ShortNavigationBarItem(
        selected = isMoreSelected,
        onClick = onMoreSelected,
        icon = {
            NavigationIndicatorIcon(
                icon = R.drawable.ic_lucide_ellipsis,
                label = stringResource(R.string.action_more),
                selected = isMoreSelected,
            )
        },
        label = null,
        colors = itemColors,
    )
}

private fun NavigationSuiteScope.AppNavigationItems(
    selectedDestination: TopLevelDestination,
    usesMore: Boolean,
    isMoreSelected: Boolean,
    onDestinationSelected: (TopLevelDestination) -> Unit,
    onMoreSelected: () -> Unit,
    itemColors: NavigationSuiteItemColors,
) {
    (if (usesMore) TopLevelDestination.compactPrimary else TopLevelDestination.entries)
        .forEach { destination ->
            val selected = !isMoreSelected && selectedDestination == destination
            item(
                selected = selected,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    NavigationIcon(
                        icon = destination.icon,
                        label = stringResource(destination.label),
                        selected = selected,
                    )
                },
                label = null,
                colors = itemColors,
            )
        }
    if (usesMore) {
        item(
            selected = isMoreSelected,
            onClick = onMoreSelected,
            icon = {
                NavigationIcon(
                    icon = R.drawable.ic_lucide_ellipsis,
                    label = stringResource(R.string.action_more),
                    selected = isMoreSelected,
                )
            },
            label = null,
            colors = itemColors,
        )
    }
}

@Composable
private fun navigationSuiteItemColors(): NavigationSuiteItemColors =
    NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            indicatorColor = Color.Transparent,
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            indicatorColor = Color.Transparent,
        ),
        navigationDrawerItemColors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = Color.Transparent,
        ),
    )

internal fun appNavigationSuiteType(adaptiveInfo: WindowAdaptiveInfo): NavigationSuiteType =
    when (val recommended = NavigationSuiteScaffoldDefaults.navigationSuiteType(adaptiveInfo)) {
        NavigationSuiteType.NavigationBar,
        NavigationSuiteType.ShortNavigationBarMedium -> NavigationSuiteType.ShortNavigationBarCompact
        NavigationSuiteType.WideNavigationRailCollapsed,
        NavigationSuiteType.WideNavigationRailExpanded -> NavigationSuiteType.NavigationRail
        else -> recommended
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGridApi::class)
@Composable
internal fun AppMoreSheet(
    onDismissRequest: () -> Unit,
    destinations: List<TopLevelDestination> = TopLevelDestination.compactOverflow,
    onDestinationSelected: (TopLevelDestination) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Grid(
            config = {
                repeat(3) { column(1.fr) }
                repeat((destinations.size + 2) / 3) { row(GridTrackSize.Auto) }
                columnGap(UiTokens.CompactSpacing)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiTokens.ScreenPadding)
                .padding(bottom = UiTokens.ScreenPadding),
        ) {
            destinations.forEach { destination ->
                MoreSheetItem(
                    label = stringResource(destination.label),
                    icon = destination.icon,
                    onClick = { onDestinationSelected(destination) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigationIcon(@DrawableRes icon: Int, label: String, selected: Boolean) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = { PlainTooltip { Text(label) } },
        state = androidx.compose.material3.rememberTooltipState(),
    ) {
        NavigationIndicatorIcon(icon = icon, label = label, selected = selected)
    }
}

@Composable
private fun NavigationIndicatorIcon(
    @DrawableRes icon: Int,
    label: String,
    selected: Boolean,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        label = "navigation selection indicator",
    )
    Surface(
        modifier = Modifier.size(UiTokens.NavigationSelectionIndicatorSize),
        shape = CircleShape,
        color = containerColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                modifier = Modifier.semantics { role = Role.Tab },
            )
        }
    }
}

@Composable
private fun MoreSheetItem(label: String, @DrawableRes icon: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = UiTokens.CompactSpacing,
                vertical = UiTokens.ContentSpacing,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing),
        ) {
            Icon(painterResource(icon), contentDescription = null)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}
