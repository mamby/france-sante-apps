package net.mamby.health.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import net.mamby.health.R

@Singleton
class AndroidBiometricAuthenticator @Inject constructor(
    @ApplicationContext private val context: Context,
) : BiometricAuthenticator {
    override fun availability(): AuthenticationAvailability {
        val result = BiometricManager.from(context).canAuthenticate(authenticatorTypes())
        return when (result) {
            BiometricManager.BIOMETRIC_SUCCESS -> AuthenticationAvailability.Available
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                AuthenticationAvailability.Unavailable(
                    AuthenticationUnavailableReason.HARDWARE_UNAVAILABLE,
                )
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                AuthenticationAvailability.Unavailable(
                    AuthenticationUnavailableReason.SECURITY_UPDATE_REQUIRED,
                )
            else -> AuthenticationAvailability.Unavailable(
                AuthenticationUnavailableReason.NO_SUPPORTED_AUTHENTICATOR,
            )
        }
    }

    override suspend fun authenticate(activity: FragmentActivity): UnlockResult {
        val unavailable = availability() as? AuthenticationAvailability.Unavailable
        if (unavailable != null) {
            return UnlockResult.Unavailable(unavailable.reason)
        }

        return suspendCancellableCoroutine { continuation ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult,
                    ) {
                        if (continuation.isActive) continuation.resume(UnlockResult.Success)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (!continuation.isActive) return
                        val result = when (errorCode) {
                            BiometricPrompt.ERROR_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            BiometricPrompt.ERROR_USER_CANCELED,
                            -> UnlockResult.Cancelled
                            BiometricPrompt.ERROR_HW_NOT_PRESENT,
                            BiometricPrompt.ERROR_NO_BIOMETRICS,
                            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                            -> UnlockResult.Unavailable(
                                AuthenticationUnavailableReason.NO_SUPPORTED_AUTHENTICATOR,
                            )
                            BiometricPrompt.ERROR_HW_UNAVAILABLE -> UnlockResult.Unavailable(
                                AuthenticationUnavailableReason.HARDWARE_UNAVAILABLE,
                            )
                            BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED ->
                                UnlockResult.Unavailable(
                                    AuthenticationUnavailableReason.SECURITY_UPDATE_REQUIRED,
                                )
                            else -> UnlockResult.Failed(errorCode)
                        }
                        continuation.resume(result)
                    }
                },
            )

            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
            prompt.authenticate(promptInfo())
        }
    }

    private fun promptInfo(): BiometricPrompt.PromptInfo {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.unlock_title))
            .setSubtitle(context.getString(R.string.unlock_subtitle))
            .setAllowedAuthenticators(authenticatorTypes())
            .setConfirmationRequired(true)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // The strong-biometric/device-credential combination is not supported by
            // AndroidX Biometric below API 30. Strong biometric is the modern supported path.
            builder.setNegativeButtonText(context.getString(R.string.unlock_cancel))
        }
        return builder.build()
    }

    private fun authenticatorTypes(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }
}
