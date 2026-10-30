package com.example.orb

/** Labels for a confirmed Networks reply; a cleared cache is not a device report. */
object WifiPresentation {
    private const val UNKNOWN = "Status unbekannt · erneut laden"
    fun current(state: SetupState): String = if (state.wifiStatusKnown) state.currentNetwork.ifBlank { "–" } else UNKNOWN
    fun connection(state: SetupState): String = when {
        !state.wifiStatusKnown -> UNKNOWN
        state.wifiConnected -> "verbunden"
        else -> "nicht verbunden"
    }
    fun saved(state: SetupState): String = when {
        !state.savedNetworksKnown -> "Gespeicherte Netzwerke: $UNKNOWN"
        state.savedNetworks.isEmpty() -> "Keine Netzwerke gespeichert."
        else -> ""
    }
    fun onboarding(state: SetupState): String = when {
        !state.wifiStatusKnown || !state.savedNetworksKnown -> UNKNOWN
        state.currentNetwork.isBlank() && state.savedNetworks.isEmpty() -> "Noch kein Netzwerk gespeichert."
        else -> "Aktuell: ${state.currentNetwork.ifBlank { "–" }} • ${connection(state)}"
    }
    fun summary(state: SetupState): String = when {
        !state.wifiStatusKnown || !state.savedNetworksKnown -> UNKNOWN
        state.currentNetwork.isBlank() && state.savedNetworks.isEmpty() -> "nicht eingerichtet"
        state.currentNetwork.isBlank() -> "nicht verbunden"
        else -> "${state.currentNetwork} (${connection(state)})"
    }
    fun continueLabel(state: SetupState): String =
        if (state.wifiStatusKnown && state.wifiConnected) "Weiter zum Sprachdienst" else "Später einrichten"

    /** Qualitative strength; the raw dBm value stays visible as a secondary detail. */
    fun strength(rssi: Int): String = when {
        rssi >= -55 -> "sehr gut"
        rssi >= -67 -> "gut"
        rssi >= -78 -> "mittel"
        else -> "schwach"
    }

    /** One-tap button label for the current quick-connect stage. */
    fun quickTitle(quick: QuickState): String = when (quick.stage) {
        QuickStage.IDLE -> quick.target?.let { "Mit $it verbinden" } ?: "Ein-Klick-Verbindung"
        QuickStage.SCANNING -> "Suche nach Netzwerken …"
        QuickStage.ASK_PASSWORD -> "Passwort für ${quick.target ?: "Netzwerk"} eingeben"
        QuickStage.SENDING -> "Zugangsdaten an Orb senden …"
        QuickStage.VERIFYING -> "Prüfe Verbindung am Orb …"
        QuickStage.CONNECTED -> "Verbunden mit ${quick.target ?: "Netzwerk"}"
        QuickStage.FAILED -> "Verbindung fehlgeschlagen"
    }

    /** Honest explanation for a failed or pending step; null when nothing needs saying. */
    fun quickDetail(quick: QuickState): String? = when (quick.detail) {
        QuickConnect.DETAIL_ORB_NOT_READY ->
            "Erst Orb koppeln und den Eigentümernachweis bestätigen — ohne Verbindung wird nichts gesendet."
        QuickConnect.DETAIL_NO_SECURE_NETWORK ->
            "Es sind nur offene Netze in Reichweite. Der Orb kann nur Netze mit Passwort speichern."
        QuickConnect.DETAIL_NO_NETWORKS ->
            "Der Orb hat kein Netzwerk gemeldet. Orb näher an den Router bringen und erneut versuchen."
        QuickConnect.DETAIL_SAVE_REJECTED ->
            "Der Orb hat die Zugangsdaten abgelehnt. Passwort und SSID prüfen."
        QuickConnect.DETAIL_VERIFY_TIMEOUT ->
            "Der Orb meldet keine Verbindung. Passwort prüfen — das gemerkte Passwort wurde verworfen."
        else -> null
    }
}
