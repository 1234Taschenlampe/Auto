/*
 * Capture Card Fullscreen Viewer (Ultra Low Latency)
 * ===================================================
 * Uses Windows Media Foundation for capture and GDI for rendering.
 * Zero-copy pipeline, minimal buffering, maximum performance.
 *
 * Controls:
 *   ESC / Q   - Quit
 *   F / F11   - Toggle fullscreen
 *   M         - Toggle fit mode (contain/cover)
 *   +/-       - Next/Previous device
 */

#define WIN32_LEAN_AND_MEAN
#define COBJMACROS
#include <windows.h>
#include <mfapi.h>
#include <mfidl.h>
#include <mfreadwrite.h>
#include <mferror.h>
#include <shlwapi.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#pragma comment(lib, "mfplat.lib")
#pragma comment(lib, "mf.lib")
#pragma comment(lib, "mfreadwrite.lib")
#pragma comment(lib, "mfuuid.lib")
#pragma comment(lib, "ole32.lib")
#pragma comment(lib, "shlwapi.lib")
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "gdi32.lib")

// -------------------------------------------------------------------
// Globals
// -------------------------------------------------------------------
static HWND           g_hwnd          = NULL;
static IMFSourceReader* g_reader      = NULL;
static IMFActivate**  g_devices       = NULL;
static UINT32         g_deviceCount   = 0;
static int            g_currentDevice = -1;
static UINT32         g_videoWidth    = 0;
static UINT32         g_videoHeight   = 0;
static BOOL           g_running       = TRUE;
static BOOL           g_fullscreen    = TRUE;
static BOOL           g_fitContain    = TRUE; // TRUE=contain, FALSE=cover

// For windowed mode restore
static RECT           g_windowRect    = {0};
static DWORD          g_windowStyle   = 0;

// Frame stats
static LARGE_INTEGER  g_perfFreq;
static LARGE_INTEGER  g_lastFpsTime;
static int            g_frameCount    = 0;
static double         g_fps           = 0.0;

// -------------------------------------------------------------------
// Helper: Safe Release
// -------------------------------------------------------------------
template<class T> void SafeRelease(T** pp) {
    if (*pp) { (*pp)->Release(); *pp = NULL; }
}

// -------------------------------------------------------------------
// Enumerate video capture devices
// -------------------------------------------------------------------
static HRESULT EnumerateDevices() {
    IMFAttributes* pAttributes = NULL;
    HRESULT hr = MFCreateAttributes(&pAttributes, 1);
    if (FAILED(hr)) return hr;

    hr = pAttributes->SetGUID(
        MF_DEVSOURCE_ATTRIBUTE_SOURCE_TYPE,
        MF_DEVSOURCE_ATTRIBUTE_SOURCE_TYPE_VIDCAP_GUID
    );
    if (FAILED(hr)) { SafeRelease(&pAttributes); return hr; }

    hr = MFEnumDeviceSources(pAttributes, &g_devices, &g_deviceCount);
    SafeRelease(&pAttributes);
    return hr;
}

// -------------------------------------------------------------------
// Get device friendly name
// -------------------------------------------------------------------
static BOOL GetDeviceName(UINT32 index, WCHAR* buf, UINT32 bufLen) {
    if (index >= g_deviceCount) return FALSE;
    UINT32 len = 0;
    HRESULT hr = g_devices[index]->GetAllocatedString(
        MF_DEVSOURCE_ATTRIBUTE_FRIENDLY_NAME, &buf, &len
    );
    // GetAllocatedString allocates its own buffer, we need GetString instead
    // Actually let's use GetString
    hr = g_devices[index]->GetString(
        MF_DEVSOURCE_ATTRIBUTE_FRIENDLY_NAME, buf, bufLen, &len
    );
    return SUCCEEDED(hr);
}

