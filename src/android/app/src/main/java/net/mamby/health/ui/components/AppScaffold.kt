package net.mamby.health.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import net.mamby.health.R
import net.mamby.health.ui.theme.UiTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    contextHeader: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    var floatingActionButtonHeightPx by remember { mutableIntStateOf(0) }
    val floatingActionButtonHeight = with(LocalDensity.current) {
        floatingActionButtonHeightPx.toDp()
    }

    Scaffold(
        contentWindowInsets = appContentWindowInsets(),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                )
                            }
                        }
                    },
                    actions = { actions() },
                )
                contextHeader?.invoke()
            }
        },
        floatingActionButton = {
            Box(
                modifier = Modifier.onSizeChanged {
                    floatingActionButtonHeightPx = it.height
                },
            ) {
                floatingActionButton()
            }
        },
        content = { padding ->
            val contentPadding = if (floatingActionButtonHeightPx == 0) {
                padding
            } else {
                padding.withAdditionalBottomPadding(
                    floatingActionButtonHeight + UiTokens.ContentSpacing,
                )
            }
            content(contentPadding)
        },
    )
}
