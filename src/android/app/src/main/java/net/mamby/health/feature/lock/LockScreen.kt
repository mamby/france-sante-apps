package net.mamby.health.feature.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import net.mamby.health.R
import net.mamby.health.security.AppLockState
import net.mamby.health.ui.theme.UiTokens

@Composable
fun LockScreen(
    state: AppLockState,
    message: String?,
    onUnlock: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            Icon(painterResource(R.drawable.ic_lucide_lock), contentDescription = null)
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.lock_body))
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state == AppLockState.Authenticating || state == AppLockState.Initializing) {
                CircularProgressIndicator()
            } else {
                Button(onClick = onUnlock) { Text(stringResource(R.string.unlock_action)) }
            }
        }
    }
}
