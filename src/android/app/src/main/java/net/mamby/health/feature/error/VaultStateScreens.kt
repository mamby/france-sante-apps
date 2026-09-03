package net.mamby.health.feature.error

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.mamby.health.R
import net.mamby.health.data.UnreadableReason
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.withPagePadding
import net.mamby.health.ui.theme.UiTokens

@Composable
fun VaultUnreadableScreen(
    reason: UnreadableReason,
    onRestore: () -> Unit,
    onReset: () -> Unit,
) {
    val reasonText = stringResource(
        when (reason) {
            UnreadableReason.CORRUPT -> R.string.vault_corrupt
            UnreadableReason.UNSUPPORTED_VERSION -> R.string.vault_unsupported
            UnreadableReason.KEY_UNAVAILABLE -> R.string.vault_key_unavailable
            UnreadableReason.IO_FAILURE -> R.string.vault_io_failure
        },
    )
    AppScreenScaffold(title = stringResource(R.string.vault_unreadable_title)) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .withPagePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
            ) {
                Text(stringResource(R.string.vault_unreadable_body))
                Text(reasonText)
                Button(onClick = onRestore) { Text(stringResource(R.string.restore_backup)) }
                Button(onClick = onReset) { Text(stringResource(R.string.reset_unreadable_vault)) }
            }
        }
    }
}
