package com.example.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiLanguageTest {
    @Test fun `all major surfaces have English copy and German remains unchanged`() {
        val labels = listOf("Einrichtung", "Figuren", "Netzwerk", "Sprachdienst", "Darstellung",
            "Orb-Synchronisierung ausstehend", "Bluetooth-Berechtigung erteilen", "App-Update",
            "WLAN auf dem Orb", "Kamera wechseln", "Profil entsperren")
        labels.forEach { assertEquals(it, translateUi(it, "de")); assert(translateUi(it, "en") != it) { "Untranslated: $it" } }
    }

    @Test fun `dynamic labels preserve device identifiers and unknown agent text`() {
        assertEquals("Step 2 of 4", translateTemplate("step", "en", 2))
        assertEquals("Connect to Café-42", translateTemplate("connect", "en", "Café-42"))
        assertEquals("Known Orb: 01:02. A verified connection is read-only even when the setup window is closed. Open physical Orb setup to make changes.",
            translateTemplate("knownOrb", "en", "01:02"))
        assertEquals("Agent: Guten Morgen", translateUi("Agent: Guten Morgen", "en"))
        val adversarial = "Mit von / Figur: Passwort für · verbunden https://beispiel.de %s"
        assertEquals("Connect to $adversarial", translateTemplate("connect", "en", adversarial))
        assertEquals("Character: $adversarial", translateTemplate("character", "en", adversarial))
        assertEquals(adversarial, translateUi(adversarial, "en"))
        assertEquals("https://example.com/v1/turn-stream", translateTemplate("endpointExample", "en"))
        assertEquals("Current: $adversarial • connected", translateTemplate("currentNetwork", "en", adversarial, "connected"))
    }
}
