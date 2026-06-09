using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Shapes;

namespace DesktopCursorCatchGame;

public partial class MainWindow : Window
{
    // === Game state ===
    private enum GameState { Menu, SplitAnimation, Playing, CatchAnimation, EndScreen }
    private GameState _state = GameState.Menu;
    private Difficulty _selectedDifficulty = Difficulty.Normal;

    // === Core objects ===
    private readonly GameLoop _gameLoop = new();
    private readonly CursorEntity _player = new();
    private readonly SlimeCursorEntity _slime = new();
    private readonly ParticleSystem _particles = new();
    private SafeExitManager _safeExit = null!;

    // === Timing ===
    private double _gameTimer;
    private int _timeLimitSeconds;
    private double _splitAnimTime;
    private double _catchAnimTime;
    private const double SplitDuration = 1.8;
    private const double CatchDuration = 2.5;

    // === Split animation ===
    private Point _splitOrigin;
    private Point _splitTarget;
    private double _splitProgress;

    // === Rendering ===
    private readonly DrawingGroup _drawingGroup = new();
    private Image? _renderImage;

    public MainWindow()
    {
        InitializeComponent();

        _safeExit = new SafeExitManager(this, 65); // 65s hard limit
        _safeExit.OnForceExit += OnForceExit;

        // Wire buttons
        BtnEasy.Click += (s, e) => SelectDifficulty(Difficulty.Easy);
        BtnNormal.Click += (s, e) => SelectDifficulty(Difficulty.Normal);
        BtnHard.Click += (s, e) => SelectDifficulty(Difficulty.Hard);
        BtnStart.Click += (s, e) => StartGame();
        BtnRestart.Click += (s, e) => RestartGame();
        BtnQuit.Click += (s, e) => _safeExit.SafeExit();

        // Input
        KeyDown += OnKeyDown;
        MouseMove += OnMouseMove;

        // Game loop
        _gameLoop.OnUpdate += GameUpdate;

        // Setup render image
        Loaded += OnLoaded;

        // Select Normal by default
        SelectDifficulty(Difficulty.Normal);
    }

    private void OnLoaded(object sender, RoutedEventArgs e)
    {
        _renderImage = new Image
        {
            Stretch = Stretch.None,
            IsHitTestVisible = false
        };
        var drawingImage = new DrawingImage(_drawingGroup);
        _renderImage.Source = drawingImage;
        GameCanvas.Children.Add(_renderImage);

        // Center player at screen center initially
        _player.Position = new Point(ActualWidth / 2, ActualHeight / 2);
    }

