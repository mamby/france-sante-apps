using System.Globalization;
using Volta.Core.Config;
using Volta.Core.L10n;
using Volta.Core.Platforms;
using Volta.Resources.Strings;

namespace Volta.Views;

public partial class LanguagePage : ContentPage
{
    private readonly List<Language> _appLanguages;
    private string? _selectedLanguageName;

    public LanguagePage()
    {
        InitializeComponent();
        Behaviors.Add(new SoftInputBehavior());

        _appLanguages = Localization.Languages();
        LangCollectionView.ItemsSource = _appLanguages;

        var currentLanguage = _appLanguages.FirstOrDefault(language => language.Name == CultureInfo.CurrentUICulture.Name);
        if (currentLanguage is not null)
        {
            LangCollectionView.SelectedItem = currentLanguage;
            _selectedLanguageName = currentLanguage.Name;
            DoneButton.IsEnabled = true;
        }
    }

    private void OnSearchEntryTextChanged(object sender, TextChangedEventArgs e)
    {
        var query = SearchEntry.Text?.Trim();
        var filteredLanguages = string.IsNullOrWhiteSpace(query)
            ? _appLanguages
            : _appLanguages
                .Where(language => language.NativeName.Contains(query, StringComparison.OrdinalIgnoreCase))
                .ToList();

        LangCollectionView.ItemsSource = filteredLanguages;

        if (_selectedLanguageName is null)
        {
            DoneButton.IsEnabled = false;
            return;
        }

        var selectedLanguage = filteredLanguages.FirstOrDefault(language => language.Name == _selectedLanguageName);
        LangCollectionView.SelectedItem = selectedLanguage;
        DoneButton.IsEnabled = selectedLanguage is not null;
    }

    private void OnLangSelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (e.CurrentSelection.FirstOrDefault() is not Language selectedLanguage)
        {
            return;
        }

        _selectedLanguageName = selectedLanguage.Name;
        DoneButton.IsEnabled = true;

        var culture = new CultureInfo(selectedLanguage.Name);
        Title = AppResources.ResourceManager.GetString(nameof(AppResources.LangPageTitle), culture);
        DoneButton.Text = AppResources.ResourceManager.GetString(nameof(AppResources.LangPageDone), culture);
        SearchEntry.Placeholder = AppResources.ResourceManager.GetString(nameof(AppResources.LangPageSearch), culture);
    }

    private async void OnDoneButtonClicked(object sender, EventArgs e)
    {
        if (string.IsNullOrWhiteSpace(_selectedLanguageName))
        {
            return;
        }

        Preferences.Set(SettingKeys.Language, _selectedLanguageName);
        Localization.SetLanguage(_selectedLanguageName);
        await Shell.Current.GoToAsync("..");
    }
}
