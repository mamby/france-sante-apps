using Microsoft.Maui.Controls.PlatformConfiguration.AndroidSpecific;

namespace Volta.Core.Platforms
{
    internal class SoftInputBehavior : Behavior<ContentPage>
    {
        protected override void OnAttachedTo(ContentPage page)
        {
            page.Appearing += OnAppearing;
            base.OnAttachedTo(page);
        }

        private void OnAppearing(object sender, EventArgs e)
        {
            App.Current.On<Microsoft.Maui.Controls.PlatformConfiguration.Android>().UseWindowSoftInputModeAdjust(WindowSoftInputModeAdjust.Resize);
        }

        protected override void OnDetachingFrom(ContentPage page)
        {
            page.Disappearing -= OnDisappearing;
            base.OnDetachingFrom(page);
        }

        private void OnDisappearing(object sender, EventArgs e)
        {
            App.Current.On<Microsoft.Maui.Controls.PlatformConfiguration.Android>().UseWindowSoftInputModeAdjust(WindowSoftInputModeAdjust.Pan);
        }
    }
}
