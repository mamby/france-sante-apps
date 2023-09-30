using CommunityToolkit.Maui.Behaviors;
using CommunityToolkit.Maui.Core;
using System.Globalization;
using Volta.Core.L10n;
using Volta.Core.Platforms;
using Volta.Resources.Strings;

namespace Volta.Views;

public partial class LanguagePage : ContentPage
{
    private readonly bool _isStartPage;
    private readonly List<Language> _appLanguages;
    private readonly string _okButtonResKey;

    public LanguagePage()
    {
        InitializeComponent();

        Behaviors.Add(new SoftInputBehavior());
        //Behaviors.Add(new StatusBarBehavior
        //{
        //    StatusBarColor = Colors.White,
        //    StatusBarStyle = StatusBarStyle.DarkContent
        //});

        _isStartPage = App.Current.MainPage is not AppShell;
        _appLanguages = Localization.Languages();

        Title = AppResources.LangPageTitle;
        SearchBar.Placeholder = AppResources.LangPageSearch;
        OkButton.Text = !_isStartPage ? AppResources.LangPageDone : AppResources.LangPageNext;
        _okButtonResKey = !_isStartPage ? "LangPageDone": "LangPageNext";

        LangListView.ItemsSource = _appLanguages;
    }

    void OnSearchTextChanged(object sender, EventArgs e)
    {
        OkButton.IsEnabled = false;
        LangListView.ItemsSource = _appLanguages
            .Where(l => l.NativeName.Contains(SearchBar.Text, StringComparison.OrdinalIgnoreCase))
            .ToList();
    }

    private void OnLangItemTapped(object sender, ItemTappedEventArgs e)
    {
        SearchBar.Unfocus();
        OkButton.IsEnabled = true;
        CultureInfo selectedLang = new((LangListView.SelectedItem as Language).Name);
        OkButton.Text = AppResources.ResourceManager.GetString(_okButtonResKey, selectedLang);
        SearchBar.Placeholder = AppResources.ResourceManager.GetString("LangPageSearch", selectedLang);
    }

    private async void OnOkButtonClicked(object sender, EventArgs e)
    {
        var name = (LangListView.SelectedItem as Language).Name;
        // todo : Preferences.Set(nameof(Language), name);
        Localization.SetLanguage(name);

        if (!_isStartPage)
        {
            await Shell.Current.Navigation.PopAsync();
        }
        else
        {
            await App.InitAppLockAsync();
        }
    }  
}