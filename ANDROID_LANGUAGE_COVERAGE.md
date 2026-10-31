# Android DE/EN UI-Inventar (lokaler Sprachkandidat)

## Abdeckung

- Gemeinsamer persistierter App-Selektor in Onboarding und System-Einstellungen; derselbe Wert ist der gewünschte Orb-Displaywert. Keine separate Orb-Option.
- `ui/UiLanguage.kt`: statische DE→EN-Anzeigen einschließlich Barrierefreiheit, Dialoge, Validierung, Auth, Kopplung, Onboarding, WLAN/QuickConnect, Figuren/Avatar, Kamera, System und App-Updater. Die UI übersetzt geräteeigene Statusmeldungen erst bei der Anzeige; BLE-Protokollkennungen und Diagnose werden nicht verändert.
- Dynamische App-Texte (Schrittzahl, SSID, bekannte Orb-Adresse, Figur, Slider, WLAN-Zustand, Fehlercode) werden nur für bekannte App-Muster übersetzt. Fremde Inhalte (Agentenprofil, persönliche Namen, SSID, Release Notes, BLE-Diagnose) bleiben unverändert.
- `values-en/strings.xml` enthält englische Android-Ressourcen für App-Name, Untertitel, Verbinden und Kamera-Begründung.
- Nach einer bestätigten Geräteantwort gilt die Orb-Sprache nur für die aktuelle autorisierte Generation. Fensterwechsel räumt die Readback-Aussage ab; Reconnect/Öffnung führt erneut zu Readback und nötigenfalls Write+Readback. Offline oder bei alter Firmware bleibt die Synchronisierung ehrlich ausstehend bzw. als nicht unterstützt markiert.

## Inventurgrenzen

- Automatisierte Quelltext-Inventur: 353 eindeutige statische Übersetzungsschlüssel (vor der letzten kleinen Erweiterung); dynamische Muster separat. Sichtbare, nicht übersetzte deutsche UI-Literale der Inventur sind drei bewusst deutsch gehaltene interne Updater-Statuswerte, die erst bei der Anzeige übersetzt werden. Sprachnamen Deutsch/English sind Eigenbezeichnungen. Persönlichkeits-/Sprachstil-Profile und Firmware-/Agenteninhalte sind keine UI-Strings und werden nicht übersetzt.
- Statischer Inventur- und Unit-Test-Gate ersetzt keine gerenderte Zwei-Sprachen-Abnahme auf einem Telefon und keinen echten BLE-Sprach-Readback mit passender Firmware. Keine Installation/Release durchgeführt.
