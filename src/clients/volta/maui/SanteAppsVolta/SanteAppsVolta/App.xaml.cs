using Volta.Core.L10n;
using Volta.Views;

namespace Volta;

public partial class App : Application
{
    public App()
    {
        InitializeComponent();

        if (Preferences.ContainsKey(nameof(Language)))
        {
            var name = Preferences.Get(nameof(Language), "en-US");

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

    private static async Task<bool> CanLockAsync() => true; // todo: await SecureStorage.Default.GetAsync("is_locked") == "1";

    public static async Task InitAppLockAsync() 
        => Current.MainPage = await CanLockAsync() ? new LockPage() : new AppShell();

    protected override Window CreateWindow(IActivationState activationState)
    {
        Window window = base.CreateWindow(activationState);

        window.Activated += async (s, e) =>
        {
            if (await CanLockAsync() && Current.MainPage is AppShell && Shell.Current.CurrentPage is not LockPage)
            {
                // todo : add a timer !? lock after xx sec ??
                await Shell.Current.GoToAsync("LockPage");
            }
        };

        return window;
    }
}
