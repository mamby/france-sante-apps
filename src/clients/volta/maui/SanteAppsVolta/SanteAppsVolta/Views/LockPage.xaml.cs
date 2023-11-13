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
        _biometricUnlock.Failed += BiometricUnlockFailed;
        _biometricUnlock.Succeeded += BiometricUnlockSucceeded;

        LogoutButton.Text = AppResources.LockPageLogout;
        UnlockButton.Text = AppResources.LockPageUnlock;

        // todo: userProfile
        FullNameLabel.Text = "Marianne";
        EmailLabel.Text = "marianne@example.com";
        ProfileImage.Source = "marianne.png";
    }

    protected override bool OnBackButtonPressed() => false;

    private async void BiometricUnlockSucceeded(object? sender, EventArgs e) => await Shell.Current.Navigation.PopAsync();

    private void BiometricUnlockFailed(object? sender, string e)
    {
        ErrorLabel.Text = e;
        UnlockButton.IsEnabled = true;
    }

    private async void OnUnlockButtonClicked(object sender, EventArgs e) => await StartUnlockAsync();

    private void OnLogoutButtonClicked(object sender, EventArgs e)
    {
       // todo: init logout
    }

    public async Task StartUnlockAsync()
    {
        ErrorLabel.Text = "";
        UnlockButton.IsEnabled = false;
        await _biometricUnlock.StartAsync();
    }
}