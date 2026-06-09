using System.Windows;
using System.Windows.Threading;

namespace DesktopCursorCatchGame;

public class SafeExitManager
{
    private readonly DispatcherTimer _maxTimer;
    private readonly Window _window;
    private bool _hasExited = false;

    public event Action? OnForceExit;

    public SafeExitManager(Window window, int maxSeconds = 60)
    {
        _window = window;
        _maxTimer = new DispatcherTimer
        {
            Interval = TimeSpan.FromSeconds(maxSeconds)
        };
        _maxTimer.Tick += (s, e) =>
        {
            _maxTimer.Stop();
            SafeExit();
        };
    }

    public void Start()
    {
        _maxTimer.Start();
    }

    public void SafeExit()
    {
        if (_hasExited) return;
        _hasExited = true;
        _maxTimer.Stop();

        // Restore system cursor
        System.Windows.Input.Mouse.OverrideCursor = null;

        OnForceExit?.Invoke();

        try
        {
            _window.Close();
        }
        catch { }

        Application.Current?.Shutdown();
    }
}
