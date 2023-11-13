namespace Volta.Core.Platforms;

public class BiometricUnlock
{
    public event EventHandler<string>? Failed;
    public event EventHandler? Succeeded;

    public async Task StartAsync()
    {
#if ANDROID
        AndroidBiometricUnlock androidBiometricUnlock = new();
        androidBiometricUnlock.Failed += (s, e) => Failed?.Invoke(this, e);
        androidBiometricUnlock.Succeeded += (s, e) => Succeeded?.Invoke(this, e);
        androidBiometricUnlock.Start();
#elif WINDOWS
        WinBiometricUnlock winBiometricUnlock = new();
        winBiometricUnlock.Failed += (s, e) => Failed?.Invoke(this, e);
        winBiometricUnlock.Succeeded += (s, e) => Succeeded?.Invoke(this, e);
        await winBiometricUnlock.StartAsync();
#else
        // iOS, Mac
        Succeeded?.Invoke(this, EventArgs.Empty);
#endif
    }
}