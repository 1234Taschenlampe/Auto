using System.Windows;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Shapes;

namespace DesktopCursorCatchGame;

public partial class MainWindow : Window
{
    // === Game state ===
    private enum GameState { Playing, CatchAnimation, EndScreen }
    private GameState _state = GameState.Playing;

    // === Core objects ===
    private readonly GameLoop _gameLoop = new();
    private readonly CursorEntity _player = new();
    private readonly SlimeCursorEntity _slime = new();
    private readonly ParticleSystem _particles = new();
    private SafeExitManager _safeExit = null!;

    // === Timing ===
    private double _gameTimer;
    private double _catchAnimTime;
    private const double CatchDuration = 2.0;
    private const int TimeLimitSeconds = 30; // Hard mode

    // === Matrix Rain ===
    private readonly List<MatrixColumn> _matrixColumns = new();
    private readonly Random _rng = new();
    private bool _matrixInitialized;

    // === Rendering ===
    private readonly DrawingGroup _drawingGroup = new();
    private System.Windows.Controls.Image? _renderImage;

    // === Auto-close timer ===
    private double _endScreenTimer;

    public MainWindow()
    {
        InitializeComponent();

        _safeExit = new SafeExitManager(this, 65);
        _safeExit.OnForceExit += OnForceExit;

        KeyDown += OnKeyDown;
        MouseMove += OnMouseMove;

        _gameLoop.OnUpdate += GameUpdate;

        Loaded += OnLoaded;
    }

    private void OnLoaded(object sender, RoutedEventArgs e)
    {
        _renderImage = new System.Windows.Controls.Image
        {
            Stretch = Stretch.None,
            IsHitTestVisible = false
        };
        var drawingImage = new DrawingImage(_drawingGroup);
        _renderImage.Source = drawingImage;
        GameCanvas.Children.Add(_renderImage);

        // Init Matrix rain
        InitMatrixRain();

        // Auto-start in Hard mode immediately
        StartGame();
    }

    // === Matrix Rain ===
    private void InitMatrixRain()
    {
        double w = ActualWidth;
        if (w <= 0) w = SystemParameters.PrimaryScreenWidth;
        int colWidth = 18;
        int numCols = (int)(w / colWidth) + 1;
        _matrixColumns.Clear();

        for (int i = 0; i < numCols; i++)
        {
            _matrixColumns.Add(new MatrixColumn
            {
                X = i * colWidth,
                Y = _rng.NextDouble() * -600,
                Speed = 60 + _rng.NextDouble() * 180,
                CharDelay = 0.03 + _rng.NextDouble() * 0.06,
                Timer = 0,
                Chars = GenerateMatrixChars(15 + _rng.Next(25)),
                Opacity = 0.15 + _rng.NextDouble() * 0.5
            });
        }
        _matrixInitialized = true;
    }

    private string GenerateMatrixChars(int length)
    {
        const string pool = "ﾊﾐﾋｰｳｼﾅﾓﾆｻﾜﾂｵﾘｱﾎﾃﾏｹﾒｴｶｷﾑﾕﾗｾﾈｽﾀﾇﾍ012345789ABCDEFZ<>¦╌╎";
        var chars = new char[length];
        for (int i = 0; i < length; i++)
            chars[i] = pool[_rng.Next(pool.Length)];
        return new string(chars);
    }

    private void UpdateMatrixRain(double dt)
    {
        double h = ActualHeight > 0 ? ActualHeight : SystemParameters.PrimaryScreenHeight;
        foreach (var col in _matrixColumns)
        {
            col.Y += col.Speed * dt;
            col.Timer += dt;

            // Random char changes
            if (col.Timer > col.CharDelay)
            {
                col.Timer = 0;
                int idx = _rng.Next(col.Chars.Length);
                var arr = col.Chars.ToCharArray();
                const string pool = "ﾊﾐﾋｰｳｼﾅﾓﾆｻﾜﾂｵﾘｱﾎﾃﾏｹﾒｴｶｷﾑﾕﾗｾﾈｽﾀﾇﾍ012345789Z";
                arr[idx] = pool[_rng.Next(pool.Length)];
                col.Chars = new string(arr);
            }

            if (col.Y > h + 400)
            {
                col.Y = _rng.NextDouble() * -400;
                col.Speed = 60 + _rng.NextDouble() * 180;
                col.Opacity = 0.15 + _rng.NextDouble() * 0.5;
                col.Chars = GenerateMatrixChars(15 + _rng.Next(25));
            }
        }
    }

