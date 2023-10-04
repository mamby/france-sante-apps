using Volta.Core.Config;
using Volta.Core.L10n;
using Volta.Views;

namespace Volta;

public partial class App : Application
{
    DateTime _sleepTime;
    public double LockAfter { get; set; }

    public App()
    {
        InitializeComponent();

        LockAfter = Preferences.Get(SettingKeys.LockAfter, 15); // todo : default to 0 (Secure by default)

        if (Preferences.ContainsKey(SettingKeys.Language))
        {
            var name = Preferences.Get(SettingKeys.Language, "en-US");

            if (Localization.LanguageExist(name))
            {
                Localization.SetLanguage(name);
                InitAppLockAsync().GetAwaiter().GetResult();
            }
            else
            {
                Current.MainPage = new LanguagePage();
            }
        }
        else
        {
            Current.MainPage = new LanguagePage();
        }
    }

    private static async Task<bool> LockEnabledAsync() => true; // todo: await SecureStorage.Default.GetAsync(SettingKeys.AppLock) == "1";

    public static async Task InitAppLockAsync() 
        => Current.MainPage = await LockEnabledAsync() ? new LockPage() : new AppShell();

    protected override Window CreateWindow(IActivationState activationState)
    {
        Window window = base.CreateWindow(activationState);

        window.Deactivated += (s, e) => _sleepTime = DateTime.UtcNow;

        window.Activated += async (s, e) =>
        {
            if (await LockEnabledAsync() 
                && Current.MainPage is AppShell 
                && Shell.Current.CurrentPage is not LockPage
                && (DateTime.UtcNow - _sleepTime).TotalSeconds > LockAfter)
            {
                await Shell.Current.GoToAsync("LockPage");
            }
        };

        return window;
    }
}
