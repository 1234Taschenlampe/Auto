# DROLLOLOL PRANK SEQUENCE
# ========================

$targetDir = "C:\Users\onlym\.gemini\antigravity\scratch\capture-viewer"
$wallpaperPath = "$targetDir\wallpaper.png"

# Save original wallpaper path to a temp file for restoration later
$oldWallpaper = (Get-ItemProperty -Path 'HKCU:\Control Panel\Desktop').Wallpaper
if ($oldWallpaper -and $oldWallpaper -notlike "*wallpaper.png") {
    $oldWallpaper | Out-File "$targetDir\original_wallpaper.txt" -Force
}

# 1. Take a screenshot of the desktop to use as the background in game.html
try {
    Add-Type -AssemblyName System.Windows.Forms -ErrorAction Stop
    Add-Type -AssemblyName System.Drawing -ErrorAction Stop
    $screen = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
    $bitmap = New-Object System.Drawing.Bitmap $screen.Width, $screen.Height
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.CopyFromScreen($screen.Location, [System.Drawing.Point]::Empty, $screen.Size)
    $bitmap.Save("$targetDir\desktop.png", [System.Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose()
    $bitmap.Dispose()
} catch {
    Write-Host "Screenshot failed: $_"
}

# 2. Set System Volume to 100%
$wshShell = New-Object -ComObject WScript.Shell
for ($i = 0; $i -lt 50; $i++) {
    $wshShell.SendKeys([char]175) # Volume Up
}

# 3. Change Desktop Wallpaper to the Prank Cat
$code = @'
using System;
using System.Runtime.InteropServices;
public class Wallpaper {
    [DllImport("user32.dll", CharSet = CharSet.Auto)]
    public static extern int SystemParametersInfo(int uAction, int uParam, string lpvParam, int fuWinIni);
}
'@
Add-Type -TypeDefinition $code -ErrorAction SilentlyContinue

if (Test-Path $wallpaperPath) {
    [Wallpaper]::SystemParametersInfo(20, 0, $wallpaperPath, 3) | Out-Null
}

# 4. Open the lock screen mini-game (game.html uses desktop.png as background)
Start-Process "http://localhost:8088/game.html"
