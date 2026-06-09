@echo off
title Capture Card Viewer - UGREEN 25854
echo ============================================
echo   Capture Card Viewer (Low Latency)
echo ============================================
echo.
echo Steuerung:
echo   F     = Vollbild ein/aus
echo   Q/ESC = Beenden
echo   M     = Ton stumm/laut
echo   SPACE = Pause
echo.
echo Starte Stream...
ffplay -f dshow -rtbufsize 150M -audio_buffer_size 50 -fflags nobuffer -flags low_delay -probesize 32 -analyzeduration 0 -framedrop -video_size 1920x1080 -framerate 60 -i "video=UGREEN 25854:audio=Digitale Audioschnittstelle (UGREEN 25854)" -fs -window_title "Capture Viewer" -loglevel quiet
