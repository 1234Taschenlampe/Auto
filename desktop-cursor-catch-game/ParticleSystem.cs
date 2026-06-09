using System.Windows;
using System.Windows.Media;

namespace DesktopCursorCatchGame;

public class Particle
{
    public Point Position;
    public double VelocityX;
    public double VelocityY;
    public double Life;
    public double MaxLife;
    public double Size;
    public Color Color;
    public double Gravity;
    public double Rotation;
    public double RotationSpeed;
}

public class TrailPoint
{
    public Point Position;
    public double Life;
    public double MaxLife;
    public double Size;
}

public class ParticleSystem
{
    private readonly List<Particle> _particles = new();
    private readonly List<TrailPoint> _trail = new();
    private readonly Random _rng = new();

    public IReadOnlyList<Particle> Particles => _particles;
    public IReadOnlyList<TrailPoint> Trail => _trail;

    public void AddSlimeTrail(Point position, double size)
    {
        if (_trail.Count > 200) return;
        _trail.Add(new TrailPoint
        {
            Position = position,
            Life = 2.0,
            MaxLife = 2.0,
            Size = size * (0.3 + _rng.NextDouble() * 0.3)
        });
    }

    public void EmitSlimeDrip(Point position, int count = 3)
    {
        for (int i = 0; i < count; i++)
        {
            double angle = _rng.NextDouble() * Math.PI * 2;
            double speed = 0.5 + _rng.NextDouble() * 2;
            _particles.Add(new Particle
            {
                Position = position,
                VelocityX = Math.Cos(angle) * speed,
                VelocityY = Math.Sin(angle) * speed,
                Life = 1.0 + _rng.NextDouble() * 1.5,
                MaxLife = 2.5,
                Size = 3 + _rng.NextDouble() * 6,
                Color = Color.FromArgb(200, 50, (byte)(200 + _rng.Next(55)), 80),
                Gravity = 0.5 + _rng.NextDouble() * 0.5,
                Rotation = _rng.NextDouble() * 360,
                RotationSpeed = (_rng.NextDouble() - 0.5) * 200
            });
        }
    }

    public void EmitSplitAnimation(Point origin, Point target, int count = 8)
    {
        for (int i = 0; i < count; i++)
        {
            double t = (double)i / count;
            double midX = (origin.X + target.X) / 2 + (_rng.NextDouble() - 0.5) * 40;
            double midY = (origin.Y + target.Y) / 2 + (_rng.NextDouble() - 0.5) * 40;
            _particles.Add(new Particle
            {
                Position = new Point(
                    origin.X + (midX - origin.X) * t,
                    origin.Y + (midY - origin.Y) * t),
                VelocityX = (_rng.NextDouble() - 0.5) * 3,
                VelocityY = _rng.NextDouble() * 2 + 1,
                Life = 1.5 + _rng.NextDouble(),
                MaxLife = 2.5,
                Size = 4 + _rng.NextDouble() * 8,
                Color = Color.FromArgb(220, 30, (byte)(180 + _rng.Next(75)), 60),
                Gravity = 0.8,
                Rotation = 0,
                RotationSpeed = (_rng.NextDouble() - 0.5) * 300
            });
        }
    }

    public void EmitCatchExplosion(Point position, int count = 40)
    {
        for (int i = 0; i < count; i++)
        {
            double angle = _rng.NextDouble() * Math.PI * 2;
            double speed = 2 + _rng.NextDouble() * 10;
            byte green = (byte)(150 + _rng.Next(105));
            _particles.Add(new Particle
            {
                Position = position,
                VelocityX = Math.Cos(angle) * speed,
                VelocityY = Math.Sin(angle) * speed,
                Life = 1.5 + _rng.NextDouble() * 2,
                MaxLife = 3.5,
                Size = 4 + _rng.NextDouble() * 14,
                Color = Color.FromArgb(240, 20, green, 40),
                Gravity = 1.5,
                Rotation = _rng.NextDouble() * 360,
                RotationSpeed = (_rng.NextDouble() - 0.5) * 400
            });
        }
    }

    public void Update(double deltaTime)
    {
        for (int i = _particles.Count - 1; i >= 0; i--)
        {
            var p = _particles[i];
            p.Life -= deltaTime;
            if (p.Life <= 0)
            {
                _particles.RemoveAt(i);
                continue;
            }
            p.Position = new Point(
                p.Position.X + p.VelocityX,
                p.Position.Y + p.VelocityY);
            p.VelocityY += p.Gravity * deltaTime;
            p.VelocityX *= 0.98;
            p.VelocityY *= 0.98;
            p.Size *= 0.995;
            p.Rotation += p.RotationSpeed * deltaTime;
        }

        for (int i = _trail.Count - 1; i >= 0; i--)
        {
            _trail[i].Life -= deltaTime;
            if (_trail[i].Life <= 0)
                _trail.RemoveAt(i);
        }
    }

    public void Clear()
    {
        _particles.Clear();
        _trail.Clear();
    }
}
