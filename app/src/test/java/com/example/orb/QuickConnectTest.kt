package com.example.orb

import org.junit.Assert.*
import org.junit.Test

class QuickConnectTest {

    private fun ap(ssid: String, rssi: Int, secure: Boolean = true, saved: Boolean = false) =
        AccessPoint(ssid, rssi, saved, secure)

    // ------------------------------------------------------------------ policy

    @Test fun `password policy mirrors the provisioning protocol`() {
        assertFalse(WifiPasswordPolicy.validPassword("1234567"))
        assertTrue(WifiPasswordPolicy.validPassword("12345678"))
        assertTrue(WifiPasswordPolicy.validPassword("x".repeat(63)))
        assertFalse(WifiPasswordPolicy.validPassword("x".repeat(64)))
        assertFalse(WifiPasswordPolicy.validPassword("pass\u0000word"))
    }

    @Test fun `ssid policy rejects empty and overlong names`() {
        assertFalse(WifiPasswordPolicy.validSsid(""))
        assertFalse(WifiPasswordPolicy.validSsid("   "))
        assertFalse(WifiPasswordPolicy.validSsid("s".repeat(33)))
        assertTrue(WifiPasswordPolicy.validSsid("s".repeat(32)))
        assertEquals("home", WifiPasswordPolicy.normalizeSsid("  home  "))
        assertNull(WifiPasswordPolicy.normalizeSsid("  "))
    }

    // -------------------------------------------------------------------- plan

    @Test fun `no access points yet means scan first`() {
        assertEquals(QuickPlan.Scan, QuickConnect.plan(emptyList(), emptyMap(), orbReady = true, orbConnected = false, currentNetwork = ""))
    }

    @Test fun `unauthorised orb never produces a connect plan`() {
        val plan = QuickConnect.plan(
            listOf(ap("home", -50)),
            mapOf("home" to "secret-pass"),
            orbReady = false,
            orbConnected = true,
            currentNetwork = "home"
        )
        assertEquals(QuickPlan.Unavailable, plan)
        assertEquals(QuickStage.FAILED, QuickConnect.forPlan(plan).stage)
        assertEquals(QuickConnect.DETAIL_ORB_NOT_READY, QuickConnect.forPlan(plan).detail)
    }

    @Test fun `open networks alone cannot be provisioned`() {
        val plan = QuickConnect.plan(listOf(ap("cafe", -40, secure = false)), emptyMap(), true, false, "")
        assertEquals(QuickPlan.NoSecureNetwork, plan)
    }

    @Test fun `strongest network with a known password is used without asking`() {
        val aps = listOf(ap("weak", -85), ap("strong", -48), ap("middle", -60))
        val known = mapOf("weak" to "aaaaaaaa", "middle" to "bbbbbbbb")
        val plan = QuickConnect.plan(aps, known, orbReady = true, orbConnected = false, currentNetwork = "")
        assertEquals(QuickPlan.Connect("middle", "bbbbbbbb"), plan)
    }

    @Test fun `strongest reachable network asks for its password once`() {
        val aps = listOf(ap("home", -70), ap("office", -52))
        val plan = QuickConnect.plan(aps, emptyMap(), orbReady = true, orbConnected = false, currentNetwork = "")
        assertEquals(QuickPlan.AskPassword("office"), plan)
        assertEquals(QuickStage.ASK_PASSWORD, QuickConnect.forPlan(plan).stage)
        assertEquals("office", QuickConnect.forPlan(plan).target)
    }

    @Test fun `an invalid remembered password is treated as unknown`() {
        val aps = listOf(ap("home", -55))
        val plan = QuickConnect.plan(aps, mapOf("home" to "short"), orbReady = true, orbConnected = false, currentNetwork = "")
        assertEquals(QuickPlan.AskPassword("home"), plan)
    }

    @Test fun `already connected network needs no write`() {
        val aps = listOf(ap("home", -50), ap("other", -60))
        val plan = QuickConnect.plan(aps, mapOf("home" to "secret-pass"), orbReady = true, orbConnected = true, currentNetwork = "home")
        assertEquals(QuickPlan.Already("home"), plan)
        assertEquals(QuickStage.CONNECTED, QuickConnect.forPlan(plan).stage)
    }

