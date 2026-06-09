@echo off
title Cursor Catch Game
echo ============================================
echo   Desktop Cursor Catch Game - Starter
echo ============================================
echo.

cd /d "%~dp0desktop-cursor-catch-game"

:: Check if the exe exists (pre-built)
if exist "bin\Debug\net10.0-windows\DesktopCursorCatchGame.exe" (
    echo Starte natives Spiel...
    start "" "bin\Debug\net10.0-windows\DesktopCursorCatchGame.exe"
    exit /b
)

:: Otherwise try dotnet run
echo Exe nicht gefunden, versuche dotnet run...
dotnet run
