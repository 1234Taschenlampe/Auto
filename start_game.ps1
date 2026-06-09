# start_game.ps1 - Startet das native Cursor Catch Game
# Wird vom USB-Monitor (monitor.ps1) aufgerufen

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$exePath = Join-Path $scriptDir "desktop-cursor-catch-game\bin\Debug\net10.0-windows\DesktopCursorCatchGame.exe"

if (Test-Path $exePath) {
    Start-Process -FilePath $exePath
} else {
    # Fallback: dotnet run
    $projectDir = Join-Path $scriptDir "desktop-cursor-catch-game"
    Start-Process -FilePath "dotnet" -ArgumentList "run" -WorkingDirectory $projectDir
}
