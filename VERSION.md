# SI-M 1.2.4 (versionCode 10)

**Neu: Ein-Klick-Netzwerkverbindung.** Im Tab **Netzwerk** gibt es einen einzigen Knopf, der den Orb verbindet, statt „Netzwerke suchen → Netz antippen → Passwort tippen“. Ablauf: Ist für das stärkste erreichbare Netz schon ein Passwort bekannt, wird es ohne Nachfrage an den Orb gesendet. Ist es unbekannt, wird es **einmal** abgefragt und danach lokal gemerkt — der nächste Tipp genügt.

**Zur Ehrlichkeit des Ablaufs:** Erfolg meldet die App erst, wenn der Orb selbst die Verbindung bestätigt. Nach dem Senden fragt die App den Orb-Status ab und meldet „Verbunden mit …“ nur bei echter Bestätigung; bleibt die Bestätigung aus, gibt es nach einer Frist eine Fehlermeldung samt Ursache. Ein abgelehntes oder nicht funktionierendes Passwort wird wieder verworfen, damit es nicht stillschweigend erneut gesendet wird.

**Wo das Passwort liegt:** in Android-Keystore-gestützten verschlüsselten Einstellungen (dieselbe Technik wie das lokale Profil). Es wird ausschließlich an den gekoppelten Orb gesendet, wenn eine Verbindung aufgebaut wird. „Entfernen“ bei einem gespeicherten Netz löscht es auch lokal.

**Weitere Verbesserungen im Netzwerk-Tab:** gefundene Netze sind nach Signalstärke sortiert („sehr gut / gut / mittel / schwach“ plus dBm statt nur Rohwert); offene Netze werden ausdrücklich als nicht speicherbar gekennzeichnet, weil das Provisionierungsprotokoll nur Netze mit Passwort kennt; der Fortschritt ist in jedem Schritt sichtbar (Suchen, Senden, Prüfen) und ein Fehlversuch lässt sich direkt zurücksetzen.

**Grenzen:** Der Ein-Klick-Ablauf braucht Orb-Verbindung und Eigentümernachweis — ohne beides wird nichts gesendet, und die App sagt das auch. Ein bereits verbundenes Netz wird nicht erneut geschrieben. Physischer Orb und Firmware sind separat; dieser Build trägt weiterhin den Debug-Schlüssel.

**Enthalten aus 1.2.3:** echter Dunkelmodus (System / Hell / Dunkel) mit gemeinsamer semantischer Palette und Icon-Tabs im Dashboard.
