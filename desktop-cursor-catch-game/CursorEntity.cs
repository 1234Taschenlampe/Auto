using System.Windows;

namespace DesktopCursorCatchGame;

public class CursorEntity
{
    public Point Position { get; set; }
    public double Size { get; set; } = 24;
    public double PulsePhase { get; set; } = 0;
    private const double SmoothFactor = 0.15;

    public void Update(Point mousePosition, double deltaTime)
    {
        double dx = mousePosition.X - Position.X;
        double dy = mousePosition.Y - Position.Y;
        Position = new Point(
            Position.X + dx * SmoothFactor,
            Position.Y + dy * SmoothFactor
        );
        PulsePhase += deltaTime * 3.0;
        if (PulsePhase > Math.PI * 2) PulsePhase -= Math.PI * 2;
    }

    public double GetCurrentScale()
    {
        return 1.0 + Math.Sin(PulsePhase) * 0.08;
    }
}
