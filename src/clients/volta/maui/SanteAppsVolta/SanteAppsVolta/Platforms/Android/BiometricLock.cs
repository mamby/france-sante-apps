using AndroidX.Biometric;
using AndroidX.Fragment.App;
using Java.Lang;

namespace Volta.Core.Platforms;

public static partial class BiometricLock
{
    static partial void Prompt()
    {
        var promptInfo = new BiometricPrompt.PromptInfo.Builder()
            .SetTitle("Volta Biometric Login")
            .SetSubtitle("Log in using your biometric credential")
            .SetDescription("Please authenticate yourself here")
            .SetNegativeButtonText("Cancel")
            .SetConfirmationRequired(true)
            .SetAllowedAuthenticators(BiometricManager.Authenticators.BiometricStrong)
            .Build();

        new BiometricPrompt((FragmentActivity)Platform.CurrentActivity, new AuthCallback())
            .Authenticate(promptInfo);
    }
}

public class AuthCallback : BiometricPrompt.AuthenticationCallback
{
    public override void OnAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result)
    {
        // Handle successful authentication here
        //(App.Current.MainPage as LockPage).Enter(result.AuthenticationType.ToString());
    }

    public override void OnAuthenticationFailed()
    {
        //(App.Current.MainPage as LockPage).Enter("Echec");
    }

    public override void OnAuthenticationError(int errorCode, ICharSequence errString)
    {
        //(App.Current.MainPage as LockPage).Enter(errString.ToString());
    }
}