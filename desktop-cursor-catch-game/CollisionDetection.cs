using System.Windows;

namespace DesktopCursorCatchGame;

public static class CollisionDetection
{
    public static bool CheckCatch(CursorEntity player, SlimeCursorEntity slime, double catchRadius)
    {
        double dx = player.Position.X - slime.Position.X;
        double dy = player.Position.Y - slime.Position.Y;
        double dist = Math.Sqrt(dx * dx + dy * dy);
        return dist <= catchRadius;
    }

    public static double GetDistance(Point a, Point b)
    {
        double dx = a.X - b.X;
        double dy = a.Y - b.Y;
        return Math.Sqrt(dx * dx + dy * dy);
    }
}
