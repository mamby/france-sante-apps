#if ANDROID
using AndroidX.Biometric;
using AndroidX.Fragment.App;
using Java.Lang;
using Volta.Resources.Strings;
using static AndroidX.Biometric.BiometricManager;
using static AndroidX.Biometric.BiometricPrompt;

namespace Volta;

public class AndroidBiometricUnlock
{
    public event EventHandler<string>? Failed;
    public event EventHandler? Succeeded;

    public void Start()
    {
        var activity = Platform.CurrentActivity;
        if (activity is null)
        {
            Failed?.Invoke(this, AppResources.UnexpectedError);
            return;
        }

        var biometricManager = BiometricManager.From(activity);
        var canAuthenticate = biometricManager.CanAuthenticate(Authenticators.BiometricStrong | Authenticators.DeviceCredential);
        if (canAuthenticate != BiometricSuccess)
        {
            Failed?.Invoke(this, GetAvailabilityMessage(canAuthenticate));
            return;
        }

        var promptInfo = new PromptInfo.Builder()
            .SetTitle(AppResources.AndroidBiometricPromptTitle)
            .SetDescription(AppResources.BiometricPromptMessage)
            .SetConfirmationRequired(true)
            .SetAllowedAuthenticators(Authenticators.BiometricStrong | Authenticators.DeviceCredential)
            .Build();

        AuthCallback authCallback = new();
        authCallback.Failed += (s, e) => Failed?.Invoke(this, e);
        authCallback.Succeeded += (s, e) => Succeeded?.Invoke(this, e);

        new BiometricPrompt((FragmentActivity)activity, authCallback)
            .Authenticate(promptInfo);
    }

    private static string GetAvailabilityMessage(int status)
    {
        return status switch
        {
            BiometricErrorNoHardware => AppResources.AndroidBiometricNotAvailable,
            BiometricErrorHwUnavailable => AppResources.AndroidBiometricHardwareUnavailable,
            BiometricErrorNoneEnrolled => AppResources.AndroidBiometricNotEnrolled,
            BiometricErrorSecurityUpdateRequired => AppResources.AndroidBiometricSecurityUpdateRequired,
            BiometricErrorUnsupported => AppResources.AndroidBiometricUnsupported,
            _ => AppResources.AndroidBiometricNotAvailable
        };
    }
}

public class AuthCallback : AuthenticationCallback
{
    public event EventHandler<string>? Failed;
    public event EventHandler? Succeeded;

    public override void OnAuthenticationSucceeded(AuthenticationResult result)
    {
        Succeeded?.Invoke(this, EventArgs.Empty);
    }

    public override void OnAuthenticationError(int errorCode, ICharSequence errString)
    {
        if (errorCode == ErrorNegativeButton || errorCode == ErrorUserCanceled || errorCode == ErrorCanceled)
        {
            Failed?.Invoke(this, AppResources.AndroidBiometricCanceled);
            return;
        }

        if (errorCode == ErrorLockout || errorCode == ErrorLockoutPermanent)
        {
            Failed?.Invoke(this, AppResources.AndroidBiometricLockout);
            return;
        }

        Failed?.Invoke(this, errString.ToString());
    }

    public override void OnAuthenticationFailed()
    {
        Failed?.Invoke(this, AppResources.AndroidBiometricFailed);
    }
}
#endif
