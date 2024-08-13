using Volta.Core.Config;
using Volta.Core.L10n;
using Volta.Views;

namespace Volta;

public partial class App : Application
{
    DateTime _pauseTime;

    public App()
    {
        InitializeComponent();
        InitializeLanguage();
    }

    private static void InitializeLanguage()
    {
        if (!Preferences.ContainsKey(SettingKeys.Language))
        {
           return; // use default mechanism.
        }

        Localization.SetLanguage(Preferences.Get(SettingKeys.Language, SettingDefaults.Language));
    }

    protected override Window CreateWindow(IActivationState? activationState)
    {
        var window = new Window(new AppShell());

        window.Deactivated += (s, e) => _pauseTime = DateTime.UtcNow;

        window.Activated += async (s, e) =>
        {
            if (await SecureStorage.Default.GetAsync(SettingKeys.AppLock) != "1" // todo : change to ==
                && (DateTime.UtcNow - _pauseTime).TotalSeconds > Preferences.Get(SettingKeys.LockAfter, 0))
            {
                if (Shell.Current.CurrentPage is not LockPage)
                    await Shell.Current.GoToAsync("LockPage");

                // WINDOWS : The login prompt deactivate the current Window (Never ending prompts).
#if ANDROID
                await (Shell.Current.CurrentPage as LockPage)?.StartUnlockAsync();
#endif
            }
        };

        return window;
    }
}