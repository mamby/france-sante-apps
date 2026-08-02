package net.mamby.health.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import net.mamby.health.R
import net.mamby.health.core.model.HealthProfile
import net.mamby.health.ui.theme.UiTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    profile: HealthProfile? = null,
    onProfileClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
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
                    actions = {
                        actions()
                        if (onSettings != null) {
                            IconButton(onClick = onSettings) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = stringResource(R.string.action_settings),
                                )
                            }
                        }
                    },
                )
                if (profile != null && onProfileClick != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                onClickLabel = stringResource(R.string.profile_switch),
                                onClick = onProfileClick,
                            )
                            .padding(horizontal = UiTokens.ScreenPadding, vertical = UiTokens.CompactSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(UiTokens.ProfileAvatarSize)
                                .clip(CircleShape)
                                .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(profile.initials(), fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            profile.displayName,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = UiTokens.ContentSpacing),
                            fontWeight = FontWeight.Medium,
                        )
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    }
                }
            }
        },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

private fun HealthProfile.initials(): String = displayName
    .trim()
    .split(Regex("\\s+"))
    .filter(String::isNotEmpty)
    .take(2)
    .mapNotNull(String::firstOrNull)
    .joinToString("")
    .uppercase()
