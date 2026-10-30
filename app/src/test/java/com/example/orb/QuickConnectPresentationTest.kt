package com.example.orb

import org.junit.Assert.*
import org.junit.Test

/** One-tap labels must state what actually happened, never a hopeful guess. */
class QuickConnectPresentationTest {

    @Test fun `idle button offers the action`() {
        assertEquals("Ein-Klick-Verbindung", WifiPresentation.quickTitle(QuickState()))
    }

    @Test fun `in-flight stages name the current step and the network`() {
        assertEquals("Suche nach Netzwerken …", WifiPresentation.quickTitle(QuickState(QuickStage.SCANNING)))
        assertEquals("Passwort für home eingeben", WifiPresentation.quickTitle(QuickState(QuickStage.ASK_PASSWORD, "home")))
        assertEquals("Zugangsdaten an Orb senden …", WifiPresentation.quickTitle(QuickState(QuickStage.SENDING, "home")))
        assertEquals("Prüfe Verbindung am Orb …", WifiPresentation.quickTitle(QuickState(QuickStage.VERIFYING, "home")))
        assertEquals("Verbunden mit home", WifiPresentation.quickTitle(QuickState(QuickStage.CONNECTED, "home")))
    }

    @Test fun `failure explains the cause and never claims success`() {
        for (detail in listOf(
            QuickConnect.DETAIL_ORB_NOT_READY,
            QuickConnect.DETAIL_NO_NETWORKS,
            QuickConnect.DETAIL_NO_SECURE_NETWORK,
            QuickConnect.DETAIL_SAVE_REJECTED,
            QuickConnect.DETAIL_VERIFY_TIMEOUT
        )) {
            val state = QuickState(QuickStage.FAILED, detail = detail)
            assertEquals("Verbindung fehlgeschlagen", WifiPresentation.quickTitle(state))
            val text = WifiPresentation.quickDetail(state)
            assertNotNull("detail text missing for $detail", text)
            assertTrue("detail text must not promise success: $text", !text!!.contains("Verbunden", true))
        }
    }

    @Test fun `no detail text without a reason`() {
        assertNull(WifiPresentation.quickDetail(QuickState()))
        assertNull(WifiPresentation.quickDetail(QuickState(QuickStage.VERIFYING, "home")))
    }

    @Test fun `strength labels follow the documented thresholds`() {
        assertEquals("sehr gut", WifiPresentation.strength(-40))
        assertEquals("sehr gut", WifiPresentation.strength(-55))
        assertEquals("gut", WifiPresentation.strength(-56))
        assertEquals("gut", WifiPresentation.strength(-67))
        assertEquals("mittel", WifiPresentation.strength(-68))
        assertEquals("mittel", WifiPresentation.strength(-78))
        assertEquals("schwach", WifiPresentation.strength(-79))
        assertEquals("schwach", WifiPresentation.strength(-95))
    }
}
