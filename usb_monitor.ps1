# USB Stick Monitor Daemon
# ==============================
# Watches for a USB stick with the name/label "drollolol" and launches the viewer.

$volumeLabel = "drollolol"
$targetDir = "C:\Users\onlym\.gemini\antigravity\scratch\capture-viewer"

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
            # 1. Start Python server if not already running on port 8088
            $portActive = Get-NetTCPConnection -LocalPort 8088 -State Listen -ErrorAction SilentlyContinue
            if (-not $portActive) {
                Start-Process -FilePath "python.exe" -ArgumentList "-m http.server 8088 --bind 127.0.0.1" -WorkingDirectory $targetDir -WindowStyle Hidden
            }
            
            # 2. Open the web viewer in the default browser
            Start-Process "http://localhost:8088/index.html"
            
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
