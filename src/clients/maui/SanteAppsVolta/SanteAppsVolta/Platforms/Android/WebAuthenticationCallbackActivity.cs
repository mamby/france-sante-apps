using Android.App;
using Android.Content.PM;

namespace Volta;

[Activity(NoHistory = true, LaunchMode = LaunchMode.SingleTop, Exported = true)]
[IntentFilter([Android.Content.Intent.ActionView],
              Categories = [Android.Content.Intent.CategoryDefault, Android.Content.Intent.CategoryBrowsable],
              DataScheme = CALLBACK_SCHEME)]
public class WebAuthenticationCallbackActivity : Microsoft.Maui.Authentication.WebAuthenticatorCallbackActivity
{
    const string CALLBACK_SCHEME = "myapp";

}