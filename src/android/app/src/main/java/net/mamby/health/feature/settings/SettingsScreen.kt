package net.mamby.health.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import net.mamby.androidkit.compose.form.AndroidKitSettingsPage
import net.mamby.androidkit.compose.form.AndroidKitSettingsSelection
import net.mamby.androidkit.compose.form.AndroidKitSettingsOption
import net.mamby.androidkit.compose.form.AndroidKitLanguageSetting
import net.mamby.androidkit.compose.form.AndroidKitFloatingOpacitySetting
import net.mamby.androidkit.compose.form.AndroidKitAppLockSetting
import net.mamby.health.ui.components.FormDialog
import net.mamby.health.ui.components.SwitchField
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
    onOpacityChanged: (Float) -> Unit,
    onOpacityChangeFinished: () -> Unit,
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

    val themeChoices = ThemeMode.entries.map { mode -> mode to stringResource(mode.labelResource()) }
    val languages = localeChoices().map { (tag, label) -> tag to stringResource(label) }
    val lockTimeouts = timeoutChoices().map { (duration, label) -> duration to stringResource(label) }
    val backupStateLabel = stringResource(settings.backupStatus.state.labelResource())
    val configureBackupLabel = stringResource(R.string.configure_backup)
    val backupNowLabel = stringResource(R.string.backup_now)
    val removeBackupLabel = stringResource(R.string.remove_backup_configuration)
    val restoreBackupLabel = stringResource(R.string.restore_backup)
    val appLockLabel = stringResource(R.string.app_lock)
    val appLockBody = stringResource(R.string.app_lock_body)
    val lockNowLabel = stringResource(R.string.lock_now)

    AndroidKitSettingsPage(title = stringResource(R.string.settings_title), onBack = onBack) {
        message?.let { item(key = "message") { Text(it) } }
        generalSection(
            label = stringResource(R.string.settings_general),
            language = AndroidKitLanguageSetting(
                selection = AndroidKitSettingsSelection(
                    label = stringResource(R.string.language_title),
                    options = languages.map { (tag, label) -> AndroidKitSettingsOption(tag, label) },
                    selectedId = selectedLocaleTag, onSelected = onLocaleChanged,
                    closeContentDescription = stringResource(R.string.common_close),
                ),
                searchLabel = stringResource(R.string.settings_search_languages),
                emptyResultsLabel = stringResource(R.string.settings_no_languages),
            ),
            theme = AndroidKitSettingsSelection(
                label = stringResource(R.string.theme_title),
                options = themeChoices.map { (mode, label) -> AndroidKitSettingsOption(mode.name, label) },
                selectedId = settings.themeMode.name,
                onSelected = { onThemeChanged(ThemeMode.valueOf(it)) },
                closeContentDescription = stringResource(R.string.common_close),
            ),
            floatingOpacity = AndroidKitFloatingOpacitySetting(
                label = stringResource(R.string.settings_floating_opacity), value = settings.floatingSurfaceOpacityLevel,
                minimumLabel = stringResource(R.string.settings_min), maximumLabel = stringResource(R.string.settings_max),
                onValueChange = onOpacityChanged, onValueChangeFinished = onOpacityChangeFinished,
            ),
        )
        securitySection(
            label = stringResource(R.string.security_title),
            appLock = AndroidKitAppLockSetting(
                label = appLockLabel, supportingText = appLockBody,
                checked = settings.appLockEnabled, onCheckedChange = onAppLockChanged,
            ),
        ) {
            if (settings.appLockEnabled) {
                item {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing),
                        verticalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing),
                    ) {
                        lockTimeouts.forEach { (duration, label) ->
                            FilterChip(
                                selected = settings.appLockTimeout == duration,
                                onClick = { onAppLockTimeoutChanged(duration) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
                button(label = lockNowLabel, onClick = onLockNow)
            }
        }
        section(
            key = "backup",
            label = stringResource(R.string.backup_title),
            description = stringResource(R.string.backup_body),
        ) {
            item {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing),
                ) {
                    Text(backupStateLabel, style = MaterialTheme.typography.bodyLarge)
                    settings.backupStatus.lastSuccess?.let {
                        Text(
                            stringResource(R.string.backup_last_success, it.localizedDateTime(zoneId)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (settings.backupConfiguration == null) {
                button(label = configureBackupLabel, onClick = { backupDialog = true })
            } else {
                button(label = backupNowLabel, onClick = onBackupNow)
                button(label = configureBackupLabel, onClick = { backupDialog = true })
                button(label = removeBackupLabel, onClick = onClearBackup)
            }
            button(
                label = restoreBackupLabel,
                onClick = {
                    openBackup.launch(
                        arrayOf(
                            PortableBackupFormat.MIME_TYPE,
                            "application/octet-stream",
                            "application/zip",
                        ),
                    )
                },
            )
        }
        section(key = "recovery", label = stringResource(R.string.recovery_title)) {
            item {
                Text(stringResource(R.string.recovery_body), modifier = Modifier.weight(1f))
            }
        }
        section(key = "privacy", label = stringResource(R.string.privacy_title)) {
            item {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing),
                ) {
                    Text(stringResource(R.string.privacy_body))
                    Text(stringResource(R.string.health_disclaimer))
                    Text(
                        stringResource(
                            R.string.build_channel,
                            stringResource(environmentLabelResource()),
                        ),
                    )
                }
            }
        }
        section(key = "data", label = stringResource(R.string.data_title)) {
            item {
                OutlinedButton(
                    onClick = { deleteDialog = true },
                    modifier = Modifier.weight(1f),
                ) {
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
