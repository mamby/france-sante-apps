namespace Volta.Core.Platforms;

public static partial class BiometricLock
{
    static partial void Prompt();
    static partial void ClosePrompt();

    public static void Open()
    {
        Prompt();
    }

    public static void Close()
    {
        ClosePrompt();
    }
}