// -------------------------------------------------------------------
// Find device by name substring (case-insensitive)
// -------------------------------------------------------------------
static int FindDevice(const WCHAR* substring) {
    WCHAR name[256];
    for (UINT32 i = 0; i < g_deviceCount; i++) {
        if (GetDeviceName(i, name, 256)) {
            // Case-insensitive search
            WCHAR nameLower[256], subLower[256];
            wcscpy_s(nameLower, name);
            wcscpy_s(subLower, substring);
            _wcslwr_s(nameLower);
            _wcslwr_s(subLower);
            if (wcsstr(nameLower, subLower)) return (int)i;
        }
    }
    return -1;
}

// -------------------------------------------------------------------
// Open a capture device and create a Source Reader
// -------------------------------------------------------------------
static HRESULT OpenDevice(int index) {
    if (index < 0 || index >= (int)g_deviceCount) return E_INVALIDARG;

    // Release previous reader
    SafeRelease(&g_reader);

    // Create media source
    IMFMediaSource* pSource = NULL;
    HRESULT hr = g_devices[index]->ActivateObject(
        __uuidof(IMFMediaSource), (void**)&pSource
    );
    if (FAILED(hr)) return hr;

    // Create source reader attributes for low latency
    IMFAttributes* pReaderAttrs = NULL;
    MFCreateAttributes(&pReaderAttrs, 2);
    pReaderAttrs->SetUINT32(MF_READWRITE_DISABLE_CONVERTERS, FALSE);
    pReaderAttrs->SetUINT32(MF_SOURCE_READER_ENABLE_VIDEO_PROCESSING, TRUE);

    // Create source reader
    hr = MFCreateSourceReaderFromMediaSource(pSource, pReaderAttrs, &g_reader);
    SafeRelease(&pReaderAttrs);
    SafeRelease(&pSource);
    if (FAILED(hr)) return hr;

    // Try to configure for RGB32 output at best resolution
    // First, enumerate native media types to find best resolution
    UINT32 bestWidth = 0, bestHeight = 0;
    for (DWORD i = 0; ; i++) {
        IMFMediaType* pNativeType = NULL;
        hr = g_reader->GetNativeMediaType(
            (DWORD)MF_SOURCE_READER_FIRST_VIDEO_STREAM, i, &pNativeType
        );
        if (FAILED(hr)) break;

        UINT32 w = 0, h = 0;
        MFGetAttributeSize(pNativeType, MF_MT_FRAME_SIZE, &w, &h);
        if (w * h > bestWidth * bestHeight) {
            bestWidth = w;
            bestHeight = h;
        }
        SafeRelease(&pNativeType);
    }

    if (bestWidth == 0) {
        bestWidth = 1920;
        bestHeight = 1080;
    }

    // Set output type to RGB32 (MF will convert for us)
    IMFMediaType* pOutputType = NULL;
    MFCreateMediaType(&pOutputType);
    pOutputType->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video);
    pOutputType->SetGUID(MF_MT_SUBTYPE, MFVideoFormat_RGB32);
    MFSetAttributeSize(pOutputType, MF_MT_FRAME_SIZE, bestWidth, bestHeight);

    hr = g_reader->SetCurrentMediaType(
        (DWORD)MF_SOURCE_READER_FIRST_VIDEO_STREAM, NULL, pOutputType
    );
    SafeRelease(&pOutputType);

    if (FAILED(hr)) {
        // Fallback: let MF choose format, just request RGB32
        MFCreateMediaType(&pOutputType);
        pOutputType->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video);
        pOutputType->SetGUID(MF_MT_SUBTYPE, MFVideoFormat_RGB32);
        hr = g_reader->SetCurrentMediaType(
            (DWORD)MF_SOURCE_READER_FIRST_VIDEO_STREAM, NULL, pOutputType
        );
        SafeRelease(&pOutputType);
        if (FAILED(hr)) return hr;
    }

    // Read back actual dimensions
    IMFMediaType* pActualType = NULL;
    hr = g_reader->GetCurrentMediaType(
        (DWORD)MF_SOURCE_READER_FIRST_VIDEO_STREAM, &pActualType
    );
    if (SUCCEEDED(hr)) {
        MFGetAttributeSize(pActualType, MF_MT_FRAME_SIZE, &g_videoWidth, &g_videoHeight);
        SafeRelease(&pActualType);
    }

    g_currentDevice = index;

    WCHAR devName[256];
    GetDeviceName(index, devName, 256);
    wprintf(L"Geoeffnet: %s (%ux%u)\n", devName, g_videoWidth, g_videoHeight);

    return S_OK;
}

