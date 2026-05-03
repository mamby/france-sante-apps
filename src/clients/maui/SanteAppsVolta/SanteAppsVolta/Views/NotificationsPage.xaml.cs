using Microsoft.Extensions.DependencyInjection;
using Volta.Core.Data.Services;

namespace Volta.Views;

public partial class NotificationsPage : ContentPage
{
    private readonly IAppDataService _dataService;

    public NotificationsPage()
        : this(MauiProgram.Services.GetRequiredService<IAppDataService>())
    {
    }

    public NotificationsPage(IAppDataService dataService)
    {
        InitializeComponent();
        _dataService = dataService;
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        var notifications = await _dataService.GetNotificationsAsync();
        BindableLayout.SetItemsSource(NotificationsLayout, notifications);
    }
}
