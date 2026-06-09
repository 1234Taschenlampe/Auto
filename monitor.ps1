# monitor.ps1
# This script monitors for a USB drive with a specific volume label and triggers a command.

$configFile = Join-Path $PSScriptRoot "config.json"
if (-not (Test-Path $configFile)) {
    Write-Error "Configuration file (config.json) not found. Please run setup.ps1 first."
    Exit 1
}

$config = Get-Content $configFile | ConvertFrom-Json
$targetLabel = $config.TargetLabel
$actionCommand = $config.ActionCommand
$actionArgs = $config.ActionArguments

Write-Host "=================================================="
Write-Host "USB Auto-Runner Monitor Started"
Write-Host "Target USB Label: $targetLabel"
Write-Host "Action to Execute: $actionCommand $actionArgs"
Write-Host "Press Ctrl+C to stop monitoring."
Write-Host "=================================================="

$detectedDrives = @{}

while ($true) {
    # Find all connected logical disks of type 2 (Removable Disk)
    $drives = Get-CimInstance -ClassName Win32_LogicalDisk -Filter "DriveType=2"
    
    $currentActive = @{}
    
    foreach ($drive in $drives) {
        $driveLetter = $drive.DeviceID # e.g. "E:"
        $volumeName = $drive.VolumeName
        
        # Check if the volume name matches our target label (case-insensitive)
        if ($volumeName -ieq $targetLabel) {
            $currentActive[$driveLetter] = $true
            
            # If we haven't seen this drive letter in this insertion cycle, trigger action
            if (-not $detectedDrives.ContainsKey($driveLetter)) {
                $detectedDrives[$driveLetter] = $true
                Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Detected target USB: $driveLetter ($volumeName)"
                
                try {
                    Write-Host "Running command: $actionCommand $actionArgs"
                    if ($actionArgs) {
                        Start-Process -FilePath $actionCommand -ArgumentList $actionArgs -NoNewWindow
                    } else {
                        Start-Process -FilePath $actionCommand -NoNewWindow
                    }
                } catch {
                    Write-Warning "Failed to execute action: $_"
                }
            }
        }
    }
    
    # Clean up drives that are no longer connected
    $keys = @($detectedDrives.Keys)
    foreach ($key in $keys) {
        if (-not $currentActive.ContainsKey($key)) {
            $detectedDrives.Remove($key)
            Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Target USB removed from $key"
        }
    }
    
    Start-Sleep -Seconds 2
}
