package net.mamby.health.security

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.mamby.health.di.ApplicationScope
import net.mamby.health.settings.AppSettings
import net.mamby.health.settings.SettingsRepository

@Singleton
class ProcessAppLockManager @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authenticator: BiometricAuthenticator,
    private val clock: Clock,
    @ApplicationScope applicationScope: CoroutineScope,
) : AppLockManager, DefaultLifecycleObserver {
    private val monitor = Any()
    private val authenticationMutex = Mutex()
    private val mutableState = MutableStateFlow<AppLockState>(AppLockState.Initializing)

    private var settings: AppSettings? = null
    private var authenticated = false
    private var backgroundedAt: Instant? = null

    override val state: StateFlow<AppLockState> = mutableState.asStateFlow()

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        applicationScope.launch {
            settingsRepository.settings.collect(::onSettingsChanged)
        }
    }

    override suspend fun enable(activity: FragmentActivity): UnlockResult =
        authenticationMutex.withLock {
            if (synchronized(monitor) { settings?.appLockEnabled == true }) {
                return@withLock authenticateAndUpdateState(activity)
            }

            mutableState.value = AppLockState.Authenticating
            val result = authenticator.authenticate(activity)
            if (result !is UnlockResult.Success) {
                mutableState.value = AppLockState.Disabled
                return@withLock result
            }

            val previousSettings = synchronized(monitor) {
                val current = settings ?: AppSettings()
                settings = current.copy(appLockEnabled = true)
                authenticated = true
                backgroundedAt = null
                mutableState.value = AppLockState.Unlocked
                current
            }
            try {
                settingsRepository.setAppLockEnabled(true)
                UnlockResult.Success
            } catch (error: Exception) {
                synchronized(monitor) {
                    settings = previousSettings
                    authenticated = false
                    mutableState.value = AppLockState.Disabled
                }
                throw error
            }
        }

    override suspend fun disable() {
        settingsRepository.setAppLockEnabled(false)
        synchronized(monitor) {
            settings = (settings ?: AppSettings()).copy(appLockEnabled = false)
            authenticated = false
            backgroundedAt = null
            mutableState.value = AppLockState.Disabled
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        synchronized(monitor) {
            val currentSettings = settings ?: return
            if (!currentSettings.appLockEnabled) {
                authenticated = false
                backgroundedAt = null
                mutableState.value = AppLockState.Disabled
                return
            }

            val backgroundInstant = backgroundedAt
            val timeoutExpired = backgroundInstant != null && hasExpired(
                backgroundInstant = backgroundInstant,
                timeout = currentSettings.appLockTimeout,
            )
            backgroundedAt = null
            if (!authenticated || timeoutExpired) {
                authenticated = false
                mutableState.value = AppLockState.Locked
            } else {
                mutableState.value = AppLockState.Unlocked
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        synchronized(monitor) {
            val currentSettings = settings ?: return
            if (!currentSettings.appLockEnabled) return

            backgroundedAt = clock.instant()
            if (currentSettings.appLockTimeout.isZero) {
                authenticated = false
                mutableState.value = AppLockState.Locked
            }
        }
    }

    override suspend fun unlock(activity: FragmentActivity): UnlockResult =
        authenticationMutex.withLock {
            val enabled = synchronized(monitor) { settings?.appLockEnabled == true }
            if (!enabled) {
                mutableState.value = AppLockState.Disabled
                return@withLock UnlockResult.Success
            }

            authenticateAndUpdateState(activity)
        }

    override fun lock() {
        synchronized(monitor) {
            authenticated = false
            backgroundedAt = null
            mutableState.value = if (settings?.appLockEnabled == true) {
                AppLockState.Locked
            } else {
                AppLockState.Disabled
            }
        }
    }

    private fun onSettingsChanged(updated: AppSettings) {
        synchronized(monitor) {
            val wasEnabled = settings?.appLockEnabled == true
            settings = updated
            when {
                !updated.appLockEnabled -> {
                    authenticated = false
                    backgroundedAt = null
                    mutableState.value = AppLockState.Disabled
                }
                !wasEnabled -> {
                    authenticated = false
                    backgroundedAt = null
                    mutableState.value = AppLockState.Locked
                }
                !authenticated -> mutableState.value = AppLockState.Locked
            }
        }
    }

    private suspend fun authenticateAndUpdateState(activity: FragmentActivity): UnlockResult {
        mutableState.value = AppLockState.Authenticating
        val result = authenticator.authenticate(activity)
        synchronized(monitor) {
            if (result is UnlockResult.Success) {
                authenticated = true
                backgroundedAt = null
                mutableState.value = AppLockState.Unlocked
            } else {
                authenticated = false
                mutableState.value = AppLockState.Locked
            }
        }
        return result
    }

    private fun hasExpired(backgroundInstant: Instant, timeout: Duration): Boolean {
        if (timeout.isZero) return true
        val now = clock.instant()
        if (now.isBefore(backgroundInstant)) return true
        return Duration.between(backgroundInstant, now) >= timeout
    }
}
