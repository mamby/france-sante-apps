using Volta.Views;

namespace Volta;

public partial class AppShell : Shell
{
    public AppShell()
    {
        InitializeComponent();

        Routing.RegisterRoute(nameof(LockPage), typeof(LockPage));
        Routing.RegisterRoute(nameof(LanguagePage), typeof(LanguagePage));
    }
}
