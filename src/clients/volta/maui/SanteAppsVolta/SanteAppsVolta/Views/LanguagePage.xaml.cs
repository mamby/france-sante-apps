using System.Globalization;
using Volta.Core.L10n;
using Volta.Core.Platforms;
using Volta.Resources.Strings;

namespace Volta.Views;

public partial class LanguagePage : ContentPage
{
    private readonly List<Language> _appLanguages;

    public LanguagePage()
    {
        InitializeComponent();

        Behaviors.Add(new SoftInputBehavior());

        _appLanguages = Localization.Languages();
        LangListView.ItemsSource = _appLanguages;
    }

    void OnSearchTextChanged(object sender, EventArgs e)
    {
        DoneButton.IsEnabled = false;
        LangListView.ItemsSource = _appLanguages
            .Where(l => l.NativeName.Contains(SearchBar.Text, StringComparison.OrdinalIgnoreCase))
            .ToList();
    }

    private void OnLangItemTapped(object sender, ItemTappedEventArgs e)
    {
        SearchBar.Unfocus();
        DoneButton.IsEnabled = true;
        CultureInfo selectedLang = new((LangListView.SelectedItem as Language)!.Name);
        Title = AppResources.ResourceManager.GetString(nameof(AppResources.LangPageTitle), selectedLang);
        DoneButton.Text = AppResources.ResourceManager.GetString(nameof(AppResources.LangPageDone), selectedLang);
        SearchBar.Placeholder = AppResources.ResourceManager.GetString(nameof(AppResources.LangPageSearch), selectedLang);
    }

    private async void OnDoneButtonClicked(object sender, EventArgs e)
    {
        var name = (LangListView.SelectedItem as Language)!.Name;
        // todo : Preferences.Set(SettingKeys.Language, name);
        Localization.SetLanguage(name);
        await Shell.Current.Navigation.PopAsync();     
    }  
}