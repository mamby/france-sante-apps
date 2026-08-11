package net.mamby.health.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import net.mamby.health.ui.theme.UiTokens

internal val LocalBottomTabBarInsets = staticCompositionLocalOf<WindowInsets> {
    WindowInsets(0, 0, 0, 0)
}

@Composable
internal fun appContentWindowInsets(): WindowInsets =
    WindowInsets.safeDrawing.union(LocalBottomTabBarInsets.current)

@Composable
fun PaddingValues.withScreenPadding(): PaddingValues =
    withScreenPadding(topPadding = UiTokens.ScreenPadding)

@Composable
fun PaddingValues.withPagePadding(): PaddingValues =
    withScreenPadding(topPadding = UiTokens.PageTopPadding)

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
    top = UiTokens.PageTopPadding,
    end = UiTokens.ScreenPadding,
    bottom = UiTokens.ScreenPadding,
)

@Composable
internal fun PaddingValues.withAdditionalBottomPadding(additionalBottom: Dp): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        top = calculateTopPadding(),
        end = calculateEndPadding(direction),
        bottom = calculateBottomPadding() + additionalBottom,
    )
}
