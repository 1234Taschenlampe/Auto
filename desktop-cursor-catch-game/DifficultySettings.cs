namespace DesktopCursorCatchGame;

public enum Difficulty { Easy, Normal, Hard }

public static class DifficultySettings
{
    public static double GetSlimeBaseSpeed(Difficulty d) => d switch
    {
        Difficulty.Easy => 3.0,
        Difficulty.Normal => 5.0,
        Difficulty.Hard => 8.0,
        _ => 5.0
    };

    public static double GetSlimeFleeMultiplier(Difficulty d) => d switch
    {
        Difficulty.Easy => 1.5,
        Difficulty.Normal => 2.5,
        Difficulty.Hard => 4.0,
        _ => 2.5
    };

    public static double GetCatchRadius(Difficulty d) => d switch
    {
        Difficulty.Easy => 35.0,
        Difficulty.Normal => 25.0,
        Difficulty.Hard => 18.0,
        _ => 25.0
    };

    public static int GetTimeLimitSeconds(Difficulty d) => d switch
    {
        Difficulty.Easy => 60,
        Difficulty.Normal => 45,
        Difficulty.Hard => 30,
        _ => 45
    };

    public static double GetDirectionChangeInterval(Difficulty d) => d switch
    {
        Difficulty.Easy => 2.0,
        Difficulty.Normal => 1.2,
        Difficulty.Hard => 0.6,
        _ => 1.2
    };
}
