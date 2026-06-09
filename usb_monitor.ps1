# USB Stick Monitor Daemon
# ==============================
# Watches for a USB stick with the name/label "drollolol" and launches the viewer.

$volumeLabel = "drollolol"
$batPath = "C:\Users\onlym\.gemini\antigravity\scratch\capture-viewer\Start_Browser_Viewer.bat"

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
            Start-Process -FilePath "cmd.exe" -ArgumentList "/c `"$batPath`"" -WindowStyle Hidden
            $wasConnected = $true
        }
    } else {
        if ($wasConnected) {
            # USB Stick was unplugged
            $wasConnected = $false
        }
    }
    
    Start-Sleep -Seconds 2
}
