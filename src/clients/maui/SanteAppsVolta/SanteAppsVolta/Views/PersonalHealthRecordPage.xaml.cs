using Microsoft.Extensions.DependencyInjection;
using Volta.Core.Data.Services;

namespace Volta.Views;

public partial class PersonalHealthRecordPage : ContentPage
{
    private readonly IAppDataService _dataService;

    public PersonalHealthRecordPage()
        : this(MauiProgram.Services.GetRequiredService<IAppDataService>())
    {
    }

    public PersonalHealthRecordPage(IAppDataService dataService)
    {
        InitializeComponent();
        _dataService = dataService;
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        var sections = await _dataService.GetHealthRecordSectionsAsync();
        BindableLayout.SetItemsSource(SectionsLayout, sections);
    }
}
