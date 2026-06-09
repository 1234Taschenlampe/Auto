# DROLLOLOL RECOVERY TOOL
# ========================
# Restores desktop wallpaper, stops background monitor, and removes Autostart registration.

$ws = New-Object -ComObject Wscript.Shell
$targetDir = "C:\Users\onlym\.gemini\antigravity\scratch\usb-auto-runner"
$registryPath = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run"

# 1. Ask for Password
# We use a simple VBscript input box for a clean, graphical dialog prompt
$inputBoxScript = '
Set objArgs = WScript.Arguments
input = InputBox("Bitte Admin-Passwort eingeben, um den PC wiederherzustellen:", "Drollolol Recovery Tool")
WScript.Echo input
'
$tempVbs = "$env:TEMP\inputbox.vbs"
$inputBoxScript | Out-File $tempVbs -Encoding ascii
$password = (cscript //nologo $tempVbs).Trim()
Remove-Item $tempVbs -ErrorAction SilentlyContinue

if ($password -ne "admin") {
    $ws.Popup("Falsches Passwort! Zugriff verweigert.", 0, "Drollolol Recovery", 16) | Out-Null
    exit
}

# 2. Restore Original Wallpaper (if any exists from older versions)
$code = @'
using System;
using System.Runtime.InteropServices;
public class Wallpaper {
    [DllImport("user32.dll", CharSet = CharSet.Auto)]
    public static extern int SystemParametersInfo(int uAction, int uParam, string lpvParam, int fuWinIni);
}
'@
Add-Type -TypeDefinition $code -ErrorAction SilentlyContinue

$origFile = "$targetDir\original_wallpaper.txt"
if (-not (Test-Path $origFile)) {
    # Fallback to old path if present
    $origFile = "C:\Users\onlym\.gemini\antigravity\scratch\capture-viewer\original_wallpaper.txt"
}

if (Test-Path $origFile) {
    $origPath = (Get-Content $origFile -Raw).Trim()
    if ($origPath -and (Test-Path $origPath)) {
        [Wallpaper]::SystemParametersInfo(20, 0, $origPath, 3) | Out-Null
        $ws.Popup("Hintergrundbild erfolgreich wiederhergestellt!", 0, "Drollolol Recovery", 64) | Out-Null
    } else {
        $ws.Popup("Fehler: Originales Hintergrundbild-Pfad existiert nicht mehr.", 0, "Drollolol Recovery", 48) | Out-Null
    }
    Remove-Item $origFile -Force -ErrorAction SilentlyContinue
} else {
    $ws.Popup("Es wurde kein gespeichertes Original-Hintergrundbild gefunden (bereits wiederhergestellt?).", 0, "Drollolol Recovery", 48) | Out-Null
}

# 3. Stop background monitor processes (both old and new name)
$monitorProcesses = Get-Process | Where-Object { $_.CommandLine -like "*monitor.ps1*" } -ErrorAction SilentlyContinue
if ($monitorProcesses) {
    $monitorProcesses | Stop-Process -Force
}

# 4. Remove Registry Autostart Entries
Remove-ItemProperty -Path $registryPath -Name "USBAutoRunnerMonitor" -ErrorAction SilentlyContinue
Remove-ItemProperty -Path $registryPath -Name "CaptureCardViewerMonitor" -ErrorAction SilentlyContinue

$ws.Popup("Wiederherstellung vollständig abgeschlossen! Autostart wurde entfernt und Hintergrund-Dienst beendet.", 0, "Drollolol Recovery", 64) | Out-Null
