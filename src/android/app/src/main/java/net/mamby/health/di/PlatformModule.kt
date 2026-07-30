package net.mamby.health.di

import android.content.ContentResolver
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.security.SecureRandom
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import net.mamby.health.BuildConfig
import net.mamby.health.backup.AndroidBackupRepository
import net.mamby.health.backup.BackupRepository
import net.mamby.health.backup.LocalBackupKeyProtector
import net.mamby.health.backup.VaultCipherBackupKeyProtector
import net.mamby.health.notifications.AndroidNotificationPublisher
import net.mamby.health.notifications.NotificationPublisher
import net.mamby.health.notifications.ReminderScheduler
import net.mamby.health.notifications.ReminderSource
import net.mamby.health.notifications.SystemZoneIdProvider
import net.mamby.health.notifications.VaultReminderSource
import net.mamby.health.notifications.WorkManagerReminderScheduler
import net.mamby.health.notifications.ZoneIdProvider
import net.mamby.health.security.AndroidBiometricAuthenticator
import net.mamby.health.security.AppLockManager
import net.mamby.health.security.BiometricAuthenticator
import net.mamby.health.security.ProcessAppLockManager
import net.mamby.health.settings.DataStoreSettingsRepository
import net.mamby.health.settings.SettingsRepository

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EnvironmentName

@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformBindings {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        implementation: DataStoreSettingsRepository,
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindBiometricAuthenticator(
        implementation: AndroidBiometricAuthenticator,
    ): BiometricAuthenticator

    @Binds
    @Singleton
    abstract fun bindAppLockManager(implementation: ProcessAppLockManager): AppLockManager

    @Binds
    @Singleton
    abstract fun bindNotificationPublisher(
        implementation: AndroidNotificationPublisher,
    ): NotificationPublisher

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(
        implementation: WorkManagerReminderScheduler,
    ): ReminderScheduler

    @Binds
    @Singleton
    abstract fun bindReminderSource(implementation: VaultReminderSource): ReminderSource

    @Binds
    @Singleton
    abstract fun bindZoneIdProvider(implementation: SystemZoneIdProvider): ZoneIdProvider

    @Binds
    @Singleton
    abstract fun bindLocalBackupKeyProtector(
        implementation: VaultCipherBackupKeyProtector,
    ): LocalBackupKeyProtector

    @Binds
    @Singleton
    abstract fun bindBackupRepository(implementation: AndroidBackupRepository): BackupRepository
}

@Module
@InstallIn(SingletonComponent::class)
object PlatformModule {
    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler {
            preferencesOf(booleanPreferencesKey("app_lock_enabled") to true)
        },
        produceFile = { context.preferencesDataStoreFile("settings.preferences_pb") },
    )

    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideSecureRandom(): SecureRandom = SecureRandom()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
        isLenient = false
        classDiscriminator = "type"
    }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

    @Provides
    @EnvironmentName
    fun provideEnvironmentName(): String = BuildConfig.ENVIRONMENT
}
