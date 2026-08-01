package net.mamby.health.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import net.mamby.health.navigation.TopLevelDestination

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigationSuite(
    selectedDestination: TopLevelDestination,
    onDestinationSelected: (TopLevelDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val selected = selectedDestination == destination
                item(
                    selected = selected,
                    onClick = { onDestinationSelected(destination) },
                    icon = {
                        val label = stringResource(destination.label)
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Above,
                            ),
                            tooltip = { PlainTooltip { Text(label) } },
                            state = androidx.compose.material3.rememberTooltipState(),
                        ) {
                            Icon(
                                if (selected) destination.selectedIcon else destination.icon,
                                contentDescription = label,
                                modifier = Modifier.semantics { role = Role.Tab },
                            )
                        }
                    },
                    label = {},
                )
            }
        },
        content = content,
    )
}
