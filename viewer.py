"""
Capture Card Fullscreen Viewer
==============================
Zeigt das Bild einer Capture-Karte (z.B. UGREEN) auf dem gesamten Display an.
Nutzt Media Foundation (MSMF) und DirectShow (DSHOW) Backends.

Steuerung:
  F / F11  - Vollbild umschalten
  M        - Modus: Einpassen / Fuellen
  O        - OSD ein/aus
  Q / ESC  - Beenden
  0-9      - Kamera-Index direkt waehlen
  +/-      - Naechste/Vorherige Kamera
"""

import cv2
import sys
import time
import ctypes
import argparse
import numpy as np

# --- Windows DPI awareness for sharp rendering ---
try:
    ctypes.windll.shcore.SetProcessDpiAwareness(2)
except Exception:
    try:
        ctypes.windll.user32.SetProcessDPIAware()
    except Exception:
        pass


def get_screen_resolution():
    """Get primary monitor resolution via Windows API."""
    try:
        user32 = ctypes.windll.user32
        return user32.GetSystemMetrics(0), user32.GetSystemMetrics(1)
    except Exception:
        return 1920, 1080


def probe_all_backends(max_index=10):
    """Probe all cameras with both MSMF and DSHOW backends."""
    backends = [
        ("MSMF", cv2.CAP_MSMF),
        ("DSHOW", cv2.CAP_DSHOW),
    ]
    results = []

    for name, backend_id in backends:
        print(f"\n  --- {name} Backend ---")
        for i in range(max_index):
            cap = cv2.VideoCapture(i, backend_id)
            if cap.isOpened():
                # Try to get a frame
                ret, frame = cap.read()
                w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
                h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
                fps = cap.get(cv2.CAP_PROP_FPS)
                status = "OK" if ret and frame is not None else "Kein Bild"
                print(f"  Index {i}: {w}x{h} @ {fps:.0f}fps [{status}]")
                results.append({
                    "index": i,
                    "backend": backend_id,
                    "backend_name": name,
                    "width": w,
                    "height": h,
                    "fps": fps,
                    "has_frame": ret and frame is not None,
                })
                cap.release()
            else:
                cap.release()

    return results


def try_open_camera(index, backends=None):
    """Try to open a camera with multiple backends, return (cap, backend_name) or (None, None)."""
    if backends is None:
        backends = [
            ("MSMF", cv2.CAP_MSMF),
            ("DSHOW", cv2.CAP_DSHOW),
            ("ANY", cv2.CAP_ANY),
        ]

    for name, backend_id in backends:
        cap = cv2.VideoCapture(index, backend_id)
        if cap.isOpened():
            ret, _ = cap.read()
            if ret:
                print(f"  Kamera {index} geoeffnet mit {name}")
                return cap, name
            cap.release()

    return None, None


def configure_capture(cap, target_w=1920, target_h=1080, target_fps=60):
    """Configure capture for best quality."""
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, target_w)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, target_h)
    cap.set(cv2.CAP_PROP_FPS, target_fps)
    # Reduce buffer to minimize latency
    cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)

    actual_w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    actual_h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    actual_fps = cap.get(cv2.CAP_PROP_FPS)
    return actual_w, actual_h, actual_fps


def resize_contain(frame, target_w, target_h):
    """Resize to fit inside target (letterbox)."""
    h, w = frame.shape[:2]
    scale = min(target_w / w, target_h / h)
    new_w, new_h = int(w * scale), int(h * scale)
    resized = cv2.resize(frame, (new_w, new_h), interpolation=cv2.INTER_LINEAR)
    canvas = np.zeros((target_h, target_w, 3), dtype=np.uint8)
    x_off = (target_w - new_w) // 2
    y_off = (target_h - new_h) // 2
    canvas[y_off:y_off + new_h, x_off:x_off + new_w] = resized
    return canvas


def resize_cover(frame, target_w, target_h):
    """Resize to cover entire target (crop)."""
    h, w = frame.shape[:2]
    scale = max(target_w / w, target_h / h)
    new_w, new_h = int(w * scale), int(h * scale)
    resized = cv2.resize(frame, (new_w, new_h), interpolation=cv2.INTER_LINEAR)
    x_off = (new_w - target_w) // 2
    y_off = (new_h - target_h) // 2
    return resized[y_off:y_off + target_h, x_off:x_off + target_w]


