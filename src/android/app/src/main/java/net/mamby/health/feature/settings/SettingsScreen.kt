package net.mamby.health.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import java.time.Duration
import java.time.ZoneId
import net.mamby.health.BuildConfig
import net.mamby.health.R
import net.mamby.health.backup.BackupKeyDeriver
import net.mamby.health.backup.PortableBackupFormat
import net.mamby.health.backup.RestorePreview
import net.mamby.health.settings.AppSettings
import net.mamby.health.settings.BackupState
import net.mamby.health.settings.ThemeMode
import net.mamby.health.ui.components.AppScreenScaffold
import net.mamby.health.ui.components.FormDialog
import net.mamby.health.ui.components.LabeledValue
import net.mamby.health.ui.components.SectionCard
import net.mamby.health.ui.components.SwitchField
import net.mamby.health.ui.components.withPagePadding
import net.mamby.health.ui.format.localizedDateTime
import net.mamby.health.ui.theme.UiTokens

@Composable
fun SettingsScreen(
    settings: AppSettings,
    zoneId: ZoneId,
    restorePreview: RestorePreview?,
    message: String?,
    onBack: (() -> Unit)? = null,
    onThemeChanged: (ThemeMode) -> Unit,
    onLocaleChanged: (String) -> Unit,
    onAppLockChanged: (Boolean) -> Unit,
    onAppLockTimeoutChanged: (Duration) -> Unit,
    onLockNow: () -> Unit,
    onConfigureBackup: (Uri, CharArray, Boolean) -> Unit,
    onBackupNow: () -> Unit,
    onClearBackup: () -> Unit,
    onPrepareRestore: (Uri, CharArray) -> Unit,
    onCommitRestore: (RestorePreview, Boolean) -> Unit,
    onDiscardRestore: (RestorePreview) -> Unit,
    onDeleteVault: () -> Unit,
    restoreRequestId: Long = 0L,
    onRestoreRequestHandled: () -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val selectedLocaleTag = remember(configuration) {
        AppCompatDelegate.getApplicationLocales()
            .get(0)
            ?.language
            ?.takeIf { it in AppSettings.supportedLocaleTags }
            ?: AppSettings.DEFAULT_LOCALE_TAG
    }
    var backupDialog by remember { mutableStateOf(false) }
    var pendingBackupPassphrase by remember { mutableStateOf<CharArray?>(null) }
    var pendingBackupScheduled by remember { mutableStateOf(true) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var deleteDialog by remember { mutableStateOf(false) }
    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(PortableBackupFormat.MIME_TYPE),
    ) { uri ->
        val passphrase = pendingBackupPassphrase
        pendingBackupPassphrase = null
        if (uri != null && passphrase != null) onConfigureBackup(uri, passphrase, pendingBackupScheduled)
        else passphrase?.fill('\u0000')
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreUri = uri
    }

    LaunchedEffect(restoreRequestId) {
        if (restoreRequestId > 0L) {
            onRestoreRequestHandled()
            openBackup.launch(arrayOf(PortableBackupFormat.MIME_TYPE, "application/octet-stream", "application/zip"))
        }
    }

    AppScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .withPagePadding(),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            PageHeader()
            message?.let { Text(it) }
            SectionCard(stringResource(R.string.appearance_title)) {
                Text(stringResource(R.string.theme_system))
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { onThemeChanged(mode) },
                        label = { Text(stringResource(mode.labelResource())) },
                    )
                }
            }
            SectionCard(stringResource(R.string.language_title)) {
                localeChoices().forEach { (tag, label) ->
                    FilterChip(
                        selected = selectedLocaleTag == tag,
                        onClick = { onLocaleChanged(tag) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            SectionCard(stringResource(R.string.security_title)) {
                Text(stringResource(R.string.app_lock_body))
                SwitchField(stringResource(R.string.app_lock), settings.appLockEnabled, onAppLockChanged)
                if (settings.appLockEnabled) {
                    timeoutChoices().forEach { (duration, label) ->
                        FilterChip(
                            selected = settings.appLockTimeout == duration,
                            onClick = { onAppLockTimeoutChanged(duration) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                    OutlinedButton(onClick = onLockNow) { Text(stringResource(R.string.lock_now)) }
                }
            }
            SectionCard(stringResource(R.string.backup_title)) {
                Text(stringResource(R.string.backup_body))
                LabeledValue(
                    stringResource(R.string.backup_title),
                    stringResource(settings.backupStatus.state.labelResource()),
                )
                settings.backupStatus.lastSuccess?.let {
                    Text(stringResource(R.string.backup_last_success, it.localizedDateTime(zoneId)))
                }
                if (settings.backupConfiguration == null) {
                    Button(onClick = { backupDialog = true }) { Text(stringResource(R.string.configure_backup)) }
                } else {
                    Button(onClick = onBackupNow) { Text(stringResource(R.string.backup_now)) }
                    OutlinedButton(onClick = { backupDialog = true }) { Text(stringResource(R.string.configure_backup)) }
                    OutlinedButton(onClick = onClearBackup) { Text(stringResource(R.string.remove_backup_configuration)) }
                }
                Button(onClick = {
                    openBackup.launch(arrayOf(PortableBackupFormat.MIME_TYPE, "application/octet-stream", "application/zip"))
                }) { Text(stringResource(R.string.restore_backup)) }
            }
            SectionCard(stringResource(R.string.recovery_title)) {
                Text(stringResource(R.string.recovery_body))
            }
            SectionCard(stringResource(R.string.privacy_title)) {
                Text(stringResource(R.string.privacy_body))
                Text(stringResource(R.string.health_disclaimer))
                Text(stringResource(R.string.build_channel, stringResource(environmentLabelResource())))
            }
            SectionCard(stringResource(R.string.data_title)) {
                OutlinedButton(onClick = { deleteDialog = true }) {
                    Text(stringResource(R.string.delete_vault))
                }
            }
        }
    }

    if (backupDialog) {
        BackupConfigurationDialog(
            onDismiss = { backupDialog = false },
            onContinue = { passphrase, scheduled ->
                pendingBackupPassphrase = passphrase
                pendingBackupScheduled = scheduled
                backupDialog = false
                createBackup.launch("health-vault.${PortableBackupFormat.FILE_EXTENSION}")
            },
        )
    }
    restoreUri?.let { uri ->
        RestorePassphraseDialog(
            onDismiss = { restoreUri = null },
            onContinue = { passphrase ->
                restoreUri = null
                onPrepareRestore(uri, passphrase)
            },
        )
    }
    restorePreview?.let { preview ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { onDiscardRestore(preview) },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = UiTokens.DialogTonalElevation,
            title = {
                Text(stringResource(if (preview.requiresCrossFlavorConfirmation) R.string.cross_flavor_title else R.string.restore_backup))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing)) {
                    val people = pluralStringResource(
                        R.plurals.restore_people_count,
                        preview.profileCount,
                        preview.profileCount,
                    )
                    val documents = pluralStringResource(
                        R.plurals.restore_documents_count,
                        preview.documentCount,
                        preview.documentCount,
                    )
                    Text(
                        stringResource(
                            R.string.restore_ready_summary,
                            preview.updatedAt.localizedDateTime(zoneId),
                            people,
                            documents,
                        ),
                    )
                    if (preview.requiresCrossFlavorConfirmation) {
                        Text(stringResource(R.string.cross_flavor_message, preview.sourceEnvironment, preview.currentEnvironment))
                    }
                }
            },
            confirmButton = {
                Button(onClick = { onCommitRestore(preview, preview.requiresCrossFlavorConfirmation) }) {
                    Text(stringResource(if (preview.requiresCrossFlavorConfirmation) R.string.confirm_cross_flavor_restore else R.string.common_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { onDiscardRestore(preview) }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
    if (deleteDialog) {
        DeleteVaultDialog(
            onDismiss = { deleteDialog = false },
            onDelete = {
                deleteDialog = false
                onDeleteVault()
            },
        )
    }
}

@StringRes
private fun environmentLabelResource(): Int = when (BuildConfig.ENVIRONMENT) {
    "dev" -> R.string.channel_dev
    "beta" -> R.string.channel_beta
    "stage" -> R.string.channel_stage
    "prod" -> R.string.channel_prod
    else -> R.string.channel_dev
}

@Composable
private fun BackupConfigurationDialog(
    onDismiss: () -> Unit,
    onContinue: (CharArray, Boolean) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var scheduled by remember { mutableStateOf(true) }
    val valid = BackupKeyDeriver.isAcceptablePassphrase(passphrase) && passphrase == confirmation
    FormDialog(
        title = stringResource(R.string.configure_backup),
        saveEnabled = valid,
        onDismiss = onDismiss,
        onSave = { onContinue(passphrase.toCharArray(), scheduled) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
            Text(stringResource(R.string.backup_passphrase_requirement))
            OutlinedTextField(
                passphrase,
                { passphrase = it },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.backup_passphrase)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            OutlinedTextField(
                confirmation,
                { confirmation = it },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.backup_passphrase_confirm)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = confirmation.isNotEmpty() && passphrase != confirmation,
            )
            SwitchField(stringResource(R.string.backup_scheduled), scheduled, { scheduled = it })
        }
    }
}

@Composable
private fun RestorePassphraseDialog(
    onDismiss: () -> Unit,
    onContinue: (CharArray) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    FormDialog(
        title = stringResource(R.string.restore_backup),
        saveEnabled = BackupKeyDeriver.isAcceptablePassphrase(passphrase),
        onDismiss = onDismiss,
        onSave = { onContinue(passphrase.toCharArray()) },
    ) {
        OutlinedTextField(
            passphrase,
            { passphrase = it },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.restore_passphrase)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
    }
}

@Composable
private fun DeleteVaultDialog(onDismiss: () -> Unit, onDelete: () -> Unit) {
    val required = stringResource(R.string.delete_vault_confirmation_word)
    var confirmation by remember { mutableStateOf("") }
    FormDialog(
        title = stringResource(R.string.delete_vault_title),
        saveEnabled = confirmation == required,
        onDismiss = onDismiss,
        onSave = onDelete,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing)) {
            Text(stringResource(R.string.delete_vault_message))
            OutlinedTextField(
                confirmation,
                { confirmation = it },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.delete_vault_confirmation_label, required)) },
            )
        }
    }
}

private fun ThemeMode.labelResource(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun BackupState.labelResource(): Int = when (this) {
    BackupState.NOT_CONFIGURED -> R.string.backup_not_configured
    BackupState.READY -> R.string.backup_ready
    BackupState.RUNNING -> R.string.backup_running
    BackupState.SUCCEEDED -> R.string.backup_succeeded
    BackupState.NEEDS_ATTENTION -> R.string.backup_needs_attention
}

private fun localeChoices(): List<Pair<String, Int>> = listOf(
    "" to R.string.language_system,
    "en" to R.string.language_english,
    "fr" to R.string.language_french,
    "ar" to R.string.language_arabic,
)

private fun timeoutChoices(): List<Pair<Duration, Int>> = listOf(
    Duration.ZERO to R.string.timeout_immediate,
    Duration.ofMinutes(1) to R.string.timeout_one_minute,
    Duration.ofMinutes(5) to R.string.timeout_five_minutes,
    Duration.ofMinutes(15) to R.string.timeout_fifteen_minutes,
)
