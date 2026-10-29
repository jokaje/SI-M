# SI-M 1.2.2 (versionCode 8)

**Neu: In-App-Update über GitHub Releases.** Der Knopf „Jetzt prüfen“ liest `releases/latest/download/update.json`, zeigt Version und Änderungsnotizen, lädt die APK nur auf Knopfdruck und prüft Größe, SHA-256, Paketname, `versionCode`, `minSdk` und die Signatur der installierten App, bevor der Android-Installer startet. Update-Adressen sind auf die Release-Assets dieses Repositories festgelegt, fremde Hosts und unsichere Umleitungen werden abgelehnt. Nur manuelle Prüfung, kein Hintergrunddienst, kein automatischer Download.

**Lizenz:** Eigenständige SI-M-Beiträge unter Apache License 2.0 (`LICENSE`, `NOTICE`). Drittanbieter- und Upstream-Rechte bleiben unverändert (`THIRD_PARTY_NOTICES.md`); der ausgeschlossene Jollybot-Charakter bleibt ausgenommen.

**Enthalten aus 1.2.1/1.2.2:** gemeinsamer C11-64×64-Pixelrenderer über JNI, versionierte 48-Byte-DNA, fünf mitgelieferte Figuren mit Einfach-/Experte-Auswahl, lokale Entwürfe und Favoriten, kontrastgeprüfte helle und dunkle Palette, abgesichertes Onboarding, BLE-Kopplung mit Besitznachweis, WLAN-/Gerätebefehle, Sprachdienst-Konfiguration und lokale Kamera-Vorschau.

**Grenzen:** Keine veröffentlichte Release-APK, kein Gerätetest gegen ein echtes GitHub-Release und keine behauptete CI-Freigabe. Der Build trägt den Debug-Schlüssel; für Update-Fähigkeit über Installationen hinweg ist ein dauerhaftes Release-Zertifikat nötig. Physischer Orb und Firmware sind separat.
