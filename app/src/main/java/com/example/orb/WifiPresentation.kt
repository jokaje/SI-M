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
}
