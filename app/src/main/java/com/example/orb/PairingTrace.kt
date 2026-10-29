package com.example.orb

/** Strict vocabulary: no peer address, passkey, Wi-Fi details or GATT payload can enter this trace. */
class PairingTrace(private val elapsed: () -> Long) {
    enum class Stage {
        TAP, PERMISSION_OK, PERMISSION_DENIED, CONNECT_JOB, GATT_START, GATT_CALLBACK,
        MTU_START, MTU_CALLBACK, DISCOVERY_START, DISCOVERY_CALLBACK, SERVICE_FOUND,
        BOND_START, STALE_BOND, PAIRING_VARIANT, CONSENT_PENDING, CONSENT_TIMEOUT,
        UNSUPPORTED_PAIRING, PROTECTED_READ_START, PROTECTED_READ_CALLBACK,
        CCCD_START, CCCD_CALLBACK, CONNECTED, LIFECYCLE_STOP, LIFECYCLE_RESUME,
        COMPANION_DISCOVERED, COMPANION_READ_START, COMPANION_READ_CALLBACK,
        COMPANION_READ, OWNER_ENROLL_WRITE, OWNER_VERIFIED,
        TRUSTED_RECONNECT, PROOF_CHALLENGE, PROOF_VERIFIED, STATUS_VERIFIED,
        WINDOW_CLOSED, TIMEOUT, FAILED, STOPPED
    }
    private val entries = ArrayDeque<String>()
    private var start = elapsed()
    fun reset() { entries.clear(); start = elapsed() }
    fun record(stage: Stage, code: Int? = null) {
        val value = code?.coerceIn(-1, 999) // only platform status/variant; never arbitrary text
        if (entries.size == 40) entries.removeFirst()
        entries.addLast("+${(elapsed() - start).coerceAtLeast(0)}ms ${stage.name}" + (value?.let { " code=$it" } ?: ""))
    }
    fun snapshot(): String = entries.joinToString("\n")

    companion object {
        /** Never continue an authorized session or an unobserved background connection. */
        fun mayContinueDuringSystemPairing(connecting: Boolean, passkeyObserved: Boolean, authorized: Boolean,
                                           consentPending: Boolean = false) =
            connecting && (passkeyObserved || consentPending) && !authorized
    }
}
