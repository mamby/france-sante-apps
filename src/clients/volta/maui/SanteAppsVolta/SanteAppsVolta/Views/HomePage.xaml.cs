using CommunityToolkit.Maui.Behaviors;
using CommunityToolkit.Maui.Core;
using System.Globalization;
using Volta.Core.L10n;
using Volta.Resources.Strings;

namespace Volta.Views;

public partial class HomePage : ContentPage
{
    public HomePage()
    {
        InitializeComponent();
    }

    private void OnCounterClicked(object sender, EventArgs e)
    {
        CounterBtn.Text = Shell.Current.CurrentItem.Icon.ToString();
        Shell.Current.CurrentItem.Icon = "unlock.png";
    }
}
