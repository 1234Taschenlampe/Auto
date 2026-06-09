"""
Testet verschiedene Methoden, die UGREEN Capture-Karte zu oeffnen.
"""
import cv2
import sys

DEVICE_NAME = "UGREEN 25854"

print(f"OpenCV Version: {cv2.__version__}")
print(f"Build Info (backends):")
# Show available backends
for attr in dir(cv2):
    if attr.startswith("CAP_"):
        val = getattr(cv2, attr)
        if isinstance(val, int) and val > 0 and val < 10000:
            pass  # too many

print()

# Method 1: Open by index with various backends
print("=== Methode 1: Index + Backend ===")
backends = [
    ("CAP_ANY", cv2.CAP_ANY),
    ("CAP_MSMF", cv2.CAP_MSMF),
    ("CAP_DSHOW", cv2.CAP_DSHOW),
]

for idx in range(5):
    for bname, bid in backends:
        cap = cv2.VideoCapture(idx, bid)
        if cap.isOpened():
            ret, frame = cap.read()
            w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
            h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
            cap.release()
            print(f"  Index {idx} + {bname}: {w}x{h} - {'OK' if ret else 'Kein Bild'}")
        else:
            cap.release()

# Method 2: Open by name (DirectShow graph name)
print("\n=== Methode 2: Oeffnen per Name ===")
name_variants = [
    f"video={DEVICE_NAME}",
    DEVICE_NAME,
    f"@device_pnp_\\\\?\\usb#vid_2b89&pid_5854",
]

for name in name_variants:
    for bname, bid in backends:
        cap = cv2.VideoCapture(name, bid)
        if cap.isOpened():
            ret, frame = cap.read()
            w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
            h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
            cap.release()
            print(f"  '{name}' + {bname}: {w}x{h} - {'OK' if ret else 'Kein Bild'}")
        else:
            cap.release()

# Method 3: Try higher indices up to 20
print("\n=== Methode 3: Erweiteter Index-Scan (0-20) ===")
for idx in range(20):
    cap = cv2.VideoCapture(idx)
    if cap.isOpened():
        ret, frame = cap.read()
        w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        backend = cap.getBackendName()
        cap.release()
        print(f"  Index {idx} ({backend}): {w}x{h} - {'OK' if ret else 'Kein Bild'}")
    else:
        cap.release()

print("\nFertig.")
