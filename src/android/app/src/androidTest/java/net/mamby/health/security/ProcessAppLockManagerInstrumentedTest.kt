package net.mamby.health.security

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import net.mamby.health.settings.AppSettings
import net.mamby.health.settings.BackupConfiguration
import net.mamby.health.settings.BackupStatus
import net.mamby.health.settings.SettingsRepository
import net.mamby.health.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessAppLockManagerInstrumentedTest {
    @Test
    fun processLifecycle_locksAtTimeoutAndFailsClosedWhenTheClockMovesBackwards() = runBlocking {
        val timeout = Duration.ofMinutes(5)
        val clock = MutableClock(Instant.parse("2026-07-30T10:00:00Z"))
        val settingsRepository = FakeSettingsRepository(
            AppSettings(appLockEnabled = true, appLockTimeout = timeout),
        )
        val authenticator = SuccessfulAuthenticator()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var manager: ProcessAppLockManager
        lateinit var activity: FragmentActivity

        instrumentation.runOnMainSync {
            manager = ProcessAppLockManager(
                settingsRepository = settingsRepository,
                authenticator = authenticator,
                clock = clock,
                applicationScope = scope,
            )
            activity = FragmentActivity()
        }

        try {
            assertEquals(AppLockState.Locked, manager.state.value)
            assertEquals(UnlockResult.Success, manager.unlock(activity))
            assertEquals(AppLockState.Unlocked, manager.state.value)

            manager.onStop(TestLifecycleOwner())
            clock.advance(timeout.minusSeconds(1))
            manager.onStart(TestLifecycleOwner())
            assertEquals(AppLockState.Unlocked, manager.state.value)

            manager.onStop(TestLifecycleOwner())
            clock.advance(timeout)
            manager.onStart(TestLifecycleOwner())
            assertEquals(AppLockState.Locked, manager.state.value)

            assertEquals(UnlockResult.Success, manager.unlock(activity))
            manager.onStop(TestLifecycleOwner())
            clock.moveBackwards(Duration.ofSeconds(1))
            manager.onStart(TestLifecycleOwner())
            assertEquals(AppLockState.Locked, manager.state.value)

            assertEquals(UnlockResult.Success, manager.unlock(activity))
            settingsRepository.setAppLockTimeout(Duration.ZERO)
            manager.onStop(TestLifecycleOwner())
            assertEquals(AppLockState.Locked, manager.state.value)
            assertEquals(3, authenticator.authenticationCount)
        } finally {
            instrumentation.runOnMainSync {
                ProcessLifecycleOwner.get().lifecycle.removeObserver(manager)
            }
            scope.cancel()
        }
    }
}

private class TestLifecycleOwner : LifecycleOwner {
    override val lifecycle: Lifecycle = LifecycleRegistry(this)
}

private class SuccessfulAuthenticator : BiometricAuthenticator {
    var authenticationCount = 0
        private set

    override fun availability(): AuthenticationAvailability = AuthenticationAvailability.Available

    override suspend fun authenticate(activity: FragmentActivity): UnlockResult {
        authenticationCount++
        return UnlockResult.Success
    }
}

private class FakeSettingsRepository(initial: AppSettings) : SettingsRepository {
    private val mutableSettings = MutableStateFlow(initial)

    override val settings = mutableSettings

    override fun previewFloatingSurfaceOpacityLevel(level: Float) {}

    override suspend fun saveFloatingSurfaceOpacityLevel() {}

    override suspend fun setThemeMode(mode: ThemeMode) {
        mutableSettings.update { it.copy(themeMode = mode) }
    }

    override suspend fun setLocaleTag(localeTag: String) {
        // Locale selection is owned by AppCompat rather than AppSettings.
    }

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        mutableSettings.update { it.copy(appLockEnabled = enabled) }
    }

    override suspend fun setAppLockTimeout(timeout: Duration) {
        mutableSettings.update { it.copy(appLockTimeout = timeout) }
    }

    override suspend fun setBackupConfiguration(configuration: BackupConfiguration) {
        mutableSettings.update { it.copy(backupConfiguration = configuration) }
    }

    override suspend fun setBackupStatus(status: BackupStatus) {
        mutableSettings.update { it.copy(backupStatus = status) }
    }

    override suspend fun clearBackupConfiguration() {
        mutableSettings.update {
            it.copy(backupConfiguration = null, backupStatus = BackupStatus())
        }
    }
}

private class MutableClock(
    private var current: Instant,
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

    override fun instant(): Instant = current

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }

    fun moveBackwards(duration: Duration) {
        current = current.minus(duration)
    }
}
