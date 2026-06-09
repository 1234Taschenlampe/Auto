"""
Listet alle verfügbaren Videogeräte auf Windows auf.
Gibt den Index aus, der für OpenCV verwendet werden kann.
"""
import subprocess, re

def list_devices_wmi():
    """List video devices using PowerShell/WMI."""
    ps_cmd = (
        "Get-CimInstance Win32_PnPEntity | "
        "Where-Object { $_.PNPClass -eq 'Camera' -or $_.PNPClass -eq 'Image' -or "
        "($_.PNPClass -eq 'MEDIA' -and $_.Name -match 'video|capture|cam|ugreen|hdmi') } | "
        "Select-Object Name, PNPClass, Status | Format-Table -AutoSize"
    )
    result = subprocess.run(["powershell", "-Command", ps_cmd], capture_output=True, text=True)
    print("=== Windows-Geräte ===")
    print(result.stdout)

def probe_opencv_indices(max_index=10):
    """Try opening each index with OpenCV to find active cameras."""
    import cv2
    print("=== OpenCV Kamera-Index-Scan ===")
    found = []
    for i in range(max_index):
        cap = cv2.VideoCapture(i, cv2.CAP_DSHOW)
        if cap.isOpened():
            w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
            h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
            fps = cap.get(cv2.CAP_PROP_FPS)
            ret, frame = cap.read()
            status = "[OK] Bild empfangen" if ret and frame is not None else "[X] Kein Bild"
            print(f"  Index {i}: {w}x{h} @ {fps:.0f}fps - {status}")
            found.append(i)
            cap.release()
        else:
            pass  # Skip non-existent indices silently
    
    if not found:
        print("  Keine Kameras gefunden!")
    else:
        print(f"\n  Gefundene Indizes: {found}")
    print()

if __name__ == "__main__":
    list_devices_wmi()
    probe_opencv_indices()
    print("Nutze den Index deiner UGREEN Capture-Karte im Viewer.")
    input("Drücke Enter zum Beenden...")
