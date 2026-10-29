# SI-M · Super Intelligence Mobile

**Android-Begleit-App für den SI-M Orb.** Einrichtung, Gerätesteuerung und eigene animierte Figuren in einer App. Dieses Repository liefert Quellcode, **keinen fertigen APK-Download**; die kompatible Orb-Firmware ist ein separates Projekt.

![Fünf Figuren, jeweils ein tatsächlicher C-gerenderter RGB565-Referenzframe](public/assets/accepted-figures.png)

*Vorschau aus den mitgelieferten RGB565-Frames; kein Gerätescreenshot.*

## Funktionen

- **Lokales Profil:** Anmeldung ohne Server mit PBKDF2-HMAC-SHA256 und Android-Keystore-verschlüsselten Preferences.
- **Bluetooth LE:** Scan, Kopplung mit System-Passkey und gegenseitiger Besitznachweis. Schreibende Gerätebefehle erfordern ein physisch geöffnetes Setup-Fenster am Orb.
- **Orb-Steuerung:** WLAN suchen, speichern und entfernen mit Rücklesung; Lautstärke, Helligkeit und Ruhemodus.
- **Figuren:** Gerätekatalog und Auswahl, fünf mitgelieferte C-gerenderte Figuren (Flamme, Fuchsgeist, Kristalldrache, Himmelseule, Sternen-Axolotl). Der portable C11-Renderer unter [`shared/`](shared/) ermöglicht reproduzierbare, zufällig erzeugte Figuren und native Android-Vorschauen. Die RGB565-Dateien sind Referenzframes, keine Fotos oder Gerätescreenshots.
- **Sprachdienst-Konfiguration:** HTTPS-Bridge für `/v1/turn-stream` einschließlich Bearer-Zugangsdaten auf dem Orb hinterlegen oder entfernen. Stimme, Persona und Erinnerungen sind getrennte Zuständigkeiten; ein öffentlicher KI-Dienst und Zugangsdaten sind nicht enthalten.
- **Kamera:** lokale CameraX-Vorschau; keine Bildübertragung zum Orb und keine behauptete Objekterkennung.
- **App-Update aus GitHub Releases:** Der Knopf „Jetzt prüfen“ liest `releases/latest/download/update.json` aus diesem Repository. Ein gefundenes Update zeigt Version und Änderungsnotizen, lädt die APK erst auf Knopfdruck und prüft dabei Größe, SHA-256, Paketname, Versionsnummer und **die Signatur der bereits installierten App**. Erst danach öffnet sich der Android-Installer; ohne Zustimmung wird nichts installiert. Update-Adressen sind auf die Release-Assets dieses Repositories festgenagelt, Umleitungen auf fremde Hosts werden abgelehnt.
- **Hell und dunkel:** Die App folgt dem Android-Nachtmodus und lässt sich zusätzlich unter **System → Darstellung** fest auf Hell oder Dunkel stellen. Alle Oberflächen beziehen ihre Farben aus einer gemeinsamen semantischen Palette, damit Text und Karten in beiden Modi zusammenpassen; die Kontraste sind per Unit-Test abgesichert.
- **Dashboard mit Symbolen:** Die Tabs tragen Symbole statt Beschriftungen (System, Figuren, Netzwerk, Sprachdienst, App). Der aktive Tab wird benannt, und jedes Symbol hat eine Beschreibung für Screenreader.

Backups sind deaktiviert. Der App-Quellcode ersetzt keine Sicherheits- oder Funktionsprüfung auf echter Hardware.

## Updates veröffentlichen

Die App liest ausschließlich `https://github.com/jokaje/SI-M/releases/latest/download/update.json` und daneben die APK `SI-M.apk` aus **demselben** „latest“-Release. Beide Dateien müssen dort als Release-Assets liegen; erwartet wird genau der neue `versionCode`.

1. APK signiert bauen (gleiches Zertifikat wie die installierte App, sonst lehnt Android die Installation ab).
2. Manifest erzeugen – Größe und SHA-256 werden aus der echten Datei berechnet:

```sh
python3 tools/make_update_manifest.py --apk SI-M-1.2.2.apk \
    --release-notes NOTES.md --output dist/update.json
python3 tools/make_update_manifest.py --apk SI-M-1.2.2.apk --verify dist/update.json
```

3. GitHub-Release mit Tag `v1.2.2` anlegen, `dist/update.json` und die APK als `SI-M.apk` hochladen und als **latest** markieren.

Details, Pflichtfelder und Fehlerfälle: [`RELEASING.md`](RELEASING.md).

## Selbst bauen

Android Studio mit **JDK 17**, **Android SDK 35**, **Build-Tools 36.0.0**, **NDK 27.0.12077973** und **CMake 3.22.1** verwenden. Den Projektroot öffnen; `local.properties` mit dem SDK-Pfad nur lokal erzeugen. Das Android-CMake-Projekt bindet `shared/` relativ zum Projektroot ein.

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Unter Windows `gradlew.bat` verwenden. Die Debug-APK entsteht unter `app/build/outputs/apk/debug/app-debug.apk`. Sie trägt einen **Debug-Signaturschlüssel**, nicht automatisch das Zertifikat einer zuvor installierten App. Für ein Update ist deren bestehendes Release-Zertifikat nötig; Signing-Keys und eine veröffentlichte Release-APK sind hier nicht enthalten. Quellversion: **1.2.3 (versionCode 9)**.

Renderer-Testanleitung: [`shared/README.md`](shared/README.md). Ein kompletter Sprachdialog erfordert separat bereitgestellte kompatible Orb-Firmware und einen eigens konfigurierten Dienst; der App-Build allein beweist keinen Gerätebetrieb.

## Herkunft und Rechte

Laut Ersteller hat er die fünf Figuren mit Muse-Code und Gemini entworfen; die RGB565-Frames stammen aus diesen eigenen Firmware-Renderern. Die Browser-Erstellerdatei baut auf einer prozeduralen Muse-Vorlage auf. Muse-SDK-Code steht unter **Apache-2.0**; die ursprüngliche **Jollybot-Figur ist ausdrücklich ausgenommen**. Siehe [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) und den [vollständigen Upstream-Lizenztext](THIRD_PARTY_MUSE_APACHE_2.0.txt). Diese Hinweise bewahren die gesonderten Drittanbieter-Rechte. **Eigenständige SI-M-Beiträge sind unter [Apache License 2.0](LICENSE) mit [NOTICE](NOTICE) lizenziert.** Der ausgeschlossene Jollybot-Charakter bleibt ausdrücklich ausgenommen.

## Stand und Grenzen

Keine Release-APK und keine behauptete CI-Freigabe. Der In-App-Updater ist implementiert, aber erst nutzbar, sobald ein Release mit `update.json` und signierter `SI-M.apk` veröffentlicht ist; die Prüfsummen-, Größen- und Signaturpfade sind per Unit-Test abgedeckt, der vollständige Gerätetest gegen ein echtes Release steht noch aus. Die App prüft nur auf Knopfdruck und lädt nichts im Hintergrund. Probleme können über GitHub-Issues gemeldet werden; bitte dort keine Zugangsdaten, privaten Endpunkte oder personenbezogenen Logs veröffentlichen.
