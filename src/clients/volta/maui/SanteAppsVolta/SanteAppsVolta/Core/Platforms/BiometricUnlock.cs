namespace Volta.Core.Platforms;

public class BiometricUnlock
{
    public event EventHandler<string>? Failed;
    public event EventHandler? Succeeded;

    public void Start()
    {
#if ANDROID
        AndroidBiometricUnlock androidBiometricUnlock = new();
        androidBiometricUnlock.Failed += (s, e) => Failed?.Invoke(this, e);
        androidBiometricUnlock.Succeeded += (s, e) => Succeeded?.Invoke(this, e);
        androidBiometricUnlock.Start();
#else
        // iOS, Windows, Mac
#endif
    }
}