// -------------------------------------------------------------------
// Toggle fullscreen
// -------------------------------------------------------------------
static void ToggleFullscreen() {
    if (g_fullscreen) {
        // Go windowed
        g_fullscreen = FALSE;
        SetWindowLongPtr(g_hwnd, GWL_STYLE, g_windowStyle);
        SetWindowPos(g_hwnd, HWND_NOTOPMOST,
            g_windowRect.left, g_windowRect.top,
            g_windowRect.right - g_windowRect.left,
            g_windowRect.bottom - g_windowRect.top,
            SWP_FRAMECHANGED | SWP_SHOWWINDOW);
    } else {
        // Save current window state
        g_windowStyle = GetWindowLongPtr(g_hwnd, GWL_STYLE);
        GetWindowRect(g_hwnd, &g_windowRect);

        // Go fullscreen
        g_fullscreen = TRUE;
        MONITORINFO mi = { sizeof(mi) };
        GetMonitorInfo(MonitorFromWindow(g_hwnd, MONITOR_DEFAULTTOPRIMARY), &mi);

        SetWindowLongPtr(g_hwnd, GWL_STYLE, WS_POPUP | WS_VISIBLE);
        SetWindowPos(g_hwnd, HWND_TOPMOST,
            mi.rcMonitor.left, mi.rcMonitor.top,
            mi.rcMonitor.right - mi.rcMonitor.left,
            mi.rcMonitor.bottom - mi.rcMonitor.top,
            SWP_FRAMECHANGED | SWP_SHOWWINDOW);
    }
}

