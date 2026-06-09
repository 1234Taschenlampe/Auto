# DROLLOLOL PRANK SEQUENCE
# ========================

$targetDir = "C:\Users\onlym\.gemini\antigravity\scratch\capture-viewer"
$wallpaperPath = "$targetDir\wallpaper.png"

# Save original wallpaper path to a temp file for restoration later
$oldWallpaper = (Get-ItemProperty -Path 'HKCU:\Control Panel\Desktop').Wallpaper
if ($oldWallpaper -and $oldWallpaper -notlike "*wallpaper.png") {
    $oldWallpaper | Out-File "$targetDir\original_wallpaper.txt" -Force
}

# 1. Set System Volume to 100%
$wshShell = New-Object -ComObject WScript.Shell
for ($i = 0; $i -lt 50; $i++) {
    $wshShell.SendKeys([char]175) # Volume Up
}

# 2. Change Desktop Wallpaper to the Prank Cat
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

# 3. Open the lock screen mini-game
Start-Process "http://localhost:8088/game.html"
