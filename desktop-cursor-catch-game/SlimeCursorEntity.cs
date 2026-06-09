using System.Windows;

namespace DesktopCursorCatchGame;

public class SlimeCursorEntity
{
    public Point Position { get; set; }
    public double Size { get; set; } = 28;
    public double VelocityX { get; set; }
    public double VelocityY { get; set; }
    public double StretchX { get; set; } = 1.0;
    public double StretchY { get; set; } = 1.0;
    public bool IsFakingStill { get; private set; }
    public double Opacity { get; set; } = 1.0;
    public bool IsCaught { get; set; } = false;

    private readonly Random _rng = new();
    private double _directionChangeTimer;
    private double _fakeStillTimer;
    private double _fakeStillDuration;
    private double _baseSpeed;
    private double _fleeMultiplier;
    private double _dirChangeInterval;
    private double _wanderAngle;
    private double _screenWidth;
    private double _screenHeight;

    public void Initialize(Difficulty difficulty, double screenW, double screenH, Point startPos)
    {
        _screenWidth = screenW;
        _screenHeight = screenH;
        Position = startPos;
        _baseSpeed = DifficultySettings.GetSlimeBaseSpeed(difficulty);
        _fleeMultiplier = DifficultySettings.GetSlimeFleeMultiplier(difficulty);
        _dirChangeInterval = DifficultySettings.GetDirectionChangeInterval(difficulty);
        _wanderAngle = _rng.NextDouble() * Math.PI * 2;
        _directionChangeTimer = _dirChangeInterval;
        _fakeStillTimer = 5.0 + _rng.NextDouble() * 8.0;
        _fakeStillDuration = 0;
    }

    public void Update(Point playerPos, double deltaTime)
    {
        if (IsCaught) return;

        double dx = Position.X - playerPos.X;
        double dy = Position.Y - playerPos.Y;
        double dist = Math.Sqrt(dx * dx + dy * dy);

        // Fake still logic
        _fakeStillTimer -= deltaTime;
        if (_fakeStillTimer <= 0 && !IsFakingStill)
        {
            IsFakingStill = true;
            _fakeStillDuration = 0.8 + _rng.NextDouble() * 1.2;
        }
        if (IsFakingStill)
        {
            _fakeStillDuration -= deltaTime;
            if (_fakeStillDuration <= 0 || dist < 80)
            {
                IsFakingStill = false;
                _fakeStillTimer = 4.0 + _rng.NextDouble() * 6.0;
                // Jump away!
                double jumpAngle = Math.Atan2(-dy, -dx) + (_rng.NextDouble() - 0.5) * 1.5;
                VelocityX = Math.Cos(jumpAngle) * _baseSpeed * _fleeMultiplier * 2.5;
                VelocityY = Math.Sin(jumpAngle) * _baseSpeed * _fleeMultiplier * 2.5;
            }
            else
            {
                VelocityX *= 0.85;
                VelocityY *= 0.85;
                UpdatePosition(deltaTime);
                UpdateStretch();
                return;
            }
        }

        // Direction change timer
        _directionChangeTimer -= deltaTime;
        if (_directionChangeTimer <= 0)
        {
            _wanderAngle = _rng.NextDouble() * Math.PI * 2;
            _directionChangeTimer = _dirChangeInterval * (0.5 + _rng.NextDouble());
        }

        // Flee behavior
        double fleeStrength = 0;
        double fleeAngle = 0;
        if (dist > 0 && dist < 300)
        {
            fleeStrength = (300 - dist) / 300.0;
            fleeAngle = Math.Atan2(dy, dx);
        }

        double targetVX = Math.Cos(_wanderAngle) * _baseSpeed;
        double targetVY = Math.Sin(_wanderAngle) * _baseSpeed;

        if (fleeStrength > 0)
        {
            double fleeSpeed = _baseSpeed * _fleeMultiplier * fleeStrength;
            targetVX += Math.Cos(fleeAngle) * fleeSpeed;
            targetVY += Math.Sin(fleeAngle) * fleeSpeed;
        }

        VelocityX += (targetVX - VelocityX) * 0.08;
        VelocityY += (targetVY - VelocityY) * 0.08;

        UpdatePosition(deltaTime);
        HandleEdges();
        UpdateStretch();
    }

    private void UpdatePosition(double deltaTime)
    {
        double newX = Position.X + VelocityX;
        double newY = Position.Y + VelocityY;
        Position = new Point(newX, newY);
    }

    private void HandleEdges()
    {
        double margin = 40;
        double x = Position.X;
        double y = Position.Y;
        bool edgeHit = false;

        if (x < margin) { x = margin; VelocityX = Math.Abs(VelocityX) * 0.7; edgeHit = true; }
        if (x > _screenWidth - margin) { x = _screenWidth - margin; VelocityX = -Math.Abs(VelocityX) * 0.7; edgeHit = true; }
        if (y < margin) { y = margin; VelocityY = Math.Abs(VelocityY) * 0.7; edgeHit = true; }
        if (y > _screenHeight - margin) { y = _screenHeight - margin; VelocityY = -Math.Abs(VelocityY) * 0.7; edgeHit = true; }

        Position = new Point(x, y);

        // Sometimes slide along edge
        if (edgeHit && _rng.NextDouble() < 0.3)
        {
            _wanderAngle = Math.Atan2(VelocityY, VelocityX);
        }
    }

    private void UpdateStretch()
    {
        double speed = Math.Sqrt(VelocityX * VelocityX + VelocityY * VelocityY);
        double maxSpeed = _baseSpeed * _fleeMultiplier * 2;
        double stretchAmount = Math.Min(speed / maxSpeed, 1.0) * 0.4;

        if (speed > 0.5)
        {
            double angle = Math.Atan2(VelocityY, VelocityX);
            StretchX = 1.0 + stretchAmount * Math.Abs(Math.Cos(angle));
            StretchY = 1.0 + stretchAmount * Math.Abs(Math.Sin(angle));
            // Conservation of area
            if (StretchX > 1.0) StretchY = 1.0 / StretchX * (1.0 + stretchAmount);
        }
        else
        {
            StretchX += (1.0 - StretchX) * 0.1;
            StretchY += (1.0 - StretchY) * 0.1;
        }
    }
}
