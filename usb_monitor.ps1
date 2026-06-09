# USB Stick Monitor Daemon
# ==============================
# Watches for a USB stick with the name/label "drollolol" and runs the prank.
# Restores the original wallpaper when the USB stick is unplugged.

$volumeLabel = "drollolol"
$targetDir = "C:\Users\onlym\.gemini\antigravity\scratch\capture-viewer"
$prankPath = "$targetDir\prank.ps1"

# Wallpaper API
$code = @'
using System;
using System.Runtime.InteropServices;
public class Wallpaper {
    [DllImport("user32.dll", CharSet = CharSet.Auto)]
    public static extern int SystemParametersInfo(int uAction, int uParam, string lpvParam, int fuWinIni);
}
'@
Add-Type -TypeDefinition $code -ErrorAction SilentlyContinue

function Restore-Wallpaper {
    $origFile = "$targetDir\original_wallpaper.txt"
    if (Test-Path $origFile) {
        $origPath = (Get-Content $origFile -Raw).Trim()
        if ($origPath -and (Test-Path $origPath)) {
            [Wallpaper]::SystemParametersInfo(20, 0, $origPath, 3) | Out-Null
        }
        Remove-Item $origFile -Force -ErrorAction SilentlyContinue
    }
}

$wasConnected = $false

# Initial check
$volumes = Get-Volume | Where-Object { $_.FileSystemLabel -eq $volumeLabel }
if ($volumes) {
    $wasConnected = $true
}

while ($true) {
    $volumes = Get-Volume | Where-Object { $_.FileSystemLabel -eq $volumeLabel }
    
    if ($volumes) {
        if (-not $wasConnected) {
            # USB Stick with label "drollolol" was plugged in!
            # Start the prank sequence stumm in the background
            Start-Process -FilePath "powershell.exe" -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$prankPath`"" -WindowStyle Hidden
            $wasConnected = $true
        }
    } else {
        if ($wasConnected) {
            # USB Stick was unplugged - Restore original wallpaper!
            Restore-Wallpaper
            $wasConnected = $false
        }
    }
    
    Start-Sleep -Seconds 2
}