    // === Input ===
    private void OnKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Escape)
        {
            _safeExit.SafeExit();
        }
    }

    private Point _mousePos;
    private void OnMouseMove(object sender, MouseEventArgs e)
    {
        _mousePos = e.GetPosition(GameCanvas);
    }

    // === Difficulty selection ===
    private void SelectDifficulty(Difficulty d)
    {
        _selectedDifficulty = d;
        BtnEasy.BorderThickness = new Thickness(d == Difficulty.Easy ? 2 : 1);
        BtnEasy.Background = new SolidColorBrush(d == Difficulty.Easy ? Color.FromRgb(0x44, 0x44, 0x44) : Color.FromRgb(0x33, 0x33, 0x33));
        BtnNormal.BorderThickness = new Thickness(d == Difficulty.Normal ? 2 : 1);
        BtnNormal.Background = new SolidColorBrush(d == Difficulty.Normal ? Color.FromRgb(0x44, 0x44, 0x44) : Color.FromRgb(0x33, 0x33, 0x33));
        BtnHard.BorderThickness = new Thickness(d == Difficulty.Hard ? 2 : 1);
        BtnHard.Background = new SolidColorBrush(d == Difficulty.Hard ? Color.FromRgb(0x44, 0x44, 0x44) : Color.FromRgb(0x33, 0x33, 0x33));
    }

    // === Game start ===
    private void StartGame()
    {
        StartScreen.Visibility = Visibility.Collapsed;
        EndScreen.Visibility = Visibility.Collapsed;
        HudLayer.Visibility = Visibility.Visible;

        _timeLimitSeconds = DifficultySettings.GetTimeLimitSeconds(_selectedDifficulty);
        _gameTimer = 0;
        DifficultyText.Text = _selectedDifficulty switch
        {
            Difficulty.Easy => "Einfach",
            Difficulty.Normal => "Normal",
            Difficulty.Hard => "Schwer",
            _ => "Normal"
        };

        // Initialize positions
        double cx = ActualWidth / 2;
        double cy = ActualHeight / 2;
        _player.Position = new Point(cx, cy);
        _mousePos = new Point(cx, cy);

        // Split animation setup
        _splitOrigin = new Point(cx, cy);
        double angle = new Random().NextDouble() * Math.PI * 2;
        double dist = 150 + new Random().NextDouble() * 100;
        _splitTarget = new Point(cx + Math.Cos(angle) * dist, cy + Math.Sin(angle) * dist);
        _splitAnimTime = 0;
        _splitProgress = 0;

        _slime.Initialize(_selectedDifficulty, ActualWidth, ActualHeight, _splitOrigin);
        _slime.IsCaught = false;
        _slime.Opacity = 1.0;

        _particles.Clear();

        // Start with split animation
        _state = GameState.SplitAnimation;
        _particles.EmitSplitAnimation(_splitOrigin, _splitTarget, 15);

        Cursor = Cursors.None;

        _safeExit.Start();
        _gameLoop.Start();
    }

    private void RestartGame()
    {
        _gameLoop.Stop();
        _particles.Clear();
        StartGame();
    }

    // === Main game update ===
    private void GameUpdate(double dt)
    {
        switch (_state)
        {
            case GameState.SplitAnimation:
                UpdateSplitAnimation(dt);
                break;
            case GameState.Playing:
                UpdatePlaying(dt);
                break;
            case GameState.CatchAnimation:
                UpdateCatchAnimation(dt);
                break;
        }

        _particles.Update(dt);
        Render();
    }

    private void UpdateSplitAnimation(double dt)
    {
        _splitAnimTime += dt;
        _splitProgress = Math.Min(_splitAnimTime / SplitDuration, 1.0);

        // Ease out cubic
        double eased = 1.0 - Math.Pow(1.0 - _splitProgress, 3);

        // Move slime from origin to target
        double sx = _splitOrigin.X + (_splitTarget.X - _splitOrigin.X) * eased;
        double sy = _splitOrigin.Y + (_splitTarget.Y - _splitOrigin.Y) * eased;
        _slime.Position = new Point(sx, sy);

        // Emit slime drips along the path
        if (_splitAnimTime % 0.1 < dt)
        {
            double midX = (_splitOrigin.X + sx) / 2 + (new Random().NextDouble() - 0.5) * 20;
            double midY = (_splitOrigin.Y + sy) / 2 + (new Random().NextDouble() - 0.5) * 20;
            _particles.EmitSlimeDrip(new Point(midX, midY), 2);
        }

        _player.Update(_mousePos, dt);

        if (_splitProgress >= 1.0)
        {
            _state = GameState.Playing;
        }
    }

    private void UpdatePlaying(double dt)
    {
        _gameTimer += dt;

        // Update timer display
        double remaining = Math.Max(0, _timeLimitSeconds - _gameTimer);
        TimerText.Text = $"{remaining:F1}s";

        if (remaining <= 10)
            TimerText.Foreground = new SolidColorBrush(Color.FromRgb(0xFF, 0x55, 0x55));
        else
            TimerText.Foreground = new SolidColorBrush(Color.FromArgb(0xAA, 0xFF, 0xFF, 0xFF));

        // Time up
        if (_gameTimer >= _timeLimitSeconds)
        {
            EndGame(false);
            return;
        }

        // Update entities
        _player.Update(_mousePos, dt);
        _slime.Update(_player.Position, dt);

        // Slime trail
        if (_gameLoop.FrameCount % 3 == 0)
        {
            _particles.AddSlimeTrail(_slime.Position, _slime.Size);
        }

        // Occasional drips
        if (_gameLoop.FrameCount % 20 == 0)
        {
            _particles.EmitSlimeDrip(_slime.Position, 1);
        }

        // Check collision
        double catchRadius = DifficultySettings.GetCatchRadius(_selectedDifficulty);
        if (CollisionDetection.CheckCatch(_player, _slime, catchRadius))
        {
            _slime.IsCaught = true;
            _particles.EmitCatchExplosion(_slime.Position, 50);
            _catchAnimTime = 0;
            _state = GameState.CatchAnimation;
        }
    }

    private void UpdateCatchAnimation(double dt)
    {
        _catchAnimTime += dt;

        // Shrink slime
        _slime.Opacity = Math.Max(0, 1.0 - _catchAnimTime / 0.5);
        _slime.Size = Math.Max(0, 28 * (1.0 - _catchAnimTime / 0.5));

        _player.Update(_mousePos, dt);

        if (_catchAnimTime >= CatchDuration)
        {
            EndGame(true);
        }
    }

    private void EndGame(bool caught)
    {
        _gameLoop.Stop();
        _state = GameState.EndScreen;

        Cursor = Cursors.Arrow;
        EndScreen.Visibility = Visibility.Visible;
        HudLayer.Visibility = Visibility.Collapsed;

        if (caught)
        {
            EndTitle.Text = "🎉 Gefangen!";
            EndTitle.Foreground = new SolidColorBrush(Color.FromRgb(0x33, 0xFF, 0x66));
            EndTimeText.Text = $"Zeit: {_gameTimer:F1}s";
        }
        else
        {
            EndTitle.Text = "⏰ Zeit abgelaufen!";
            EndTitle.Foreground = new SolidColorBrush(Color.FromRgb(0xFF, 0x55, 0x55));
            EndTimeText.Text = $"Der Slime ist entkommen!";
        }
    }

    // === Rendering ===
    private void Render()
    {
        using var dc = _drawingGroup.Open();

        double w = ActualWidth;
        double h = ActualHeight;

        if (w <= 0 || h <= 0) return;

        // Clear
        dc.DrawRectangle(Brushes.Transparent, null, new Rect(0, 0, w, h));

        // Draw trail
        foreach (var trail in _particles.Trail)
        {
            double alpha = (trail.Life / trail.MaxLife) * 0.3;
            var brush = new SolidColorBrush(Color.FromArgb(
                (byte)(alpha * 255), 30, 200, 60));
            brush.Freeze();
            dc.DrawEllipse(brush, null, trail.Position, trail.Size / 2, trail.Size / 2);
        }

        // Draw particles
        foreach (var p in _particles.Particles)
        {
            double alpha = (p.Life / p.MaxLife);
            var color = Color.FromArgb(
                (byte)(alpha * p.Color.A),
                p.Color.R, p.Color.G, p.Color.B);
            var brush = new SolidColorBrush(color);
            brush.Freeze();
            dc.DrawEllipse(brush, null, p.Position, p.Size / 2, p.Size / 2);
        }

        // Draw split animation strings
        if (_state == GameState.SplitAnimation && _splitProgress < 1.0)
        {
            DrawSlimeStrings(dc, _player.Position, _slime.Position, 1.0 - _splitProgress);
        }

        // Draw slime cursor
        if (_state != GameState.EndScreen && _state != GameState.Menu && _slime.Opacity > 0.01)
        {
            DrawSlimeCursor(dc);
        }

        // Draw player cursor
        if (_state != GameState.EndScreen && _state != GameState.Menu)
        {
            DrawPlayerCursor(dc);
        }
    }

    private void DrawSlimeStrings(DrawingContext dc, Point from, Point to, double strength)
    {
        int stringCount = 5;
        var rng = new Random(42); // deterministic for consistency
        var pen = new Pen(new SolidColorBrush(Color.FromArgb(
            (byte)(strength * 180), 40, 220, 70)), 2.5 * strength);
        pen.Freeze();

        for (int i = 0; i < stringCount; i++)
        {
            double offsetX = (rng.NextDouble() - 0.5) * 30;
            double offsetY = (rng.NextDouble() - 0.5) * 30;
            double mid = 0.3 + rng.NextDouble() * 0.4;

            var midPoint = new Point(
                from.X + (to.X - from.X) * mid + offsetX * strength,
                from.Y + (to.Y - from.Y) * mid + offsetY * strength + 20 * strength);

            var geometry = new StreamGeometry();
            using (var ctx = geometry.Open())
            {
                ctx.BeginFigure(from, false, false);
                ctx.QuadraticBezierTo(midPoint, to, true, false);
            }
            geometry.Freeze();
            dc.DrawGeometry(null, pen, geometry);
        }

        // Drip drops along strings
        for (int i = 0; i < 3; i++)
        {
            double t = 0.2 + rng.NextDouble() * 0.6;
            var dripPos = new Point(
                from.X + (to.X - from.X) * t + (rng.NextDouble() - 0.5) * 15,
                from.Y + (to.Y - from.Y) * t + rng.NextDouble() * 15 * strength);
            double dripSize = 3 + rng.NextDouble() * 4 * strength;
            var dripBrush = new SolidColorBrush(Color.FromArgb(
                (byte)(strength * 150), 50, 230, 80));
            dripBrush.Freeze();
            dc.DrawEllipse(dripBrush, null, dripPos, dripSize, dripSize * 1.3);
        }
    }

    private void DrawSlimeCursor(DrawingContext dc)
    {
        var pos = _slime.Position;
        double size = _slime.Size;
        double opacity = _slime.Opacity;

        // Glow
        var glowBrush = new RadialGradientBrush(
            Color.FromArgb((byte)(opacity * 60), 30, 255, 60),
            Color.FromArgb(0, 30, 255, 60));
        glowBrush.Freeze();
        dc.DrawEllipse(glowBrush, null, pos, size * 1.8, size * 1.8);

        // Main body with stretch
        double sx = size * _slime.StretchX / 2;
        double sy = size * _slime.StretchY / 2;

        // Outer ring
        var outerBrush = new SolidColorBrush(Color.FromArgb((byte)(opacity * 200), 20, 180, 50));
        outerBrush.Freeze();
        dc.DrawEllipse(outerBrush, null, pos, sx + 2, sy + 2);

        // Inner body gradient
        var bodyBrush = new RadialGradientBrush(
            Color.FromArgb((byte)(opacity * 255), 80, 255, 120),
            Color.FromArgb((byte)(opacity * 220), 20, 160, 40));
        bodyBrush.Freeze();
        dc.DrawEllipse(bodyBrush, null, pos, sx, sy);

        // Highlight
        var hlPos = new Point(pos.X - sx * 0.25, pos.Y - sy * 0.3);
        var hlBrush = new RadialGradientBrush(
            Color.FromArgb((byte)(opacity * 120), 200, 255, 210),
            Color.FromArgb(0, 200, 255, 210));
        hlBrush.Freeze();
        dc.DrawEllipse(hlBrush, null, hlPos, sx * 0.5, sy * 0.4);

        // Eyes
        if (opacity > 0.5 && size > 10)
        {
            double eyeOffsetX = sx * 0.3;
            double eyeOffsetY = -sy * 0.15;
            double eyeSize = Math.Max(2, size * 0.12);

            var eyeWhite = new SolidColorBrush(Color.FromArgb((byte)(opacity * 255), 255, 255, 255));
            eyeWhite.Freeze();
            var eyePupil = new SolidColorBrush(Color.FromArgb((byte)(opacity * 255), 20, 20, 20));
            eyePupil.Freeze();

            // Left eye
            dc.DrawEllipse(eyeWhite, null,
                new Point(pos.X - eyeOffsetX, pos.Y + eyeOffsetY),
                eyeSize * 1.2, eyeSize * 1.4);
            dc.DrawEllipse(eyePupil, null,
                new Point(pos.X - eyeOffsetX + 1, pos.Y + eyeOffsetY),
                eyeSize * 0.6, eyeSize * 0.7);

            // Right eye
            dc.DrawEllipse(eyeWhite, null,
                new Point(pos.X + eyeOffsetX, pos.Y + eyeOffsetY),
                eyeSize * 1.2, eyeSize * 1.4);
            dc.DrawEllipse(eyePupil, null,
                new Point(pos.X + eyeOffsetX + 1, pos.Y + eyeOffsetY),
                eyeSize * 0.6, eyeSize * 0.7);
        }

        // "Fake still" indicator - slime pulses when faking
        if (_slime.IsFakingStill)
        {
            double pulse = Math.Sin(_gameLoop.ElapsedTime * 8) * 0.15 + 0.85;
            var fakeBrush = new SolidColorBrush(Color.FromArgb(
                (byte)(40 * pulse), 100, 255, 100));
            fakeBrush.Freeze();
            dc.DrawEllipse(fakeBrush, null, pos, size * pulse, size * pulse);
        }
    }

    private void DrawPlayerCursor(DrawingContext dc)
    {
        var pos = _player.Position;
        double scale = _player.GetCurrentScale();
        double size = _player.Size * scale / 2;

        // Outer glow
        var glowBrush = new RadialGradientBrush(
            Color.FromArgb(40, 100, 180, 255),
            Color.FromArgb(0, 100, 180, 255));
        glowBrush.Freeze();
        dc.DrawEllipse(glowBrush, null, pos, size * 2, size * 2);

        // Ring
        var ringPen = new Pen(new SolidColorBrush(Color.FromArgb(180, 100, 200, 255)), 2.5);
        ringPen.Freeze();
        dc.DrawEllipse(null, ringPen, pos, size, size);

        // Inner dot
        var dotBrush = new SolidColorBrush(Color.FromArgb(220, 150, 220, 255));
        dotBrush.Freeze();
        dc.DrawEllipse(dotBrush, null, pos, 3, 3);

        // Crosshair lines
        double lineLen = size * 0.5;
        var linePen = new Pen(new SolidColorBrush(Color.FromArgb(120, 100, 200, 255)), 1.5);
        linePen.Freeze();
        dc.DrawLine(linePen, new Point(pos.X - size - lineLen, pos.Y), new Point(pos.X - size + 2, pos.Y));
        dc.DrawLine(linePen, new Point(pos.X + size - 2, pos.Y), new Point(pos.X + size + lineLen, pos.Y));
        dc.DrawLine(linePen, new Point(pos.X, pos.Y - size - lineLen), new Point(pos.X, pos.Y - size + 2));
        dc.DrawLine(linePen, new Point(pos.X, pos.Y + size - 2), new Point(pos.X, pos.Y + size + lineLen));
    }

    private void OnForceExit()
    {
        _gameLoop.Stop();
        Cursor = Cursors.Arrow;
    }

    protected override void OnClosed(EventArgs e)
    {
        _gameLoop.Stop();
        Cursor = Cursors.Arrow;
        Mouse.OverrideCursor = null;
        base.OnClosed(e);
    }
}