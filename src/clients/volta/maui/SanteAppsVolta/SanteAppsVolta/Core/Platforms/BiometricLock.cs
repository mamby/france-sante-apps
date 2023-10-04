namespace Volta.Core.Platforms;

public static partial class BiometricLock
{
    static partial void Prompt();

    public static void Open()
    {
        Prompt();
    }
}