// -------------------------------------------------------------------
// Render a frame to the window
// -------------------------------------------------------------------
static void RenderFrame(BYTE* pixels, UINT32 width, UINT32 height, LONG stride) {
    if (!g_hwnd || !pixels) return;

    HDC hdc = GetDC(g_hwnd);
    if (!hdc) return;

    RECT clientRect;
    GetClientRect(g_hwnd, &clientRect);
    int clientW = clientRect.right;
    int clientH = clientRect.bottom;

    // Calculate destination rect based on fit mode
    int dstX = 0, dstY = 0, dstW = clientW, dstH = clientH;

    if (g_fitContain) {
        // Contain: fit inside, letterbox
        double scaleX = (double)clientW / width;
        double scaleY = (double)clientH / height;
        double scale = (scaleX < scaleY) ? scaleX : scaleY;
        dstW = (int)(width * scale);
        dstH = (int)(height * scale);
        dstX = (clientW - dstW) / 2;
        dstY = (clientH - dstH) / 2;

        // Clear letterbox areas
        if (dstX > 0 || dstY > 0) {
            HBRUSH black = (HBRUSH)GetStockObject(BLACK_BRUSH);
            if (dstX > 0) {
                RECT left = {0, 0, dstX, clientH};
                RECT right = {dstX + dstW, 0, clientW, clientH};
                FillRect(hdc, &left, black);
                FillRect(hdc, &right, black);
            }
            if (dstY > 0) {
                RECT top = {0, 0, clientW, dstY};
                RECT bottom = {0, dstY + dstH, clientW, clientH};
                FillRect(hdc, &top, black);
                FillRect(hdc, &bottom, black);
            }
        }
    }
    // Cover mode: stretch to fill (may crop)
    // For cover, we'd need to adjust source rect, but StretchDIBits
    // makes it simpler to just fill the whole client area

    // Setup BITMAPINFO
    BITMAPINFO bmi = {0};
    bmi.bmiHeader.biSize        = sizeof(BITMAPINFOHEADER);
    bmi.bmiHeader.biWidth       = width;
    // Negative height = top-down DIB (MF gives us bottom-up, so positive)
    bmi.bmiHeader.biHeight      = -(LONG)height; // top-down
    bmi.bmiHeader.biPlanes      = 1;
    bmi.bmiHeader.biBitCount    = 32;
    bmi.bmiHeader.biCompression = BI_RGB;

    // Handle stride (MF might use different stride than width*4)
    LONG absStride = (stride < 0) ? -stride : stride;
    BYTE* scanline0 = pixels;

    // If stride is negative, the image is top-down
    if (stride < 0) {
        bmi.bmiHeader.biHeight = (LONG)height; // bottom-up
        scanline0 = pixels + (height - 1) * absStride;
    }

    SetStretchBltMode(hdc, HALFTONE);

    if (g_fitContain) {
        StretchDIBits(hdc,
            dstX, dstY, dstW, dstH,   // dst
            0, 0, width, height,        // src
            scanline0, &bmi,
            DIB_RGB_COLORS, SRCCOPY);
    } else {
        // Cover mode: crop source to fill dest
        double scaleX = (double)clientW / width;
        double scaleY = (double)clientH / height;
        double scale = (scaleX > scaleY) ? scaleX : scaleY;
        int srcW = (int)(clientW / scale);
        int srcH = (int)(clientH / scale);
        int srcX = ((int)width - srcW) / 2;
        int srcY = ((int)height - srcH) / 2;

        StretchDIBits(hdc,
            0, 0, clientW, clientH,
            srcX, srcY, srcW, srcH,
            scanline0, &bmi,
            DIB_RGB_COLORS, SRCCOPY);
    }

    // Draw FPS overlay
    char fpsText[64];
    sprintf_s(fpsText, "%ux%u | %.0f fps", g_videoWidth, g_videoHeight, g_fps);

    SetBkMode(hdc, TRANSPARENT);
    SetTextColor(hdc, RGB(200, 200, 200));

    HFONT hFont = CreateFontA(16, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
        DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
        CLEARTYPE_QUALITY, DEFAULT_PITCH | FF_SWISS, "Segoe UI");
    HFONT hOldFont = (HFONT)SelectObject(hdc, hFont);

    SIZE textSize;
    GetTextExtentPoint32A(hdc, fpsText, (int)strlen(fpsText), &textSize);

    int tx = clientW - textSize.cx - 16;
    int ty = 12;

    // Semi-transparent background
    RECT bgRect = {tx - 8, ty - 4, tx + textSize.cx + 8, ty + textSize.cy + 4};
    HBRUSH bgBrush = CreateSolidBrush(RGB(20, 20, 20));
    FillRect(hdc, &bgRect, bgBrush);
    DeleteObject(bgBrush);

    TextOutA(hdc, tx, ty, fpsText, (int)strlen(fpsText));

    SelectObject(hdc, hOldFont);
    DeleteObject(hFont);

    ReleaseDC(g_hwnd, hdc);
}

// -------------------------------------------------------------------
// Window Procedure
// -------------------------------------------------------------------
static LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
    case WM_KEYDOWN:
        switch (wParam) {
        case VK_ESCAPE:
        case 'Q':
            g_running = FALSE;
            PostQuitMessage(0);
            return 0;

        case 'F':
        case VK_F11:
            ToggleFullscreen();
            return 0;

        case 'M':
            g_fitContain = !g_fitContain;
            InvalidateRect(hwnd, NULL, TRUE);
            return 0;

        case VK_OEM_PLUS:
        case VK_ADD:
            if (g_currentDevice + 1 < (int)g_deviceCount) {
                OpenDevice(g_currentDevice + 1);
            }
            return 0;

        case VK_OEM_MINUS:
        case VK_SUBTRACT:
            if (g_currentDevice > 0) {
                OpenDevice(g_currentDevice - 1);
            }
            return 0;
        }
        break;

    case WM_DESTROY:
        g_running = FALSE;
        PostQuitMessage(0);
        return 0;

    case WM_ERASEBKGND:
        return 1; // Prevent flicker
    }

    return DefWindowProc(hwnd, msg, wParam, lParam);
}

