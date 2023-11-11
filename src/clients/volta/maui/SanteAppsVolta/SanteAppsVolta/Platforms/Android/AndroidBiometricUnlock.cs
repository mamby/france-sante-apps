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
        var promptInfo = new PromptInfo.Builder()
            .SetTitle(AppResources.AndroidBiometricTitle)
            .SetDescription(AppResources.AndroidBiometricDescription)
            .SetNegativeButtonText(AppResources.AndroidBiometricNegativeText)
            .SetConfirmationRequired(true)
            .SetAllowedAuthenticators(Authenticators.BiometricStrong)
            .Build();

        AuthCallback authCallback = new();
        authCallback.Failed += (s, e) => Failed?.Invoke(this, e);
        authCallback.Succeeded += (s, e) => Succeeded?.Invoke(this, e);

        new BiometricPrompt((FragmentActivity)Platform.CurrentActivity!, authCallback)
            .Authenticate(promptInfo);
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
        if (errorCode != 10 && errorCode != 13) // Canceled by user.
            Failed?.Invoke(this, errString.ToString());
    }
}