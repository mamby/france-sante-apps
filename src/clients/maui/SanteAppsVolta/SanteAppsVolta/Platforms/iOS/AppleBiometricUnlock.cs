#if IOS
using Foundation;
using LocalAuthentication;
using Volta.Resources.Strings;

namespace Volta;

public class AppleBiometricUnlock
{
    public event EventHandler<string>? Failed;
    public event EventHandler? Succeeded;

    private const int LaErrorAuthenticationFailed = -1;
    private const int LaErrorUserCancel = -2;
    private const int LaErrorSystemCancel = -4;
    private const int LaErrorBiometryNotAvailable = -6;
    private const int LaErrorBiometryNotEnrolled = -7;
    private const int LaErrorBiometryLockout = -8;
    private const int LaErrorAppCancel = -9;

    public async Task StartAsync()
    {
        var context = new LAContext();
        if (!context.CanEvaluatePolicy(LAPolicy.DeviceOwnerAuthentication, out NSError? authError))
        {
            Failed?.Invoke(this, MapError(authError) ?? AppResources.AppleBiometricNotAvailable);
            return;
        }

        try
        {
            var (success, error) = await context.EvaluatePolicyAsync(
                LAPolicy.DeviceOwnerAuthentication,
                AppResources.BiometricPromptMessage);

            if (success)
            {
                Succeeded?.Invoke(this, EventArgs.Empty);
                return;
            }

            Failed?.Invoke(this, MapError(error) ?? AppResources.AppleBiometricFailed);
        }
        catch
        {
            Failed?.Invoke(this, AppResources.UnexpectedError);
        }
    }

    private static string? MapError(NSError? error)
    {
        if (error is null)
        {
            return null;
        }

        var code = (int)error.Code;
        return code switch
        {
            LaErrorBiometryNotAvailable => AppResources.AppleBiometricNotAvailable,
            LaErrorBiometryNotEnrolled => AppResources.AppleBiometricNotEnrolled,
            LaErrorBiometryLockout => AppResources.AppleBiometricLockout,
            LaErrorUserCancel => AppResources.AppleBiometricCanceled,
            LaErrorAppCancel => AppResources.AppleBiometricCanceled,
            LaErrorSystemCancel => AppResources.AppleBiometricCanceled,
            LaErrorAuthenticationFailed => AppResources.AppleBiometricFailed,
            _ => AppResources.AppleBiometricFailed
        };
    }
}
#endif
