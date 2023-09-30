using Volta.Views;

namespace Volta;

public partial class AppShell : Shell
{
    public AppShell()
    {
        InitializeComponent();

        Routing.RegisterRoute("LockPage", typeof(LockPage));
    }
}
