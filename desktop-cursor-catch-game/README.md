# 🟢 Desktop Cursor Catch Game

Ein natives Windows-Desktop-Spiel (WPF), bei dem sich ein Slime-Cursor vom echten Cursor löst und über den Bildschirm flieht. Fange ihn ein!

## 🎮 Spielablauf

1. **Start** – Schwierigkeit wählen und Start klicken
2. **Split-Animation** – Der Slime löst sich klebrig vom Cursor ab
3. **Jagen** – Fange den fliehenden Slime-Cursor mit deinem Spieler-Cursor
4. **Gefangen!** – Slime zerplatzt in Schleimtropfen

## 🕹️ Steuerung

| Taste | Aktion |
|-------|--------|
| **Maus** | Spieler-Cursor bewegen |
| **ESC** | Spiel sofort beenden |
| **Alt+F4** | Spiel sofort beenden |

## ⚙️ Schwierigkeitsgrade

| Grad | Speed | Fangradius | Zeitlimit |
|------|-------|-----------|-----------|
| Einfach | Langsam | 35px | 60s |
| Normal | Mittel | 25px | 45s |
| Schwer | Schnell | 18px | 30s |

## 🛡️ Sicherheit

- ✅ Kein Autostart, keine Registry-Änderungen
- ✅ Keine Admin-Rechte nötig
- ✅ ESC/Alt+F4 beendet sofort
- ✅ Automatisches Ende nach max. 65 Sekunden
- ✅ Systemcursor wird nach Beenden wiederhergestellt
- ✅ Keine Systemfunktionen werden blockiert

## 🔧 Build & Start

```powershell
cd desktop-cursor-catch-game
dotnet build
dotnet run
```

Oder die fertige `.exe` starten:
```
bin\Debug\net10.0-windows\DesktopCursorCatchGame.exe
```

## 📁 Projektstruktur

```
desktop-cursor-catch-game/
├── MainWindow.xaml          # UI Layout (Vollbild-Overlay)
├── MainWindow.xaml.cs       # Spiellogik & Rendering
├── GameLoop.cs              # Frame-basierte Spielschleife
├── CursorEntity.cs          # Spieler-Cursor (folgt Maus smooth)
├── SlimeCursorEntity.cs     # KI-gesteuerter fliehender Slime
├── ParticleSystem.cs        # Partikel, Schleimspur, Explosionen
├── CollisionDetection.cs    # Kollisionserkennung
├── DifficultySettings.cs    # Schwierigkeitsgrade
├── SafeExitManager.cs       # Sicherer Exit-Manager
└── README.md
```
