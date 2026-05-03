using Microsoft.Extensions.DependencyInjection;
using Volta.Core.Config;
using Volta.Core.Data.Models;
using Volta.Core.Data.Services;

namespace Volta.Views;

public partial class MoreFeaturesPage : ContentPage
{
    private static readonly int[] LockAfterOptionsInSeconds = [15, 30, 60, 120, 300];

    private readonly IAppDataService _dataService;
    private bool _isLoading;
    private string _currentLanguageCode = SettingDefaults.Language;

    public MoreFeaturesPage()
        : this(MauiProgram.Services.GetRequiredService<IAppDataService>())
    {
    }

    public MoreFeaturesPage(IAppDataService dataService)
    {
        InitializeComponent();
        _dataService = dataService;

        LockAfterPicker.ItemsSource = LockAfterOptionsInSeconds
            .Select(seconds => $"{seconds} seconds")
            .ToList();
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        await LoadAsync();
    }

    private async Task LoadAsync()
    {
        _isLoading = true;

        try
        {
            var settings = await _dataService.GetSecuritySettingsAsync();
            _currentLanguageCode = settings.LanguageCode;

            AppLockSwitch.IsToggled = settings.AppLockEnabled;
            LockAfterPicker.SelectedIndex = Array.IndexOf(LockAfterOptionsInSeconds, settings.LockAfterSeconds);

            if (LockAfterPicker.SelectedIndex < 0)
            {
                LockAfterPicker.SelectedIndex = Array.IndexOf(LockAfterOptionsInSeconds, SettingDefaults.LockAfterSeconds);
            }

            await RefreshFeatureCardsAsync();
        }
        finally
        {
            _isLoading = false;
        }
    }

    private async Task RefreshFeatureCardsAsync()
    {
        var moreFeatures = await _dataService.GetMoreFeaturesSnapshotAsync();
        BindableLayout.SetItemsSource(FeatureItemsLayout, moreFeatures.Items);
    }

    private async Task SaveCurrentSettingsAsync()
    {
        if (LockAfterPicker.SelectedIndex < 0)
        {
            return;
        }

        var selectedLockAfterSeconds = LockAfterOptionsInSeconds[LockAfterPicker.SelectedIndex];
        var settings = new AppSecuritySettings(AppLockSwitch.IsToggled, selectedLockAfterSeconds, _currentLanguageCode);

        await _dataService.SaveSecuritySettingsAsync(settings);
        await RefreshFeatureCardsAsync();
    }

    private async void OnAppLockSwitchToggled(object sender, ToggledEventArgs e)
    {
        if (_isLoading)
        {
            return;
        }

        await SaveCurrentSettingsAsync();
    }

    private async void OnLockAfterPickerSelectedIndexChanged(object sender, EventArgs e)
    {
        if (_isLoading)
        {
            return;
        }

        await SaveCurrentSettingsAsync();
    }

    private async void OnLanguageButtonClicked(object sender, EventArgs e)
    {
        await Shell.Current.GoToAsync(nameof(LanguagePage));
        _currentLanguageCode = Preferences.Get(SettingKeys.Language, SettingDefaults.Language);
    }

    private async void OnLockNowButtonClicked(object sender, EventArgs e)
    {
        await Shell.Current.GoToAsync(nameof(LockPage));
    }

    private async void OnSignOutButtonClicked(object sender, EventArgs e)
    {
        await _dataService.SignOutAsync();
        await RefreshFeatureCardsAsync();

        await DisplayAlertAsync("Signed out", "Mock session has been reset.", "OK");
        await Shell.Current.GoToAsync("//home");
    }
}

