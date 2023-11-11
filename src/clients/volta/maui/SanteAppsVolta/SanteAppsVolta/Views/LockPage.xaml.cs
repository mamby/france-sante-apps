using Volta.Core.Platforms;
using Volta.Resources.Strings;

namespace Volta.Views;

public partial class LockPage : ContentPage
{
    readonly BiometricUnlock _biometricUnlock;

    public LockPage()
	{
        InitializeComponent();
        _biometricUnlock = new BiometricUnlock();
        _biometricUnlock.Failed += (s, e) => ErrorLabel.Text = e;
        _biometricUnlock.Succeeded += (s, e) => Shell.Current.Navigation.PopAsync();

        LogoutButton.Text = AppResources.LockPageLogout;
        UnlockButton.Text = AppResources.LockPageUnlock;

        // todo: userProfile
        FullNameLabel.Text = "Marianne";
        EmailLabel.Text = "marianne@example.com";
        ProfileImage.Source = "marianne.png";
    }

    protected override bool OnBackButtonPressed() => false;

    protected override void OnAppearing()
    {
        base.OnAppearing();
        StartUnlock();
    }

    private void OnUnlockButtonClicked(object sender, EventArgs e)
    {
        StartUnlock();
    }

    private void OnLogoutButtonClicked(object sender, EventArgs e)
    {
       // todo: init logout
    }

    public void StartUnlock()
    {
        ErrorLabel.Text = "";
        _biometricUnlock.Start();
    }
}