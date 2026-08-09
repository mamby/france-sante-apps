package net.mamby.health.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.GridTrackSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    NavigationSuiteScaffold(
        layoutType = layoutType,
        containerColor = Color.Transparent,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            shortNavigationBarContainerColor = Color.Transparent,
            navigationBarContainerColor = Color.Transparent,
            navigationRailContainerColor = Color.Transparent,
            navigationDrawerContainerColor = Color.Transparent,
        ),
        navigationSuiteItems = {
            (if (usesMore) TopLevelDestination.compactPrimary else TopLevelDestination.entries)
                .forEach { destination ->
                val selected = !isMoreSelected && selectedDestination == destination
                item(
                    selected = selected,
                    onClick = { onDestinationSelected(destination) },
                    icon = {
                        val label = stringResource(destination.label)
                        NavigationIcon(
                            imageVector = if (selected) destination.selectedIcon else destination.icon,
                            label = label,
                        )
                    },
                )
            }
            if (usesMore) {
                item(
                    selected = isMoreSelected,
                    onClick = onMoreSelected,
                    icon = {
                        NavigationIcon(
                            imageVector = Icons.Outlined.MoreHoriz,
                            label = stringResource(R.string.action_more),
                        )
                    },
                )
            }
        },
        content = content,
    )
}

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
                row(GridTrackSize.Auto)
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
private fun NavigationIcon(imageVector: ImageVector, label: String) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
        ),
        tooltip = { PlainTooltip { Text(label) } },
        state = androidx.compose.material3.rememberTooltipState(),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = label,
            modifier = Modifier.semantics { role = Role.Tab },
        )
    }
}

@Composable
private fun MoreSheetItem(label: String, icon: ImageVector, onClick: () -> Unit) {
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
            Icon(icon, contentDescription = null)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}
