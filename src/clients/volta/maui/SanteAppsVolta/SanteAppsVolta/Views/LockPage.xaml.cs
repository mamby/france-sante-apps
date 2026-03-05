using Microsoft.Extensions.DependencyInjection;
using Volta.Core.Data.Services;
using Volta.Core.Platforms;
using Volta.Resources.Strings;

namespace Volta.Views;

public partial class LockPage : ContentPage
{
    private readonly BiometricUnlock _biometricUnlock;
    private readonly IAppDataService _dataService;

    public LockPage()
        : this(MauiProgram.Services.GetRequiredService<IAppDataService>())
    {
    }

    public LockPage(IAppDataService dataService)
    {
        InitializeComponent();

        _dataService = dataService;

        _biometricUnlock = new BiometricUnlock();
        _biometricUnlock.Failed += BiometricUnlockFailed;
        _biometricUnlock.Succeeded += BiometricUnlockSucceeded;

        LogoutButton.Text = AppResources.LockPageLogout;
        UnlockButton.Text = AppResources.LockPageUnlock;
    }

    protected override bool OnBackButtonPressed() => false;

    protected override async void OnAppearing()
    {
        base.OnAppearing();

        var profile = await _dataService.GetUserProfileAsync();
        FullNameLabel.Text = profile.FullName;
        EmailLabel.Text = profile.Email;
        ProfileImage.Source = profile.AvatarSource;
    }

    private async void BiometricUnlockSucceeded(object? sender, EventArgs e)
    {
        await Shell.Current.Navigation.PopAsync();
    }

    private void BiometricUnlockFailed(object? sender, string message)
    {
        ErrorLabel.Text = message;
        UnlockButton.IsEnabled = true;
    }

    private async void OnUnlockButtonClicked(object sender, EventArgs e)
    {
        await StartUnlockAsync();
    }

    private async void OnLogoutButtonClicked(object sender, EventArgs e)
    {
        await _dataService.SignOutAsync();
        await Shell.Current.Navigation.PopAsync();
        await Shell.Current.GoToAsync("//home");
    }

    public async Task StartUnlockAsync()
    {
        ErrorLabel.Text = string.Empty;
        UnlockButton.IsEnabled = false;
        await _biometricUnlock.StartAsync();
    }
}
