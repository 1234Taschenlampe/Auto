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

# 2. Change Desktop Wallpaper to the Prank Cat (4) AND launch Matrix Console (2)
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

# Start Matrix Window (2)
Start-Process powershell.exe -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$targetDir\matrix.ps1`"" -WindowStyle Normal

# Wait 2 seconds for visual transition
Start-Sleep -Seconds 2

# 3. Speak via TTS (1)
try {
    Add-Type -AssemblyName System.Speech -ErrorAction Stop
    $speak = New-Object System.Speech.Synthesis.SpeechSynthesizer
    $speak.Rate = 0
    $speak.Speak("Achtung! USB-Stick drollolol erkannt. Starte Download von geheimen Daten. Selbstzerstörung eingeleitet.") | Out-Null
} catch {
    # Fallback to SAPI.SpVoice if System.Speech is not loaded
    $voice = New-Object -ComObject SAPI.SpVoice
    $voice.Speak("Achtung! USB-Stick drollolol erkannt. Starte Download von geheimen Daten. Selbstzerstörung eingeleitet.") | Out-Null
}

# 4. Ghost Mouse: Jiggle cursor & type "drollolol..." in Notepad (3)
Add-Type -AssemblyName System.Windows.Forms -ErrorAction SilentlyContinue
Add-Type -AssemblyName System.Drawing -ErrorAction SilentlyContinue

$screen = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
$centerX = $screen.Width / 2
$centerY = $screen.Height / 2

# Jiggle mouse in circles for a few seconds
for ($i = 0; $i -lt 100; $i++) {
    $angle = $i * 0.2
    $x = $centerX + [Math]::Sin($angle) * 200
    $y = $centerY + [Math]::Cos($angle) * 200
    [System.Windows.Forms.Cursor]::Position = New-Object System.Drawing.Point($x, $y)
    Start-Sleep -Milliseconds 15
}

# Start Notepad and type
$notepad = Start-Process notepad.exe -PassThru
Start-Sleep -Seconds 1

# Activate the notepad window
$ws = New-Object -ComObject WScript.Shell
$ws.AppActivate($notepad.Id) | Out-Null

$text = "drollolol... your PC belongs to drollolol now!"
foreach ($char in $text.ToCharArray()) {
    $ws.SendKeys($char)
    Start-Sleep -Milliseconds 100
}
