package net.mamby.health.settings

import java.time.Duration
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    fun previewFloatingSurfaceOpacityLevel(level: Float)

    suspend fun saveFloatingSurfaceOpacityLevel()

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setLocaleTag(localeTag: String)

    suspend fun setAppLockEnabled(enabled: Boolean)

    suspend fun setAppLockTimeout(timeout: Duration)

    suspend fun setBackupConfiguration(configuration: BackupConfiguration)

    suspend fun setBackupStatus(status: BackupStatus)

    suspend fun clearBackupConfiguration()
}
