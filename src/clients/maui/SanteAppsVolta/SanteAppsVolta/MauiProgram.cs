using CommunityToolkit.Maui;
using Microsoft.Extensions.Logging;
using Volta.Core.Data.Services;
using Volta.Views;

namespace Volta;

public static class MauiProgram
{
    public static IServiceProvider Services { get; private set; } = default!;

    public static MauiApp CreateMauiApp()
    {
        var builder = MauiApp.CreateBuilder();
        builder
            .UseMauiApp<App>()
            .UseMauiCommunityToolkit()
            .ConfigureFonts(fonts =>
            {
                fonts.AddFont("OpenSans-Regular.ttf", "OpenSansRegular");
                fonts.AddFont("OpenSans-Semibold.ttf", "OpenSansSemibold");
            });

        builder.Services.AddSingleton<IAppDataService, StaticAppDataService>();

        builder.Services.AddTransient<HomePage>();
        builder.Services.AddTransient<PersonalHealthRecordPage>();
        builder.Services.AddTransient<NotificationsPage>();
        builder.Services.AddTransient<ProfilePage>();
        builder.Services.AddTransient<MoreFeaturesPage>();
        builder.Services.AddTransient<LanguagePage>();
        builder.Services.AddTransient<LockPage>();

#if DEBUG
        builder.Logging.AddDebug();
#endif

        var app = builder.Build();
        Services = app.Services;

        return app;
    }
}
