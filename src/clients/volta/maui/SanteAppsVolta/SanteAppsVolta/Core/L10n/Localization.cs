using System.Globalization;

namespace Volta.Core.L10n;

public static class Localization
{
    private static readonly Dictionary<string, Language> _languages = new()
    {
        { "fr-FR", new Language { Name = "fr-FR", NativeName = "Français (France)", FlowDirection = FlowDirection.LeftToRight } },
        { "en-US", new Language { Name = "en-US", NativeName = "English (United States)", FlowDirection = FlowDirection.LeftToRight } },
        { "ar-SA", new Language { Name = "ar-SA", NativeName = "المملكة العربية السعودية - العربية", FlowDirection = FlowDirection.RightToLeft } }
    };

    public static bool LanguageExist(string name) => _languages.ContainsKey(name);

    public static List<Language> Languages()
    {
        var languages = _languages.Values.OrderBy(l => l.NativeName).ToList();

        if (_languages.TryGetValue(CultureInfo.CurrentCulture.Name, out var currentLang))
        {
            languages.Remove(currentLang);
            languages.Insert(0, currentLang);
        }

        return languages;
    }

    public static void SetLanguage(string name)
    {
        CultureInfo ci = new(name);
        CultureInfo.CurrentCulture = ci;
        CultureInfo.CurrentUICulture = ci;
    }
}