using Volta.Core.Platforms;

namespace Volta.Views;

public partial class LockPage : ContentPage
{
	public LockPage()
	{
        InitializeComponent();
    }

    protected override bool OnBackButtonPressed()
    {
        return false;
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        BiometricLock.Open();
    }

    private void EnterBtn_Clicked(object sender, EventArgs e)
    {
        BiometricLock.Open();
    }

    private void GoBtn_Clicked(object sender, EventArgs e)
    {        
        if (App.Current.MainPage is AppShell)
        {
            Shell.Current.Navigation.PopToRootAsync();
        }
        else
        {
            App.Current.MainPage = new AppShell();
        }
    }

    public void Enter(string message)
    {
        DisplayAlert("Alert", message, "OK");
    }
}