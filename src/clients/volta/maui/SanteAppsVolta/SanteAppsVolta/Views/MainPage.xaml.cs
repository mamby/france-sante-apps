using CommunityToolkit.Maui.Behaviors;
using CommunityToolkit.Maui.Core;
using System.Globalization;
using Volta.Core.L10n;
using Volta.Resources.Strings;

namespace Volta.Views;

public partial class MainPage : ContentPage
{
    public MainPage()
    {
        InitializeComponent();
    }

    private void OnCounterClicked(object sender, EventArgs e)
    {
        CounterBtn.Text = DateTime.UtcNow.ToLongDateString();
    }
}
