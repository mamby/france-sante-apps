package net.mamby.health.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import net.mamby.androidkit.compose.action.AndroidKitFloatingActionButton
import net.mamby.androidkit.compose.layout.AndroidKitPage
import net.mamby.androidkit.compose.layout.AndroidKitPageAction
import net.mamby.androidkit.compose.layout.AndroidKitPageActionItem
import net.mamby.androidkit.navigation3.listDetailBackAction
import net.mamby.health.R

@Composable
fun AppScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: List<AndroidKitPageActionItem> = emptyList(),
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    AndroidKitPage(
        title = title,
        onBack = onBack,
        actions = actions,
        floatingActionButton = floatingActionButton,
        floatingActionAlignment = Alignment.End,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingAddButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Below,
        ),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        AndroidKitFloatingActionButton(onClick = onClick) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_plus),
                contentDescription = label,
            )
        }
    }
}

@Composable
fun titleBarAction(
    label: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
): AndroidKitPageAction = AndroidKitPageAction(
    icon = ImageVector.vectorResource(icon),
    label = label,
    onClick = onClick,
)

@Composable
fun detailTitleBarActions(
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
): List<AndroidKitPageActionItem> = buildList {
    onEdit?.let { edit ->
        add(
            titleBarAction(
                label = stringResource(R.string.common_edit),
                icon = R.drawable.ic_lucide_pencil,
                onClick = edit,
            ),
        )
    }
    onDelete?.let { delete ->
        add(
            titleBarAction(
                label = stringResource(R.string.common_delete),
                icon = R.drawable.ic_lucide_trash_2,
                onClick = delete,
            ),
        )
    }
}

@Composable
fun listDetailAwareBack(onBack: () -> Unit): (() -> Unit)? = listDetailBackAction(onBack)
