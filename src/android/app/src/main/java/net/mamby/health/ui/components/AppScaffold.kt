package net.mamby.health.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.LocalListDetailSceneScope
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import net.mamby.health.R
import net.mamby.health.ui.theme.UiTokens

interface AppScreenContentScope {
    @Composable
    fun PageHeader(modifier: Modifier = Modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable AppScreenContentScope.(PaddingValues) -> Unit,
) {
    var floatingActionButtonHeightPx by remember { mutableIntStateOf(0) }
    val floatingActionButtonHeight = with(LocalDensity.current) {
        floatingActionButtonHeightPx.toDp()
    }
    val topEdgeProtectionHeight = with(LocalDensity.current) {
        (WindowInsets.systemBars.getTop(this) * UiTokens.TopEdgeProtectionHeightMultiplier).toDp()
    }

    val pageScope = object : AppScreenContentScope {
        @Composable
        override fun PageHeader(modifier: Modifier) {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .heightIn(min = UiTokens.FloatingBackButtonTouchTargetSize),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    Spacer(
                        Modifier.width(
                            UiTokens.FloatingBackButtonTouchTargetSize + UiTokens.ContentSpacing,
                        ),
                    )
                }
                Text(
                    text = title,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
                actions()
            }
        }
    }

    Box {
        Scaffold(
            contentWindowInsets = appContentWindowInsets(),
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
                pageScope.content(contentPadding)
            },
        )
        EdgeProtection(
            edge = EdgeProtectionEdge.Top,
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(topEdgeProtectionHeight),
        )
        if (onBack != null) {
            FloatingBackButton(
                onBack = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Start,
                        ),
                    )
                    .padding(
                        start = UiTokens.ScreenPadding,
                        top = UiTokens.PageTopPadding,
                    ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.action_back)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Below,
        ),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(UiTokens.FloatingBackButtonVisualSize)
                .dropShadow(
                    shape = CircleShape,
                    shadow = Shadow(
                        radius = UiTokens.FloatingBackButtonShadowRadius,
                        spread = UiTokens.FloatingBackButtonShadowSpread,
                        color = MaterialTheme.colorScheme.scrim.copy(
                            alpha = UiTokens.FloatingBackButtonShadowAlpha,
                        ),
                        offset = DpOffset(
                            x = UiTokens.FloatingBackButtonShadowOffsetX,
                            y = UiTokens.FloatingBackButtonShadowOffsetY,
                        ),
                    ),
                ),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(
                    alpha = UiTokens.FloatingBackButtonContainerAlpha,
                ),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lucide_arrow_left),
                contentDescription = label,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun listDetailAwareBack(onBack: () -> Unit): (() -> Unit)? =
    onBack.takeIf { LocalListDetailSceneScope.current == null }
