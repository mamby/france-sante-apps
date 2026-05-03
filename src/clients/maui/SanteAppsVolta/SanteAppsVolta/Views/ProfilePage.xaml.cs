using Microsoft.Extensions.DependencyInjection;
using Volta.Core.Data.Services;

namespace Volta.Views;

public partial class ProfilePage : ContentPage
{
    private readonly IAppDataService _dataService;

    public ProfilePage()
        : this(MauiProgram.Services.GetRequiredService<IAppDataService>())
    {
    }

    public ProfilePage(IAppDataService dataService)
    {
        InitializeComponent();
        _dataService = dataService;
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        var profile = await _dataService.GetUserProfileAsync();

        FullNameLabel.Text = profile.FullName;
        EmailLabel.Text = profile.Email;
        NationalHealthIdLabel.Text = profile.NationalHealthId;
        DateOfBirthLabel.Text = profile.DateOfBirthLabel;
        BloodTypeLabel.Text = profile.BloodType;
        AddressLabel.Text = profile.Address;

        ProfileImage.Source = profile.AvatarSource;

        BindableLayout.SetItemsSource(EmergencyContactsLayout, profile.EmergencyContacts);
        BindableLayout.SetItemsSource(CareTeamLayout, profile.CareTeam);
    }
}
