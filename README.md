# SI-M

SI-M (Super Intelligence Mobile) ist die Android-Begleit-App für den SI-M Orb.

## Funktionen

- **Lokales Profil:** Anmeldung ohne Server. Das Passwort wird als
  PBKDF2-HMAC-SHA256-Prüfsumme (210 000 Runden) in
  Android-Keystore-verschlüsselten Preferences gespeichert.
- **BLE-Einrichtung:** echter Scan, Kopplung mit System-Passkey, gegenseitiger
  Besitznachweis (HMAC) und gespeicherter Owner. Keine Demo-Geräte.
- **WLAN:** Netzwerke auf dem Orb suchen, speichern und entfernen. Der Orb
  bestätigt jede Änderung; die App liest den Stand anschließend zurück.
- **Gerät:** Lautstärke, Helligkeit und Ruhemodus direkt am Orb.
- **Figuren:** Katalog vom Orb lesen und Figur auf dem Gerät wechseln.
- **Sprachdienst:** HTTPS-Bridge für `/v1/turn-stream` inklusive
  Bearer-Zugangsdaten auf dem Orb hinterlegen oder entfernen.
- **Kamera-Vorschau:** lokale CameraX-Vorschau auf dem Smartphone. Es werden
  keine Bilder an den Orb gesendet und keine Objekterkennung vorgetäuscht.

## Sicherheit

- Alle schreibenden Befehle benötigen ein am Orb physisch geöffnetes
  Setup-Fenster. Ohne Fenster ist die Verbindung nur lesbar.
- Der Besitznachweis läuft über einen pro Kopplung erzeugten Schlüssel; der
  Schlüssel und die Nonces werden nie protokolliert.
- Zugangsdaten und Passwörter erscheinen nicht in Logs oder in
  `toString()`-Ausgaben.
- Backups sind deaktiviert, damit verschlüsselte Profile nicht auf fremde
  Geräte wandern.

## Bauen

Voraussetzungen: JDK 17, Android SDK 35, Build-Tools 36.0.0.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat :app:assembleDebug
```

Die APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`.

Tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

## Protokoll

Die BLE-UUIDs und die Proof-/Status-Marken sind mit der Orb-Firmware
abgestimmt und bleiben unverändert. Sie sind reine Wire-Protokollwerte und
werden in der App nicht angezeigt.

Der portable C11-Renderer liegt im Projektordner `shared/`; das Android-CMake-Projekt referenziert ihn relativ zum Projektroot. `local.properties` bleibt hostlokal.
