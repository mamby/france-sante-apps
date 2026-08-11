package net.mamby.health.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import net.mamby.health.ui.theme.UiTokens

internal enum class EdgeProtectionEdge {
    Top,
    Bottom,
}

@Composable
internal fun EdgeProtection(
    edge: EdgeProtectionEdge,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val colors = when (edge) {
        EdgeProtectionEdge.Top -> listOf(
            color.copy(alpha = UiTokens.EdgeProtectionEdgeAlpha),
            color.copy(alpha = UiTokens.EdgeProtectionMiddleAlpha),
            Color.Transparent,
        )

        EdgeProtectionEdge.Bottom -> listOf(
            Color.Transparent,
            color.copy(alpha = UiTokens.EdgeProtectionMiddleAlpha),
            color.copy(alpha = UiTokens.EdgeProtectionEdgeAlpha),
        )
    }
    Spacer(
        modifier = modifier.background(
            brush = Brush.verticalGradient(colors),
        ),
    )
}
