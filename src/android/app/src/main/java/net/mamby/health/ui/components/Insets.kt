package net.mamby.health.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.mamby.androidkit.compose.layout.androidKitContentWindowInsets
import net.mamby.health.ui.theme.UiTokens

@Composable
internal fun appContentWindowInsets(): WindowInsets =
    androidKitContentWindowInsets()

@Composable
fun PaddingValues.withScreenPadding(): PaddingValues =
    withScreenPadding(topPadding = UiTokens.ScreenPadding)

@Composable
fun PaddingValues.withPagePadding(): PaddingValues =
    withScreenPadding(topPadding = 0.dp)

@Composable
private fun PaddingValues.withScreenPadding(topPadding: Dp): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction) + UiTokens.ScreenPadding,
        top = calculateTopPadding() + topPadding,
        end = calculateEndPadding(direction) + UiTokens.ScreenPadding,
        bottom = calculateBottomPadding() + UiTokens.ScreenPadding,
    )
}

fun Modifier.withPagePadding(): Modifier = padding(
    start = UiTokens.ScreenPadding,
    end = UiTokens.ScreenPadding,
    bottom = UiTokens.ScreenPadding,
)
