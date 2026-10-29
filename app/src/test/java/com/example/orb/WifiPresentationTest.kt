package com.example.orb

import org.junit.Assert.*
import org.junit.Test

class WifiPresentationTest {
    @Test fun `unknown initial state never reports disconnected or unconfigured`() {
        val state = SetupState()
        assertEquals("Status unbekannt · erneut laden", WifiPresentation.connection(state))
        assertEquals("Status unbekannt · erneut laden", WifiPresentation.onboarding(state))
        assertEquals("Status unbekannt · erneut laden", WifiPresentation.summary(state))
        assertEquals("Gespeicherte Netzwerke: Status unbekannt · erneut laden", WifiPresentation.saved(state))
        assertEquals("Status unbekannt · erneut laden", WifiPresentation.current(state))
        assertEquals("Später einrichten", WifiPresentation.continueLabel(state))
    }
    @Test fun `fresh connected and disconnected readings retain truthful labels`() {
        val connected = SetupState(wifiStatusKnown = true, savedNetworksKnown = true,
            currentNetwork = "home", wifiConnected = true, savedNetworks = listOf("home"))
        assertEquals("verbunden", WifiPresentation.connection(connected))
        assertEquals("Aktuell: home • verbunden", WifiPresentation.onboarding(connected))
        assertEquals("home (verbunden)", WifiPresentation.summary(connected))
        assertEquals("Weiter zum Sprachdienst", WifiPresentation.continueLabel(connected))
        val disconnected = connected.copy(wifiConnected = false)
        assertEquals("nicht verbunden", WifiPresentation.connection(disconnected))
        assertEquals("home (nicht verbunden)", WifiPresentation.summary(disconnected))
    }
    @Test fun `only fresh empty saved list is unconfigured`() {
        val empty = SetupState(wifiStatusKnown = true, savedNetworksKnown = true)
        assertEquals("Noch kein Netzwerk gespeichert.", WifiPresentation.onboarding(empty))
        assertEquals("nicht eingerichtet", WifiPresentation.summary(empty))
        assertEquals("Keine Netzwerke gespeichert.", WifiPresentation.saved(empty))
        val savedButDisconnected = empty.copy(savedNetworks = listOf("home"))
        assertEquals("nicht verbunden", WifiPresentation.summary(savedButDisconnected))
        assertEquals("Aktuell: – • nicht verbunden", WifiPresentation.onboarding(savedButDisconnected))
    }
    @Test fun `saved list freshness and link freshness are independent`() {
        val state = SetupState(wifiStatusKnown = true, wifiConnected = true)
        assertEquals("verbunden", WifiPresentation.connection(state))
        assertEquals("Gespeicherte Netzwerke: Status unbekannt · erneut laden", WifiPresentation.saved(state))
        assertEquals("Status unbekannt · erneut laden", WifiPresentation.summary(state))
    }
}
