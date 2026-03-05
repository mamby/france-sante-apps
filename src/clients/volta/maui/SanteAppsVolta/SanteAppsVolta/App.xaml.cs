using Volta.Core.Config;
using Volta.Core.L10n;
using Volta.Views;

namespace Volta;

public partial class App : Application
{
    private DateTime _pauseTime = DateTime.UtcNow;

    public App()
    {
        InitializeComponent();
        InitializeLanguage();
    }

    private static void InitializeLanguage()
    {
        if (!Preferences.ContainsKey(SettingKeys.Language))
        {
            return;
        }

        Localization.SetLanguage(Preferences.Get(SettingKeys.Language, SettingDefaults.Language));
    }

    protected override Window CreateWindow(IActivationState? activationState)
    {
        var window = new Window(new AppShell());

        window.Deactivated += (s, e) => _pauseTime = DateTime.UtcNow;

        window.Activated += async (s, e) =>
        {
            var appLockEnabled = await IsAppLockEnabledAsync();
            var lockAfterSeconds = Preferences.Get(SettingKeys.LockAfter, SettingDefaults.LockAfterSeconds);

            if (appLockEnabled && (DateTime.UtcNow - _pauseTime).TotalSeconds > lockAfterSeconds)
            {
                if (Shell.Current.CurrentPage is not LockPage)
                {
                    await Shell.Current.GoToAsync(nameof(LockPage));
                }

                // WINDOWS : The login prompt deactivate the current Window (Never ending prompts).
#if ANDROID
                await (Shell.Current.CurrentPage as LockPage)?.StartUnlockAsync();
#endif
            }
        };

        return window;
    }

    private static async Task<bool> IsAppLockEnabledAsync()
    {
        try
        {
            var secureValue = await SecureStorage.Default.GetAsync(SettingKeys.AppLock);
            if (!string.IsNullOrWhiteSpace(secureValue))
            {
                return secureValue == "1";
            }
        }
        catch
        {
            // Fallback to Preferences when SecureStorage is unavailable.
        }

        return Preferences.Get(SettingKeys.AppLock, SettingDefaults.AppLockState) == "1";
    }
}
