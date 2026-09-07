package net.mamby.health.security

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import kotlinx.coroutines.flow.StateFlow

sealed interface AppLockState {
    data object Initializing : AppLockState

    data object Disabled : AppLockState

    data object Locked : AppLockState

    data object Authenticating : AppLockState

    data object Unlocked : AppLockState
}

sealed interface UnlockResult {
    data object Success : UnlockResult

    data object Cancelled : UnlockResult

    data class Unavailable(val reason: AuthenticationUnavailableReason) : UnlockResult

    data class Failed(val errorCode: Int) : UnlockResult
}

enum class AuthenticationUnavailableReason {
    NO_SUPPORTED_AUTHENTICATOR,
    HARDWARE_UNAVAILABLE,
    SECURITY_UPDATE_REQUIRED,
}

interface BiometricAuthenticator {
    fun availability(): AuthenticationAvailability

    suspend fun authenticate(activity: FragmentActivity): UnlockResult
}

sealed interface AuthenticationAvailability {
    data object Available : AuthenticationAvailability

    data class Unavailable(val reason: AuthenticationUnavailableReason) :
        AuthenticationAvailability
}

interface AppLockManager : DefaultLifecycleObserver {
    val state: StateFlow<AppLockState>

    suspend fun enable(activity: FragmentActivity): UnlockResult

    suspend fun disable()

    suspend fun unlock(activity: FragmentActivity): UnlockResult

    fun lock()
}