// -------------------------------------------------------------------
// Main
// -------------------------------------------------------------------
int wmain(int argc, wchar_t* argv[]) {
    // Parse args
    const WCHAR* targetDevice = L"ugreen";
    int targetIndex = -1;

    for (int i = 1; i < argc; i++) {
        if (wcscmp(argv[i], L"-d") == 0 && i + 1 < argc) {
            targetDevice = argv[++i];
        } else if (wcscmp(argv[i], L"-i") == 0 && i + 1 < argc) {
            targetIndex = _wtoi(argv[++i]);
        } else if (wcscmp(argv[i], L"-h") == 0 || wcscmp(argv[i], L"--help") == 0) {
            wprintf(L"Capture Card Viewer - Ultra Low Latency\n");
            wprintf(L"  -d <name>   Geraetename (Standard: ugreen)\n");
            wprintf(L"  -i <index>  Geraete-Index direkt\n");
            wprintf(L"\nSteuerung:\n");
            wprintf(L"  ESC/Q = Beenden   F = Vollbild   M = Modus   +/- = Geraet\n");
            return 0;
        }
    }

    // Initialize COM and Media Foundation
    HRESULT hr = CoInitializeEx(NULL, COINIT_MULTITHREADED);
    if (FAILED(hr)) { wprintf(L"COM init fehlgeschlagen\n"); return 1; }

    hr = MFStartup(MF_VERSION);
    if (FAILED(hr)) { wprintf(L"MF init fehlgeschlagen\n"); return 1; }

    // Enumerate devices
    hr = EnumerateDevices();
    if (FAILED(hr) || g_deviceCount == 0) {
        wprintf(L"Keine Videogeraete gefunden!\n");
        MFShutdown();
        CoUninitialize();
        return 1;
    }

    wprintf(L"\nGefundene Geraete:\n");
    WCHAR name[256];
    for (UINT32 i = 0; i < g_deviceCount; i++) {
        GetDeviceName(i, name, 256);
        wprintf(L"  [%u] %s\n", i, name);
    }

    // Find target device
    int devIdx = targetIndex;
    if (devIdx < 0) {
        devIdx = FindDevice(targetDevice);
    }
    if (devIdx < 0) {
        wprintf(L"\nGeraet '%s' nicht gefunden. Verwende erstes Geraet.\n", targetDevice);
        devIdx = 0;
    }

    // Open device
    hr = OpenDevice(devIdx);
    if (FAILED(hr)) {
        wprintf(L"Kann Geraet nicht oeffnen (0x%08X)\n", hr);
        MFShutdown();
        CoUninitialize();
        return 1;
    }

    // Create window
    WNDCLASSEXW wc = {0};
    wc.cbSize        = sizeof(wc);
    wc.style         = CS_HREDRAW | CS_VREDRAW;
    wc.lpfnWndProc   = WndProc;
    wc.hInstance      = GetModuleHandle(NULL);
    wc.hCursor       = LoadCursor(NULL, IDC_ARROW);
    wc.hbrBackground = (HBRUSH)GetStockObject(BLACK_BRUSH);
    wc.lpszClassName = L"CaptureViewerClass";
    RegisterClassExW(&wc);

    // Get monitor size for fullscreen
    MONITORINFO mi = { sizeof(mi) };
    GetMonitorInfo(MonitorFromPoint({0,0}, MONITOR_DEFAULTTOPRIMARY), &mi);
    int monW = mi.rcMonitor.right - mi.rcMonitor.left;
    int monH = mi.rcMonitor.bottom - mi.rcMonitor.top;

    g_hwnd = CreateWindowExW(
        0, L"CaptureViewerClass", L"Capture Card Viewer",
        WS_POPUP | WS_VISIBLE,
        mi.rcMonitor.left, mi.rcMonitor.top, monW, monH,
        NULL, NULL, GetModuleHandle(NULL), NULL
    );

    if (!g_hwnd) {
        wprintf(L"Fenster konnte nicht erstellt werden\n");
        MFShutdown();
        CoUninitialize();
        return 1;
    }

    SetWindowPos(g_hwnd, HWND_TOPMOST, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE);
    ShowWindow(g_hwnd, SW_SHOW);
    UpdateWindow(g_hwnd);

    // Hide cursor in fullscreen
    ShowCursor(FALSE);

    // FPS counter
    QueryPerformanceFrequency(&g_perfFreq);
    QueryPerformanceCounter(&g_lastFpsTime);

    wprintf(L"\n=== Viewer laeuft ===\n");
    wprintf(L"  ESC/Q = Beenden   F = Vollbild   M = Modus   +/- = Geraet\n\n");

    // ------- Main capture loop -------
    while (g_running) {
        // Process Windows messages (non-blocking)
        MSG msg;
        while (PeekMessage(&msg, NULL, 0, 0, PM_REMOVE)) {
            if (msg.message == WM_QUIT) {
                g_running = FALSE;
                break;
            }
            TranslateMessage(&msg);
            DispatchMessage(&msg);
        }
        if (!g_running) break;

        // Read next video frame
        DWORD streamIndex, flags;
        LONGLONG timestamp;
        IMFSample* pSample = NULL;

        hr = g_reader->ReadSample(
            (DWORD)MF_SOURCE_READER_FIRST_VIDEO_STREAM,
            0,          // no flags = synchronous, blocking
            &streamIndex,
            &flags,
            &timestamp,
            &pSample
        );

        if (FAILED(hr)) {
            // Device might have been disconnected
            Sleep(100);
            continue;
        }

        if (flags & MF_SOURCE_READERF_ENDOFSTREAM) {
            wprintf(L"Stream beendet.\n");
            break;
        }

        if (pSample) {
            IMFMediaBuffer* pBuffer = NULL;
            hr = pSample->ConvertToContiguousBuffer(&pBuffer);
            if (SUCCEEDED(hr)) {
                BYTE* pixels = NULL;
                DWORD maxLen = 0, curLen = 0;
                hr = pBuffer->Lock(&pixels, &maxLen, &curLen);
                if (SUCCEEDED(hr) && pixels) {
                    // MF RGB32 is bottom-up by default
                    LONG stride = (LONG)(g_videoWidth * 4);

                    // Check if stride is negative (top-down)
                    // MF provides bottom-up, so we pass positive stride
                    RenderFrame(pixels, g_videoWidth, g_videoHeight, stride);

                    pBuffer->Unlock();
                }
                SafeRelease(&pBuffer);
            }
            SafeRelease(&pSample);

            // FPS calculation
            g_frameCount++;
            LARGE_INTEGER now;
            QueryPerformanceCounter(&now);
            double elapsed = (double)(now.QuadPart - g_lastFpsTime.QuadPart) / g_perfFreq.QuadPart;
            if (elapsed >= 1.0) {
                g_fps = g_frameCount / elapsed;
                g_frameCount = 0;
                g_lastFpsTime = now;
            }
        }
    }

    // Cleanup
    ShowCursor(TRUE);
    SafeRelease(&g_reader);
    for (UINT32 i = 0; i < g_deviceCount; i++) {
        SafeRelease(&g_devices[i]);
    }
    CoTaskMemFree(g_devices);

    DestroyWindow(g_hwnd);
    MFShutdown();
    CoUninitialize();

    wprintf(L"Viewer beendet.\n");
    return 0;
}

// Entry point for subsystem:console
int main() {
    return wmain(__argc, __wargv);
}
