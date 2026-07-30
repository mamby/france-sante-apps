package net.mamby.health.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.mamby.health.di.ApplicationScope
import net.mamby.health.di.MainDispatcher

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope applicationScope: CoroutineScope,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) : SettingsRepository {
    override val settings = dataStore.data
        .catch { error ->
            if (error is IOException) {
                // A damaged settings file must never silently weaken an existing app lock.
                emit(preferencesOf(Keys.appLockEnabled to true))
            } else {
                throw error
            }
        }
        .map(::toSettings)
        .distinctUntilChanged()

    init {
        applicationScope.launch {
            settings
                .map { appSettings: AppSettings -> appSettings.localeTag }
                .distinctUntilChanged()
                .collect { localeTag: String ->
                    withContext(mainDispatcher) {
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(localeTag),
                        )
                    }
                }
        }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.themeMode] = mode.name }
    }

    override suspend fun setLocaleTag(localeTag: String) {
        require(localeTag in AppSettings.supportedLocaleTags) {
            "Unsupported locale tag"
        }
        dataStore.edit { it[Keys.localeTag] = localeTag }
    }

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.appLockEnabled] = enabled }
    }

    override suspend fun setAppLockTimeout(timeout: Duration) {
        require(!timeout.isNegative) { "App-lock timeout must not be negative" }
        dataStore.edit { it[Keys.appLockTimeoutMillis] = timeout.toMillis() }
    }

    override suspend fun setBackupConfiguration(configuration: BackupConfiguration) {
        dataStore.edit { preferences ->
            preferences[Keys.backupDestinationUri] = configuration.destinationUri
            preferences[Keys.backupScheduled] = configuration.scheduled
            preferences[Keys.backupSalt] = configuration.key.saltBase64
            preferences[Keys.backupPassphraseWrappedKey] =
                configuration.key.passphraseWrappedKeyBase64
            preferences[Keys.backupLocallyWrappedKey] = configuration.key.locallyWrappedKeyBase64
            preferences[Keys.backupIterations] = configuration.key.iterations
        }
    }

    override suspend fun setBackupStatus(status: BackupStatus) {
        dataStore.edit { preferences ->
            preferences[Keys.backupState] = status.state.name
            preferences[Keys.backupIssue] = status.issue.name
            status.lastSuccess?.let { preferences[Keys.backupLastSuccess] = it.toEpochMilli() }
                ?: preferences.remove(Keys.backupLastSuccess)
        }
    }

    override suspend fun clearBackupConfiguration() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.backupDestinationUri)
            preferences.remove(Keys.backupScheduled)
            preferences.remove(Keys.backupSalt)
            preferences.remove(Keys.backupPassphraseWrappedKey)
            preferences.remove(Keys.backupLocallyWrappedKey)
            preferences.remove(Keys.backupIterations)
            preferences[Keys.backupState] = BackupState.NOT_CONFIGURED.name
            preferences[Keys.backupIssue] = BackupIssue.NONE.name
            preferences.remove(Keys.backupLastSuccess)
        }
    }

    private fun toSettings(preferences: Preferences): AppSettings {
        val destination = preferences[Keys.backupDestinationUri]
        val backupConfiguration = destination?.let {
            val salt = preferences[Keys.backupSalt]
            val passphraseWrappedKey = preferences[Keys.backupPassphraseWrappedKey]
            val locallyWrappedKey = preferences[Keys.backupLocallyWrappedKey]
            val iterations = preferences[Keys.backupIterations]
            if (
                salt == null ||
                passphraseWrappedKey == null ||
                locallyWrappedKey == null ||
                iterations == null
            ) {
                null
            } else {
                BackupConfiguration(
                    destinationUri = destination,
                    scheduled = preferences[Keys.backupScheduled] ?: true,
                    key = BackupKeyConfiguration(
                        saltBase64 = salt,
                        passphraseWrappedKeyBase64 = passphraseWrappedKey,
                        locallyWrappedKeyBase64 = locallyWrappedKey,
                        iterations = iterations,
                    ),
                )
            }
        }

        return AppSettings(
            themeMode = preferences[Keys.themeMode]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            localeTag = preferences[Keys.localeTag]
                ?.takeIf { it in AppSettings.supportedLocaleTags }
                ?: AppSettings.DEFAULT_LOCALE_TAG,
            appLockEnabled = preferences[Keys.appLockEnabled] ?: false,
            appLockTimeout = Duration.ofMillis(
                (preferences[Keys.appLockTimeoutMillis] ?: 0L).coerceAtLeast(0L),
            ),
            backupConfiguration = backupConfiguration,
            backupStatus = BackupStatus(
                state = if (destination != null && backupConfiguration == null) {
                    BackupState.NEEDS_ATTENTION
                } else {
                    preferences[Keys.backupState]
                        ?.let { runCatching { BackupState.valueOf(it) }.getOrNull() }
                        ?: if (backupConfiguration == null) {
                            BackupState.NOT_CONFIGURED
                        } else {
                            BackupState.READY
                        }
                },
                issue = preferences[Keys.backupIssue]
                    ?.let { runCatching { BackupIssue.valueOf(it) }.getOrNull() }
                    ?: if (destination != null && backupConfiguration == null) {
                        BackupIssue.CORRUPT_CONFIGURATION
                    } else {
                        BackupIssue.NONE
                    },
                lastSuccess = preferences[Keys.backupLastSuccess]
                    ?.let(Instant::ofEpochMilli),
            ),
        )
    }

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val localeTag = stringPreferencesKey("locale_tag")
        val appLockEnabled = booleanPreferencesKey("app_lock_enabled")
        val appLockTimeoutMillis = longPreferencesKey("app_lock_timeout_millis")
        val backupDestinationUri = stringPreferencesKey("backup_destination_uri")
        val backupScheduled = booleanPreferencesKey("backup_scheduled")
        val backupSalt = stringPreferencesKey("backup_salt")
        val backupPassphraseWrappedKey = stringPreferencesKey("backup_passphrase_wrapped_key")
        val backupLocallyWrappedKey = stringPreferencesKey("backup_locally_wrapped_key")
        val backupIterations = intPreferencesKey("backup_iterations")
        val backupState = stringPreferencesKey("backup_state")
        val backupIssue = stringPreferencesKey("backup_issue")
        val backupLastSuccess = longPreferencesKey("backup_last_success")
    }
}