def draw_osd(frame, lines):
    """Draw OSD overlay in top-right corner."""
    font = cv2.FONT_HERSHEY_SIMPLEX
    scale = 0.5
    thickness = 1
    pad = 8
    line_height = 22
    h, w = frame.shape[:2]

    max_text_w = 0
    for line in lines:
        tw = cv2.getTextSize(line, font, scale, thickness)[0][0]
        if tw > max_text_w:
            max_text_w = tw

    box_w = max_text_w + pad * 2
    box_h = line_height * len(lines) + pad * 2
    x1 = w - box_w - 10
    y1 = 10

    overlay = frame.copy()
    cv2.rectangle(overlay, (x1, y1), (x1 + box_w, y1 + box_h), (0, 0, 0), -1)
    cv2.addWeighted(overlay, 0.5, frame, 0.5, 0, frame)

    for i, line in enumerate(lines):
        ty = y1 + pad + (i + 1) * line_height - 4
        cv2.putText(frame, line, (x1 + pad, ty), font, scale, (200, 200, 200), thickness, cv2.LINE_AA)


def create_message_frame(w, h, title, subtitle=""):
    """Create a frame with centered message."""
    frame = np.zeros((h, w, 3), dtype=np.uint8)
    font = cv2.FONT_HERSHEY_SIMPLEX

    ts = cv2.getTextSize(title, font, 1.2, 2)[0]
    tx = (w - ts[0]) // 2
    ty = (h + ts[1]) // 2 - 20
    cv2.putText(frame, title, (tx, ty), font, 1.2, (100, 100, 100), 2, cv2.LINE_AA)

    if subtitle:
        ss = cv2.getTextSize(subtitle, font, 0.6, 1)[0]
        sx = (w - ss[0]) // 2
        sy = ty + 40
        cv2.putText(frame, subtitle, (sx, sy), font, 0.6, (70, 70, 70), 1, cv2.LINE_AA)

    return frame


