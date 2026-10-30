package com.example.orb

/**
 * Local WLAN facts for the one-tap connection. Everything here is pure logic so it can be
 * unit-tested without Android: the provisioning protocol only accepts 8–63 ASCII characters
 * for a password and at most 32 for an SSID.
 */
object WifiPasswordPolicy {
    const val MIN_PASSWORD = 8
    const val MAX_PASSWORD = 63
    const val MAX_SSID = 32

    fun validPassword(value: String): Boolean =
        value.length in MIN_PASSWORD..MAX_PASSWORD && value.all { it.code in 32..126 }

    fun validSsid(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_SSID && value.all { it.code in 32..126 }

    /** Keys are stored verbatim; trimming protects against copy/paste whitespace. */
    fun normalizeSsid(value: String): String? = value.trim().takeIf { validSsid(it) }
}

/** What a single tap on "Verbinden" should do next. */
sealed interface QuickPlan {
    /** Password already known locally: send it without asking. */
    data class Connect(val ssid: String, val password: String) : QuickPlan

    /** Strongest reachable network, but the password still has to be typed once. */
    data class AskPassword(val ssid: String) : QuickPlan

    /** The orb already reports this network as connected; nothing to send. */
    data class Already(val ssid: String) : QuickPlan

    /** No usable access point list yet: scan first, then decide again. */
    data object Scan : QuickPlan

    /** Access points were seen, but only open ones: the protocol cannot provision those. */
    data object NoSecureNetwork : QuickPlan

    /** No authorised orb session, so no command can be sent. */
    data object Unavailable : QuickPlan
}

enum class QuickStage { IDLE, SCANNING, ASK_PASSWORD, SENDING, VERIFYING, CONNECTED, FAILED }

/** Detail codes stay machine-readable; the UI maps them to German text. */
data class QuickState(
    val stage: QuickStage = QuickStage.IDLE,
    val target: String? = null,
    val detail: String? = null
) {
    val active: Boolean get() = stage == QuickStage.SCANNING || stage == QuickStage.SENDING || stage == QuickStage.VERIFYING
}

object QuickConnect {
    val idle = QuickState()

    val DETAIL_ORB_NOT_READY = "orb_not_ready"
    val DETAIL_NO_NETWORKS = "no_networks"
    val DETAIL_NO_SECURE_NETWORK = "no_secure_network"
    val DETAIL_SAVE_REJECTED = "save_rejected"
    val DETAIL_VERIFY_TIMEOUT = "verify_timeout"

    /**
     * Strongest first. Signal strength is the only ordering the orb reports, so ties fall back to
     * the SSID to keep the choice deterministic between two runs with the same scan.
     */
    fun candidates(aps: List<AccessPoint>): List<AccessPoint> =
        aps.filter { it.secure }
            .sortedWith(compareByDescending<AccessPoint> { it.rssi }.thenBy { it.ssid })

    fun plan(
        aps: List<AccessPoint>,
        knownPasswords: Map<String, String>,
        orbReady: Boolean,
        orbConnected: Boolean,
        currentNetwork: String
    ): QuickPlan {
        if (!orbReady) return QuickPlan.Unavailable
        if (aps.isEmpty()) return QuickPlan.Scan
        val usable = candidates(aps)
        if (usable.isEmpty()) return QuickPlan.NoSecureNetwork

        val current = currentNetwork.trim()
        if (orbConnected && current.isNotEmpty() && usable.any { it.ssid == current }) {
            return QuickPlan.Already(current)
        }

        val remembered = usable.firstOrNull { ap ->
            knownPasswords[ap.ssid]?.let(WifiPasswordPolicy::validPassword) == true
        }
        if (remembered != null) return QuickPlan.Connect(remembered.ssid, knownPasswords.getValue(remembered.ssid))

        return QuickPlan.AskPassword(usable.first().ssid)
    }

    fun forPlan(plan: QuickPlan): QuickState = when (plan) {
        is QuickPlan.Connect -> QuickState(QuickStage.SENDING, plan.ssid)
        is QuickPlan.AskPassword -> QuickState(QuickStage.ASK_PASSWORD, plan.ssid)
        is QuickPlan.Already -> QuickState(QuickStage.CONNECTED, plan.ssid)
        QuickPlan.Scan -> QuickState(QuickStage.SCANNING)
        QuickPlan.NoSecureNetwork -> QuickState(QuickStage.FAILED, detail = DETAIL_NO_SECURE_NETWORK)
        QuickPlan.Unavailable -> QuickState(QuickStage.FAILED, detail = DETAIL_ORB_NOT_READY)
    }

    /** Password supplied for a pending [QuickStage.ASK_PASSWORD] step. */
    fun passwordReady(state: QuickState, password: String): QuickState =
        if (state.stage != QuickStage.ASK_PASSWORD || !WifiPasswordPolicy.validPassword(password)) state
        else QuickState(QuickStage.SENDING, state.target)

    /** Result of the provisioning write; only a confirmed orb answer starts verification. */
    fun afterSave(state: QuickState, confirmed: Boolean): QuickState = when {
        state.stage != QuickStage.SENDING -> state
        confirmed -> state.copy(stage = QuickStage.VERIFYING)
        else -> QuickState(QuickStage.FAILED, state.target, DETAIL_SAVE_REJECTED)
    }

    /**
     * The orb's own "networks" reply is the only proof that the connection works. Anything else
     * (including a long blank answer) keeps the flow in verification until the timeout hits.
     */
    fun afterVerify(state: QuickState, wifiKnown: Boolean, connected: Boolean, current: String): QuickState {
        if (state.stage != QuickStage.VERIFYING) return state
        val currentName = current.trim()
        val matches = state.target == null || currentName.isEmpty() || currentName == state.target
        return if (wifiKnown && connected && matches) state.copy(stage = QuickStage.CONNECTED) else state
    }

    fun timeout(state: QuickState): QuickState = when (state.stage) {
        QuickStage.SENDING, QuickStage.VERIFYING ->
            QuickState(QuickStage.FAILED, state.target, DETAIL_VERIFY_TIMEOUT)
        else -> state
    }

    fun cancel(): QuickState = idle
}