    @Test fun `disconnected orb still reconnects to the known network`() {
        val aps = listOf(ap("home", -50))
        val plan = QuickConnect.plan(aps, mapOf("home" to "secret-pass"), orbReady = true, orbConnected = false, currentNetwork = "")
        assertEquals(QuickPlan.Connect("home", "secret-pass"), plan)
    }

    @Test fun `equal signal strength resolves deterministically by ssid`() {
        val aps = listOf(ap("zeta", -60), ap("alpha", -60))
        val first = QuickConnect.plan(aps, emptyMap(), true, false, "")
        val second = QuickConnect.plan(aps.reversed(), emptyMap(), true, false, "")
        assertEquals(QuickPlan.AskPassword("alpha"), first)
        assertEquals(first, second)
    }

    // ------------------------------------------------------------------- flow

    @Test fun `sending needs a confirmed orb answer before verification`() {
        val sending = QuickConnect.forPlan(QuickPlan.Connect("home", "secret-pass"))
        assertEquals(QuickStage.SENDING, sending.stage)
        val rejected = QuickConnect.afterSave(sending, confirmed = false)
        assertEquals(QuickStage.FAILED, rejected.stage)
        assertEquals(QuickConnect.DETAIL_SAVE_REJECTED, rejected.detail)
        assertEquals(QuickStage.VERIFYING, QuickConnect.afterSave(sending, confirmed = true).stage)
    }

    @Test fun `connected status without matching network is not proof`() {
        val verifying = QuickState(QuickStage.VERIFYING, "home")
        assertEquals(QuickStage.VERIFYING, QuickConnect.afterVerify(verifying, true, true, "").stage)
        assertEquals(QuickStage.VERIFYING, QuickConnect.afterVerify(verifying, true, true, "other").stage)
        assertEquals(QuickStage.VERIFYING, QuickConnect.afterVerify(QuickState(QuickStage.VERIFYING), true, true, "home").stage)
        assertEquals(QuickStage.CONNECTED, QuickConnect.afterVerify(verifying, true, true, "home").stage)
    }

    @Test fun `verification only succeeds on a confirmed orb reading`() {
        val verifying = QuickState(QuickStage.VERIFYING, "home")
        assertEquals(QuickStage.VERIFYING, QuickConnect.afterVerify(verifying, wifiKnown = false, connected = false, current = "").stage)
        assertEquals(QuickStage.VERIFYING, QuickConnect.afterVerify(verifying, wifiKnown = true, connected = false, current = "").stage)
        assertEquals(QuickStage.CONNECTED, QuickConnect.afterVerify(verifying, wifiKnown = true, connected = true, current = "home").stage)
    }

    @Test fun `connected to a different network is not our success`() {
        val verifying = QuickState(QuickStage.VERIFYING, "home")
        val other = QuickConnect.afterVerify(verifying, wifiKnown = true, connected = true, current = "neighbour")
        assertEquals(QuickStage.VERIFYING, other.stage)
    }

    @Test fun `timeout only fails an in-flight write`() {
        assertEquals(QuickStage.FAILED, QuickConnect.timeout(QuickState(QuickStage.VERIFYING, "home")).stage)
        assertEquals(QuickConnect.DETAIL_VERIFY_TIMEOUT, QuickConnect.timeout(QuickState(QuickStage.SENDING, "home")).detail)
        val idle = QuickState(QuickStage.IDLE)
        assertEquals(idle, QuickConnect.timeout(idle))
        assertEquals(QuickStage.CONNECTED, QuickConnect.timeout(QuickState(QuickStage.CONNECTED, "home")).stage)
    }

    @Test fun `password step rejects an invalid value and keeps the stage`() {
        val asking = QuickConnect.forPlan(QuickPlan.AskPassword("home"))
        assertEquals(asking, QuickConnect.passwordReady(asking, "short"))
        val ready = QuickConnect.passwordReady(asking, "secret-pass")
        assertEquals(QuickStage.SENDING, ready.stage)
        assertEquals("home", ready.target)
    }

    @Test fun `cancel returns to idle`() {
        assertEquals(QuickState(), QuickConnect.cancel())
        assertFalse(QuickState().active)
        assertTrue(QuickState(QuickStage.VERIFYING).active)
    }
}