def run_viewer(camera_index=None):
    """Main viewer loop."""

    WINDOW_NAME = "Capture Card Viewer"
    screen_w, screen_h = get_screen_resolution()
    print(f"Bildschirm: {screen_w}x{screen_h}")

    # --- Find camera ---
    if camera_index is None:
        print("\nSuche alle Kameras...")
        results = probe_all_backends()

        if not results:
            print("\nKeine Kamera gefunden!")
            print("Tipps:")
            print("  - Capture-Karte ab- und wieder anstecken")
            print("  - Windows Geraete-Manager pruefen")
            input("Druecke Enter zum Beenden...")
            return

        # Filter working cameras
        working = [r for r in results if r["has_frame"]]
        if not working:
            print("\nKameras gefunden, aber kein Bild empfangen.")
            print("Ist ein HDMI-Kabel an der Capture-Karte angeschlossen?")
            input("Druecke Enter zum Beenden...")
            return

        # Pick best: prefer MSMF, higher resolution, higher index
        # (capture cards are usually higher indices than built-in webcams)
        working.sort(key=lambda r: (
            r["width"] * r["height"],  # prefer higher resolution
            r["index"],                # prefer higher index
            r["backend_name"] == "MSMF",  # prefer MSMF
        ), reverse=True)

        best = working[0]
        camera_index = best["index"]
        preferred_backend = best["backend"]
        print(f"\nBeste Kamera: Index {best['index']} ({best['backend_name']}) "
              f"- {best['width']}x{best['height']} @ {best['fps']:.0f}fps")
    else:
        preferred_backend = None

    # --- Open camera ---
    if preferred_backend is not None:
        cap = cv2.VideoCapture(camera_index, preferred_backend)
        backend_name = "auto"
        if not cap.isOpened():
            cap, backend_name = try_open_camera(camera_index)
    else:
        cap, backend_name = try_open_camera(camera_index)

    if cap is None or not cap.isOpened():
        print(f"FEHLER: Kann Kamera {camera_index} nicht oeffnen!")
        input("Druecke Enter zum Beenden...")
        return

    # Configure for best quality
    actual_w, actual_h, actual_fps = configure_capture(cap, 1920, 1080, 60)
    print(f"Aktive Aufloesung: {actual_w}x{actual_h} @ {actual_fps:.0f}fps")

    # --- Fullscreen window ---
    cv2.namedWindow(WINDOW_NAME, cv2.WINDOW_NORMAL)
    cv2.setWindowProperty(WINDOW_NAME, cv2.WND_PROP_FULLSCREEN, cv2.WINDOW_FULLSCREEN)

    # --- State ---
    is_fullscreen = True
    fit_mode = "contain"
    show_osd = True
    fps_display = 0.0
    frame_count = 0
    fps_timer = time.time()
    idx = camera_index
    no_signal_count = 0

    print("\n=== Viewer laeuft ===")
    print("  F = Vollbild    M = Modus    O = OSD    Q = Beenden")
    print("  +/- = Kamera wechseln    0-9 = Kamera direkt")

    while True:
        ret, frame = cap.read()

        if not ret:
            no_signal_count += 1
            if no_signal_count > 30:
                err = create_message_frame(screen_w, screen_h,
                    "Kein Signal",
                    "HDMI-Kabel pruefen | +/- zum Kamera wechseln | Q zum Beenden")
                cv2.imshow(WINDOW_NAME, err)
            key = cv2.waitKey(100) & 0xFF
        else:
            no_signal_count = 0

            # FPS
            frame_count += 1
            elapsed = time.time() - fps_timer
            if elapsed >= 1.0:
                fps_display = frame_count / elapsed
                frame_count = 0
                fps_timer = time.time()

            # Resize
            if fit_mode == "contain":
                display = resize_contain(frame, screen_w, screen_h)
            else:
                display = resize_cover(frame, screen_w, screen_h)

            # OSD
            if show_osd:
                draw_osd(display, [
                    f"Kamera {idx} | {actual_w}x{actual_h}",
                    f"{fps_display:.0f} fps | {fit_mode}",
                ])

            cv2.imshow(WINDOW_NAME, display)
            key = cv2.waitKey(1) & 0xFF

        # --- Keys ---
        if key == 255:
            continue

        if key in (ord('q'), ord('Q'), 27):
            break

        elif key in (ord('f'), ord('F')):
            is_fullscreen = not is_fullscreen
            if is_fullscreen:
                cv2.setWindowProperty(WINDOW_NAME, cv2.WND_PROP_FULLSCREEN, cv2.WINDOW_FULLSCREEN)
            else:
                cv2.setWindowProperty(WINDOW_NAME, cv2.WND_PROP_FULLSCREEN, cv2.WINDOW_NORMAL)
                cv2.resizeWindow(WINDOW_NAME, actual_w, actual_h)

        elif key in (ord('m'), ord('M')):
            fit_mode = "cover" if fit_mode == "contain" else "contain"

        elif key in (ord('o'), ord('O')):
            show_osd = not show_osd

        elif key in (ord('+'), ord('=')):
            new_idx = idx + 1
            new_cap, bn = try_open_camera(new_idx)
            if new_cap:
                cap.release()
                cap = new_cap
                idx = new_idx
                actual_w, actual_h, actual_fps = configure_capture(cap, 1920, 1080, 60)
                print(f"-> Kamera {idx}: {actual_w}x{actual_h}")

        elif key == ord('-'):
            if idx > 0:
                new_cap, bn = try_open_camera(idx - 1)
                if new_cap:
                    cap.release()
                    cap = new_cap
                    idx -= 1
                    actual_w, actual_h, actual_fps = configure_capture(cap, 1920, 1080, 60)
                    print(f"-> Kamera {idx}: {actual_w}x{actual_h}")

        elif ord('0') <= key <= ord('9'):
            new_idx = key - ord('0')
            new_cap, bn = try_open_camera(new_idx)
            if new_cap:
                cap.release()
                cap = new_cap
                idx = new_idx
                actual_w, actual_h, actual_fps = configure_capture(cap, 1920, 1080, 60)
                print(f"-> Kamera {idx}: {actual_w}x{actual_h}")
            else:
                print(f"Kamera {new_idx} nicht verfuegbar.")

    cap.release()
    cv2.destroyAllWindows()
    print("Viewer beendet.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Capture Card Fullscreen Viewer - Zeigt Capture-Karte auf gesamtem Display")
    parser.add_argument("-c", "--camera", type=int, default=None,
                        help="Kamera-Index (0, 1, 2...). Ohne Angabe wird automatisch gesucht.")
    args = parser.parse_args()
    run_viewer(camera_index=args.camera)
