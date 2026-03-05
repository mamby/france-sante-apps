using Microsoft.Extensions.DependencyInjection;
using Volta.Core.Data.Services;

namespace Volta.Views;

public partial class HomePage : ContentPage
{
    private readonly IAppDataService _dataService;

    public HomePage()
        : this(MauiProgram.Services.GetRequiredService<IAppDataService>())
    {
    }

    public HomePage(IAppDataService dataService)
    {
        InitializeComponent();
        _dataService = dataService;
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        await LoadAsync();
    }

    private async Task LoadAsync()
    {
        var homeData = await _dataService.GetHomeSnapshotAsync();

        GreetingLabel.Text = homeData.Greeting;
        SummaryLabel.Text = homeData.Summary;

        BindableLayout.SetItemsSource(MetricsLayout, homeData.Metrics);
        BindableLayout.SetItemsSource(AppointmentsLayout, homeData.UpcomingAppointments);
        BindableLayout.SetItemsSource(MedicationsLayout, homeData.MedicationReminders);
    }
}
