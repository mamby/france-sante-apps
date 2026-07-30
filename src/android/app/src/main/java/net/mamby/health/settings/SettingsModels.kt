package net.mamby.health.settings

import java.time.Duration
import java.time.Instant

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class BackupState {
    NOT_CONFIGURED,
    READY,
    RUNNING,
    SUCCEEDED,
    NEEDS_ATTENTION,
}

enum class BackupIssue {
    NONE,
    DESTINATION_PERMISSION_LOST,
    DESTINATION_UNAVAILABLE,
    VAULT_UNAVAILABLE,
    IO_FAILURE,
    CORRUPT_CONFIGURATION,
}

data class BackupKeyConfiguration(
    val saltBase64: String,
    val passphraseWrappedKeyBase64: String,
    val locallyWrappedKeyBase64: String,
    val iterations: Int,
)

data class BackupConfiguration(
    val destinationUri: String,
    val scheduled: Boolean,
    val key: BackupKeyConfiguration,
)

data class BackupStatus(
    val state: BackupState = BackupState.NOT_CONFIGURED,
    val issue: BackupIssue = BackupIssue.NONE,
    val lastSuccess: Instant? = null,
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val localeTag: String = DEFAULT_LOCALE_TAG,
    val appLockEnabled: Boolean = false,
    val appLockTimeout: Duration = Duration.ZERO,
    val backupConfiguration: BackupConfiguration? = null,
    val backupStatus: BackupStatus = BackupStatus(),
) {
    companion object {
        const val DEFAULT_LOCALE_TAG = ""
        val supportedLocaleTags: Set<String> = setOf(DEFAULT_LOCALE_TAG, "en", "fr", "ar")
    }
}
