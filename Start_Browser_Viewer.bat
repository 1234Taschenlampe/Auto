@echo off
title Capture Card Web Viewer Server

:: Check if server is already running on port 8088
netstat -ano | findstr LISTENING | findstr :8088 >nul
if %errorlevel% equ 0 (
    echo Web-Viewer Server laeuft bereits auf http://localhost:8088
    echo Oeffne Webbrowser...
    start "" "http://localhost:8088/index.html"
    exit /b
)

echo ===================================================
echo   Starte Web-Viewer Server auf http://localhost:8088
echo ===================================================
echo.
echo Oeffne Webbrowser...
start "" "http://localhost:8088/index.html"
python -m http.server 8088 --bind 127.0.0.1
pause
