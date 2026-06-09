using System.Diagnostics;

namespace DesktopCursorCatchGame;

public class GameLoop
{
    private readonly Stopwatch _stopwatch = new();
    private long _lastFrameTicks;
    private bool _isRunning;

    public double DeltaTime { get; private set; }
    public double ElapsedTime { get; private set; }
    public int FrameCount { get; private set; }
    public double FPS { get; private set; }

    private double _fpsAccumulator;
    private int _fpsFrameCount;

    public event Action<double>? OnUpdate;

    public void Start()
    {
        _stopwatch.Start();
        _lastFrameTicks = _stopwatch.ElapsedTicks;
        _isRunning = true;
        System.Windows.Media.CompositionTarget.Rendering += OnRendering;
    }

    public void Stop()
    {
        _isRunning = false;
        System.Windows.Media.CompositionTarget.Rendering -= OnRendering;
        _stopwatch.Stop();
    }

    private void OnRendering(object? sender, EventArgs e)
    {
        if (!_isRunning) return;

        long currentTicks = _stopwatch.ElapsedTicks;
        DeltaTime = (double)(currentTicks - _lastFrameTicks) / Stopwatch.Frequency;
        _lastFrameTicks = currentTicks;

        // Clamp delta time to prevent spiral of death
        if (DeltaTime > 0.1) DeltaTime = 0.016;

        ElapsedTime += DeltaTime;
        FrameCount++;

        // FPS calculation
        _fpsAccumulator += DeltaTime;
        _fpsFrameCount++;
        if (_fpsAccumulator >= 1.0)
        {
            FPS = _fpsFrameCount / _fpsAccumulator;
            _fpsAccumulator = 0;
            _fpsFrameCount = 0;
        }

        OnUpdate?.Invoke(DeltaTime);
    }
}