    private void DrawMatrixRain(DrawingContext dc)
    {
        int charHeight = 18;
        double h = ActualHeight > 0 ? ActualHeight : SystemParameters.PrimaryScreenHeight;

        foreach (var col in _matrixColumns)
        {
            for (int i = 0; i < col.Chars.Length; i++)
            {
                double y = col.Y + i * charHeight;
                if (y < -charHeight || y > h + charHeight) continue;

                double fade = 1.0 - (double)i / col.Chars.Length;
                byte alpha;
                Color color;

                if (i == 0)
                {
                    // Head - bright white-green
                    alpha = (byte)(col.Opacity * 255);
                    color = Color.FromArgb(alpha, 180, 255, 190);
                }
                else
                {
                    alpha = (byte)(fade * col.Opacity * 160);
                    byte green = (byte)(150 + fade * 105);
                    color = Color.FromArgb(alpha, 0, green, 0);
                }

                var text = new FormattedText(
                    col.Chars[i].ToString(),
                    System.Globalization.CultureInfo.InvariantCulture,
                    FlowDirection.LeftToRight,
                    new Typeface("Consolas"),
                    14,
                    new SolidColorBrush(color),
                    1.0);

                dc.DrawText(text, new Point(col.X, y));
            }
        }
    }

    // === Input ===
    private void OnKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Escape)
            _safeExit.SafeExit();
    }

    private Point _mousePos;
    private void OnMouseMove(object sender, MouseEventArgs e)
    {
        _mousePos = e.GetPosition(GameCanvas);
    }

    // === Game start (auto, Hard only) ===
    private void StartGame()
    {
        EndScreen.Visibility = Visibility.Collapsed;
        HudLayer.Visibility = Visibility.Visible;

        _gameTimer = 0;

        double cx = ActualWidth / 2;
        double cy = ActualHeight / 2;
        _player.Position = new Point(cx, cy);
        _mousePos = new Point(cx, cy);

        // Slime starts offset from center
        double angle = _rng.NextDouble() * Math.PI * 2;
        double dist = 200 + _rng.NextDouble() * 150;
        var slimeStart = new Point(cx + Math.Cos(angle) * dist, cy + Math.Sin(angle) * dist);

        _slime.Initialize(Difficulty.Hard, ActualWidth, ActualHeight, slimeStart);
        _slime.IsCaught = false;
        _slime.Opacity = 1.0;
        _slime.Size = 28;

        _particles.Clear();

        _state = GameState.Playing;
        Cursor = Cursors.None;

        _safeExit.Start();
        _gameLoop.Start();
    }

    // === Main game update ===
    private void GameUpdate(double dt)
    {
        // Always update Matrix rain
        if (_matrixInitialized)
            UpdateMatrixRain(dt);

        switch (_state)
        {
            case GameState.Playing:
                UpdatePlaying(dt);
                break;
            case GameState.CatchAnimation:
                UpdateCatchAnimation(dt);
                break;
            case GameState.EndScreen:
                _endScreenTimer += dt;
                if (_endScreenTimer >= 3.0)
                    _safeExit.SafeExit();
                break;
        }

        _particles.Update(dt);
        Render();
    }

    private void UpdatePlaying(double dt)
    {
        _gameTimer += dt;

        double remaining = Math.Max(0, TimeLimitSeconds - _gameTimer);
        TimerText.Text = $"{remaining:F0}";

        if (remaining <= 10)
            TimerText.Foreground = new SolidColorBrush(Color.FromArgb(0xAA, 0xFF, 0x20, 0x20));
        else
            TimerText.Foreground = new SolidColorBrush(Color.FromArgb(0x66, 0x22, 0xFF, 0x44));

        if (_gameTimer >= TimeLimitSeconds)
        {
            EndGame(false);
            return;
        }

        _player.Update(_mousePos, dt);
        _slime.Update(_player.Position, dt);

        // Slime trail
        if (_gameLoop.FrameCount % 3 == 0)
            _particles.AddSlimeTrail(_slime.Position, _slime.Size);

        if (_gameLoop.FrameCount % 15 == 0)
            _particles.EmitSlimeDrip(_slime.Position, 1);

        // Collision
        double catchRadius = DifficultySettings.GetCatchRadius(Difficulty.Hard);
        if (CollisionDetection.CheckCatch(_player, _slime, catchRadius))
        {
            _slime.IsCaught = true;
            _particles.EmitCatchExplosion(_slime.Position, 60);
            _catchAnimTime = 0;
            _state = GameState.CatchAnimation;
        }
    }

    private void UpdateCatchAnimation(double dt)
    {
        _catchAnimTime += dt;
        _slime.Opacity = Math.Max(0, 1.0 - _catchAnimTime / 0.5);
        _slime.Size = Math.Max(0, 28 * (1.0 - _catchAnimTime / 0.5));
        _player.Update(_mousePos, dt);

        if (_catchAnimTime >= CatchDuration)
            EndGame(true);
    }

    private void EndGame(bool caught)
    {
        _gameLoop.Stop();
        _state = GameState.EndScreen;
        _endScreenTimer = 0;

        Cursor = Cursors.Arrow;
        EndScreen.Visibility = Visibility.Visible;
        HudLayer.Visibility = Visibility.Collapsed;

        if (caught)
        {
            EndTitle.Text = "CAPTURED";
            EndTitle.Foreground = new SolidColorBrush(Color.FromRgb(0x00, 0xFF, 0x41));
            EndTimeText.Text = $"// elapsed: {_gameTimer:F1}s";
        }
        else
        {
            EndTitle.Text = "ESCAPED";
            EndTitle.Foreground = new SolidColorBrush(Color.FromRgb(0xFF, 0x22, 0x22));
            EndTimeText.Text = "// target lost";
        }

        // Auto-close after 3 seconds, handled in GameUpdate
        _gameLoop.Start();
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

        // Matrix rain background
        if (_matrixInitialized)
            DrawMatrixRain(dc);

        // Trail (green slime, hacker style)
        foreach (var trail in _particles.Trail)
        {
            double alpha = (trail.Life / trail.MaxLife) * 0.25;
            var brush = new SolidColorBrush(Color.FromArgb(
                (byte)(alpha * 255), 0, 200, 30));
            brush.Freeze();
            dc.DrawEllipse(brush, null, trail.Position, trail.Size / 2, trail.Size / 2);
        }

        // Particles
        foreach (var p in _particles.Particles)
        {
            double alpha = p.Life / p.MaxLife;
            var color = Color.FromArgb(
                (byte)(alpha * p.Color.A), p.Color.R, p.Color.G, p.Color.B);
            var brush = new SolidColorBrush(color);
            brush.Freeze();
            dc.DrawEllipse(brush, null, p.Position, p.Size / 2, p.Size / 2);
        }

        // Slime cursor
        if (_state != GameState.EndScreen && _slime.Opacity > 0.01)
            DrawSlimeCursor(dc);

        // Player cursor
        if (_state != GameState.EndScreen)
            DrawPlayerCursor(dc);
    }

    private void DrawSlimeCursor(DrawingContext dc)
    {
        var pos = _slime.Position;
        double size = _slime.Size;
        double opacity = _slime.Opacity;

        // Green glow
        var glowBrush = new RadialGradientBrush(
            Color.FromArgb((byte)(opacity * 50), 0, 255, 30),
            Color.FromArgb(0, 0, 255, 0));
        glowBrush.Freeze();
        dc.DrawEllipse(glowBrush, null, pos, size * 2, size * 2);

        double sx = size * _slime.StretchX / 2;
        double sy = size * _slime.StretchY / 2;

        // Outer
        var outerBrush = new SolidColorBrush(Color.FromArgb((byte)(opacity * 180), 0, 160, 20));
        outerBrush.Freeze();
        dc.DrawEllipse(outerBrush, null, pos, sx + 2, sy + 2);

        // Body
        var bodyBrush = new RadialGradientBrush(
            Color.FromArgb((byte)(opacity * 255), 50, 255, 80),
            Color.FromArgb((byte)(opacity * 200), 0, 140, 20));
        bodyBrush.Freeze();
        dc.DrawEllipse(bodyBrush, null, pos, sx, sy);

        // Highlight
        var hlPos = new Point(pos.X - sx * 0.25, pos.Y - sy * 0.3);
        var hlBrush = new RadialGradientBrush(
            Color.FromArgb((byte)(opacity * 100), 150, 255, 160),
            Color.FromArgb(0, 150, 255, 160));
        hlBrush.Freeze();
        dc.DrawEllipse(hlBrush, null, hlPos, sx * 0.5, sy * 0.4);

        // Eyes
        if (opacity > 0.5 && size > 10)
        {
            double eyeOffX = sx * 0.3;
            double eyeOffY = -sy * 0.15;
            double eyeSize = Math.Max(2, size * 0.12);

            var eyeW = new SolidColorBrush(Color.FromArgb((byte)(opacity * 255), 200, 255, 200));
            eyeW.Freeze();
            var eyeP = new SolidColorBrush(Color.FromArgb((byte)(opacity * 255), 0, 40, 0));
            eyeP.Freeze();

            dc.DrawEllipse(eyeW, null, new Point(pos.X - eyeOffX, pos.Y + eyeOffY), eyeSize * 1.2, eyeSize * 1.4);
            dc.DrawEllipse(eyeP, null, new Point(pos.X - eyeOffX + 1, pos.Y + eyeOffY), eyeSize * 0.6, eyeSize * 0.7);
            dc.DrawEllipse(eyeW, null, new Point(pos.X + eyeOffX, pos.Y + eyeOffY), eyeSize * 1.2, eyeSize * 1.4);
            dc.DrawEllipse(eyeP, null, new Point(pos.X + eyeOffX + 1, pos.Y + eyeOffY), eyeSize * 0.6, eyeSize * 0.7);
        }

        // Fake still pulse
        if (_slime.IsFakingStill)
        {
            double pulse = Math.Sin(_gameLoop.ElapsedTime * 8) * 0.15 + 0.85;
            var fakeBrush = new SolidColorBrush(Color.FromArgb(
                (byte)(30 * pulse), 0, 255, 30));
            fakeBrush.Freeze();
            dc.DrawEllipse(fakeBrush, null, pos, size * pulse, size * pulse);
        }
    }

    private void DrawPlayerCursor(DrawingContext dc)
    {
        var pos = _player.Position;
        double scale = _player.GetCurrentScale();
        double size = _player.Size * scale / 2;

        // Green glow
        var glowBrush = new RadialGradientBrush(
            Color.FromArgb(30, 0, 255, 65),
            Color.FromArgb(0, 0, 255, 0));
        glowBrush.Freeze();
        dc.DrawEllipse(glowBrush, null, pos, size * 2, size * 2);

        // Ring
        var ringPen = new Pen(new SolidColorBrush(Color.FromArgb(160, 0, 255, 65)), 2);
        ringPen.Freeze();
        dc.DrawEllipse(null, ringPen, pos, size, size);

        // Center dot
        var dotBrush = new SolidColorBrush(Color.FromArgb(220, 0, 255, 65));
        dotBrush.Freeze();
        dc.DrawEllipse(dotBrush, null, pos, 3, 3);

        // Crosshair
        double lineLen = size * 0.5;
        var linePen = new Pen(new SolidColorBrush(Color.FromArgb(100, 0, 255, 65)), 1.5);
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

// === Matrix Rain Data ===
public class MatrixColumn
{
    public double X;
    public double Y;
    public double Speed;
    public double CharDelay;
    public double Timer;
    public string Chars = "";
    public double Opacity;
}