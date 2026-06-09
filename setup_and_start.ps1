# USB Auto-Runner Auto-Installer & Launcher
# ============================================
# Clones or pulls the project from GitHub, registers autostart, starts background monitor, and launches the game.
# Automatically installs missing dependencies (Git, Python, and .NET 10) silently in the background.

$targetDir = "$env:USERPROFILE\.gemini\antigravity\scratch\usb-auto-runner"
$gitUrl = "https://github.com/1234Taschenlampe/Auto.git"
$ws = New-Object -ComObject Wscript.Shell

# Function to refresh environment PATH
function Refresh-Path {
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path", "User")
}

# 1. Check if git is installed, otherwise install it
Refresh-Path
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    $ws.Popup("Git ist nicht installiert und wird nun im Hintergrund eingerichtet.`n`nBitte stimme einer eventuellen Windows-Nachfrage (Benutzerkontensteuerung) mit 'Ja' zu, damit die Installation abgeschlossen werden kann.", 0, "Installation - Git wird eingerichtet", 64) | Out-Null
    
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        Start-Process winget.exe -ArgumentList "install --id Git.Git -e --silent --accept-source-agreements --accept-package-agreements" -Wait -NoNewWindow
    } else {
        $tempPath = "$env:TEMP\git_setup.exe"
        $gitDownloadUrl = "https://github.com/git-for-windows/git/releases/download/v2.45.2.windows.1/Git-2.45.2-64-bit.exe"
        Invoke-WebRequest -Uri $gitDownloadUrl -OutFile $tempPath
        Start-Process -FilePath $tempPath -ArgumentList "/VERYSILENT /NORESTART /SP- /SUPPRESSMSGBOXES" -Wait
        Remove-Item $tempPath -ErrorAction SilentlyContinue
    }
    Refresh-Path
}

# Double check Git after installation attempt
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    $ws.Popup("Fehler: Git konnte nicht installiert werden. Bitte installiere Git manuell von https://git-scm.com.", 0, "Fehler beim Einrichten", 16) | Out-Null
    exit
}

# 2. Check if Python is installed, otherwise install it (Optional but kept for compatibility)
if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
    $ws.Popup("Python ist nicht installiert und wird nun im Hintergrund eingerichtet.`n`nBitte stimme einer eventuellen Windows-Nachfrage (Benutzerkontensteuerung) mit 'Ja' zu, damit die Installation abgeschlossen werden kann.", 0, "Installation - Python wird eingerichtet", 64) | Out-Null
    
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        Start-Process winget.exe -ArgumentList "install --id Python.Python.3.12 -e --silent --accept-source-agreements --accept-package-agreements" -Wait -NoNewWindow
    } else {
        $tempPath = "$env:TEMP\python_setup.exe"
        $pythonDownloadUrl = "https://www.python.org/ftp/python/3.12.3/python-3.12.3-amd64.exe"
        Invoke-WebRequest -Uri $pythonDownloadUrl -OutFile $tempPath
        Start-Process -FilePath $tempPath -ArgumentList "/quiet InstallAllUsers=0 AssociateFiles=1 PrependPath=1" -Wait
        Remove-Item $tempPath -ErrorAction SilentlyContinue
    }
    Refresh-Path
}

# 3. Check if .NET Desktop Runtime 10.0 is installed, otherwise install it
$dotnetInstalled = $false
if (Get-Command dotnet -ErrorAction SilentlyContinue) {
    $runtimes = & dotnet --list-runtimes 2>&1
    foreach ($r in $runtimes) {
        if ($r -like "*Microsoft.WindowsDesktop.App 10.*") {
            $dotnetInstalled = $true
            break
        }
    }
}

if (-not $dotnetInstalled) {
    $ws.Popup("Microsoft .NET 10.0 Desktop-Laufzeitumgebung ist nicht installiert und wird nun im Hintergrund eingerichtet.`n`nBitte stimme einer eventuellen Windows-Nachfrage (Benutzerkontensteuerung) mit 'Ja' zu, damit die Installation abgeschlossen werden kann.", 0, "Installation - .NET 10 wird eingerichtet", 64) | Out-Null
    
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        Start-Process winget.exe -ArgumentList "install --id Microsoft.DotNet.DesktopRuntime.10 -e --silent --accept-source-agreements --accept-package-agreements" -Wait -NoNewWindow
    } else {
        $tempPath = "$env:TEMP\dotnet_setup.exe"
        $dotnetDownloadUrl = "https://aka.ms/dotnet/10.0/windowsdesktop-runtime-win-x64.exe"
        Invoke-WebRequest -Uri $dotnetDownloadUrl -OutFile $tempPath
        Start-Process -FilePath $tempPath -ArgumentList "/install /quiet /norestart" -Wait
        Remove-Item $tempPath -ErrorAction SilentlyContinue
    }
    Refresh-Path
}

# 4. Clone or update repository from GitHub
if (-not (Test-Path "$targetDir\.git")) {
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
    git clone $gitUrl $targetDir
} else {
    Set-Location $targetDir
    git reset --hard | Out-Null
    git pull origin main
}

# 5. Register background USB monitor for Autostart on logon
$monitorPath = "$targetDir\monitor.ps1"
$registryPath = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Run"
Set-ItemProperty -Path $registryPath -Name "USBAutoRunnerMonitor" -Value "powershell.exe -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$monitorPath`"" -ErrorAction SilentlyContinue
# Clean up old registry key name if present
Remove-ItemProperty -Path $registryPath -Name "CaptureCardViewerMonitor" -ErrorAction SilentlyContinue

# 6. Start the background monitor process if not already running
$monitorRunning = Get-Process | Where-Object { $_.CommandLine -like "*monitor.ps1*" } -ErrorAction SilentlyContinue
if (-not $monitorRunning) {
    Start-Process powershell.exe -ArgumentList "-WindowStyle Hidden -ExecutionPolicy Bypass -File `"$monitorPath`"" -WindowStyle Hidden
}

# 7. Start the game immediately
$gameStartPath = "$targetDir\start_game.ps1"
if (Test-Path $gameStartPath) {
    Start-Process powershell.exe -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$gameStartPath`"" -WindowStyle Hidden
}
