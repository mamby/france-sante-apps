package net.mamby.health

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.mamby.health.backup.BackupRepository
import net.mamby.health.data.VaultRepository
import net.mamby.health.di.ApplicationScope
import net.mamby.health.notifications.ReminderScheduler
import net.mamby.health.security.AppLockManager

@HiltAndroidApp
class HealthVaultApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var appLockManager: AppLockManager

    @Inject
    lateinit var backupRepository: BackupRepository

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    lateinit var vaultRepository: VaultRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Construct process-scoped observers before any activity or background worker needs them.
        appLockManager.state
        backupRepository.status
        reminderScheduler
        applicationScope.launch { vaultRepository.initialize() }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
