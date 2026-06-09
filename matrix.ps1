# DROLLOLOL SYSTEM DECRYPTION (Matrix Effect)
# ==========================================

$Host.UI.RawUI.WindowTitle = "DROLLOLOL SYSTEM DECRYPTION"
$Host.UI.RawUI.ForegroundColor = "Green"
$Host.UI.RawUI.BackgroundColor = "Black"
Clear-Host

$width = $Host.UI.RawUI.BufferSize.Width
if ($width -le 0) { $width = 80 }

# Generate column states
$columns = @{}
for ($i = 0; $i -lt $width; $i += 2) {
    $columns[$i] = Get-Random -Minimum 0 -Maximum 40
}

# Run matrix cascade for 15 seconds
$start = Get-Date
while ((Get-Date) -lt $start.AddSeconds(15)) {
    $line = ""
    for ($i = 0; $i -lt $width; $i++) {
        if ($columns.ContainsKey($i)) {
            if ($columns[$i] -le 0) {
                # Print random character
                $line += [char](Get-Random -Minimum 33 -Maximum 126)
                if ((Get-Random -Minimum 0 -Maximum 10) -eq 0) {
                    $columns[$i] = Get-Random -Minimum 5 -Maximum 20
                }
            } else {
                $line += " "
                $columns[$i]--
            }
        } else {
            $line += " "
        }
    }
    Write-Host $line -NoNewline
    Start-Sleep -Milliseconds 40
}

Clear-Host
Write-Host "DECRYPTION COMPLETE. SYSTEM COMPROMISED." -ForegroundColor Red
Start-Sleep -Seconds 2
exit
