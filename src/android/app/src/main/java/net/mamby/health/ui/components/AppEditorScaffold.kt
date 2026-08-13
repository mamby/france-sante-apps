package net.mamby.health.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.LocalListDetailSceneScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import net.mamby.health.R
import net.mamby.health.ui.theme.UiTokens

@Composable
fun EditorBackgroundPane(
    editorActive: Boolean,
    content: @Composable () -> Unit,
) {
    val unavailableLabel = stringResource(R.string.editor_background_unavailable)
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (editorActive) UiTokens.BackgroundPaneDisabledAlpha else 1f)
                .then(
                    if (editorActive) {
                        Modifier.clearAndSetSemantics {
                            disabled()
                            contentDescription = unavailableLabel
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            content()
        }
        if (editorActive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AppEditorScaffold(
    title: String,
    isDirty: Boolean,
    saveEnabled: Boolean,
    isSaving: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var discardConfirmationVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val savingLabel = stringResource(R.string.editor_saving)
    val requestExit = {
        if (!isSaving) {
            if (isDirty) discardConfirmationVisible = true else onCancel()
        }
    }

    BackHandler(enabled = !discardConfirmationVisible, onBack = requestExit)
    LaunchedEffect(isSaving) {
        if (isSaving) focusManager.clearFocus(force = true)
    }

    Scaffold(
        contentWindowInsets = appContentWindowInsets(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (LocalListDetailSceneScope.current == null) {
                        IconButton(onClick = requestExit, enabled = !isSaving) {
                            Icon(
                                painter = painterResource(R.drawable.ic_lucide_arrow_left),
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = requestExit, enabled = !isSaving) {
                        Text(stringResource(R.string.common_cancel), maxLines = 1)
                    }
                    Button(
                        onClick = onSave,
                        enabled = saveEnabled && !isSaving,
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(UiTokens.EditorProgressIndicatorSize),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = UiTokens.FloatingNavigationBorderWidth,
                            )
                        } else {
                            Text(stringResource(R.string.common_save), maxLines = 1)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = UiTokens.EditorMaxWidth)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = UiTokens.ScreenPadding)
                    .padding(
                        top = UiTokens.PageTopPadding,
                        bottom = UiTokens.ScreenPadding,
                    )
                    .then(
                        if (isSaving) {
                            Modifier.clearAndSetSemantics {
                                disabled()
                                contentDescription = savingLabel
                            }
                        } else {
                            Modifier
                        },
                    ),
                verticalArrangement = Arrangement.spacedBy(UiTokens.SectionSpacing),
                content = content,
            )
            if (isSaving) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .semantics { disabled() }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        },
                )
            }
        }
    }

    if (discardConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { discardConfirmationVisible = false },
            title = { Text(stringResource(R.string.unsaved_changes_title)) },
            text = { Text(stringResource(R.string.unsaved_changes_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        discardConfirmationVisible = false
                        onCancel()
                    },
                ) {
                    Text(stringResource(R.string.unsaved_changes_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { discardConfirmationVisible = false }) {
                    Text(stringResource(R.string.unsaved_changes_keep_editing))
                }
            },
        )
    }
}

@Composable
fun EditorSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        content()
    }
}

@Composable
fun EditorFieldPair(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= UiTokens.EditorTwoColumnMinWidth) {
            Row(horizontalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
                first(Modifier.weight(1f))
                second(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        }
    }
}
