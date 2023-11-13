using Volta.Resources.Strings;
using Windows.Security.Credentials.UI;

namespace Volta;

public class WinBiometricUnlock
{
    public event EventHandler<string>? Failed;
    public event EventHandler? Succeeded;

    public async Task StartAsync()
    {
        var success = false;
        string errorMessage = "";

        try
        {
            if (await UserConsentVerifier.CheckAvailabilityAsync() == UserConsentVerifierAvailability.Available)
            {
                var consentResult = await UserConsentVerifier.RequestVerificationAsync(AppResources.BiometricPromptMessage);

                switch (consentResult)
                {
                    case UserConsentVerificationResult.Verified:
                        success = true;
                        break;
                    case UserConsentVerificationResult.DeviceNotPresent:
                        errorMessage = AppResources.WinBiometricNotPresent;
                        break;
                    case UserConsentVerificationResult.NotConfiguredForUser:
                        errorMessage = AppResources.WinBiometricNotRegistered;
                        break;
                    case UserConsentVerificationResult.DisabledByPolicy:
                        errorMessage = AppResources.WinBiometricDisabled;
                        break;
                    case UserConsentVerificationResult.DeviceBusy:
                        errorMessage = AppResources.WinBiometricBusy;
                        break;
                    case UserConsentVerificationResult.RetriesExhausted:
                        break;
                    case UserConsentVerificationResult.Canceled:
                        break;
                    default:
                        errorMessage = AppResources.WinBiometricNotAvailable;
                        break;
                }
            }
            else
            {
                errorMessage = AppResources.WinBiometricNotAvailable;
            }
        }
        catch
        {
            errorMessage = AppResources.UnexpectedError;
        }

        if (success)
        {
            Succeeded?.Invoke(this, EventArgs.Empty);
        }
        else
        {
            Failed?.Invoke(this, errorMessage);
        }
    }
}