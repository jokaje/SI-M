# SI-M Releases und In-App-Update

Die App aktualisiert sich ausschließlich über **GitHub Releases dieses Repositories**. Es gibt keinen Update-Server, kein Konto und kein Token in der App.

## Feste Adressen

| Zweck | Adresse |
| --- | --- |
| Manifest | `https://github.com/jokaje/SI-M/releases/latest/download/update.json` |
| APK | `https://github.com/jokaje/SI-M/releases/latest/download/SI-M.apk` |
| Release-Erkennung | `https://api.github.com/repos/jokaje/SI-M/releases/latest` |

Nur diese Hosts sind erlaubt: `github.com`, `api.github.com`, `objects.githubusercontent.com`, `release-assets.githubusercontent.com`. Umleitungen werden ausschließlich auf diese Hosts verfolgt, `http`, Zugangsdaten in der URL und fremde Ports werden abgelehnt.

## Manifest-Schema (schemaVersion 1)

Genau diese neun Felder, keine weiteren:

```json
{
  "schemaVersion": 1,
  "packageName": "com.aistudio.sim.aiorb",
  "versionCode": 8,
  "versionName": "1.2.2",
  "apkUrl": "https://github.com/jokaje/SI-M/releases/latest/download/SI-M.apk",
  "sha256": "<64 hex, klein>",
  "sizeBytes": 12345678,
  "minSdk": 26,
  "releaseNotes": "Kurztext, max. 4096 Zeichen"
}
```

Abgelehnt werden unter anderem: unbekanntes `schemaVersion`, zusätzliche Felder, fehlende Felder, fremder `packageName`, `versionCode` kleiner oder gleich der installierten Version, `sizeBytes` außerhalb 1 … 200 MiB, `sha256` ohne 64 Hex-Zeichen, `minSdk` über der Geräteversion oder unter 26, `releaseNotes` über 4096 Zeichen und jede `apkUrl`, die nicht exakt die Asset-Adresse oben ist.

## Release-Schritte

1. `versionCode` und `versionName` in `app/build.gradle.kts` erhöhen und `VERSION.md` aktualisieren.
2. APK **mit demselben Zertifikat** wie die installierte App bauen und signieren. Ein anderes Zertifikat führt dazu, dass Android die Installation verweigert — die App prüft die Signatur vorher und bricht mit einer Meldung ab.
3. Manifest aus der echten Datei erzeugen:

```sh
python3 tools/make_update_manifest.py --apk build/SI-M-1.2.2.apk \
    --release-notes NOTES.md --output dist/update.json
python3 tools/make_update_manifest.py --apk build/SI-M-1.2.2.apk --verify dist/update.json
```

4. GitHub-Release anlegen (Tag z. B. `v1.2.2`), die APK als **`SI-M.apk`** und `update.json` als Assets hochladen, Release veröffentlichen und als **latest** markieren.
5. Auf einem Gerät prüfen: „Jetzt prüfen“ findet die Version, Download zeigt Fortschritt, Installation öffnet den System-Installer.

`versionCode`, `versionName` und `minSdk` liest das Skript aus der APK (`aapt2 dump badging`). Fehlt `aapt2` im `PATH`, lassen sich die Werte mit `--version-code`, `--version-name` und `--min-sdk` explizit setzen — die Prüfsumme und die Größe werden immer aus der Datei berechnet.

## Verhalten in der App

- Prüfung und Download laufen **nur auf Knopfdruck**, nie im Hintergrund.
- Nach dem Download werden Größe, SHA-256, Paketname, `versionCode`, `minSdk` sowie die Signatur der installierten App geprüft. Unstimmigkeiten löschen die Datei und zeigen eine Meldung.
- Die Installation startet ausschließlich über den System-Installer nach ausdrücklichem Tippen. Fehlt die Berechtigung „Unbekannte Apps installieren“, öffnet die App die passende Android-Einstellungsseite und verlangt danach einen erneuten Tipp.
- Abbrechen bricht den laufenden Download ab und löscht die Teilanlage.
- Die heruntergeladene Datei liegt nur im Cache (`cacheDir/updates`) und wird vor jedem neuen Download geleert.

## Grenzen

- Voraussetzung ist ein veröffentlichtes Release. Ohne `update.json` und `SI-M.apk` im „latest“-Release meldet die App „Release-Assets fehlen“.
- Der **Debug**-Build trägt den Android-Debug-Schlüssel. Ein Update von einer mit anderem Zertifikat signierten Installation ist damit nicht möglich; für die Weitergabe ist ein eigenes, dauerhaft aufbewahrtes Release-Zertifikat nötig.
- Signaturschlüssel und Passwörter gehören **nicht** ins Repository, sondern in eine lokale, gesicherte Ablage.
