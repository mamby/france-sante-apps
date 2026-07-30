package net.mamby.health.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import net.mamby.health.ui.theme.UiTokens

@Composable
fun PaddingValues.withScreenPadding(): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction) + UiTokens.ScreenPadding,
        top = calculateTopPadding() + UiTokens.ScreenPadding,
        end = calculateEndPadding(direction) + UiTokens.ScreenPadding,
        bottom = calculateBottomPadding() + UiTokens.ScreenPadding,
    )
}
