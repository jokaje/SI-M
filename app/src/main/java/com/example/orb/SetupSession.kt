package com.example.orb

/** Local session fence. Android does not expose the peer's actual SC/MITM/key-size details. */
class SetupSession {
    companion object {
        // Android BluetoothDevice.PAIRING_VARIANT_PIN: AOSP BLE passkey request invokes
        // pin_request_cb(..., false), then BondStateMachine emits PIN (0). Input, not Just Works.
        const val PIN_INPUT = 0
        // BluetoothDevice.PAIRING_VARIANT_PASSKEY: another system passkey-entry path.
        const val PASSKEY_INPUT = 1
        const val CONSENT = 3
    }
    var generation = 0L; private set
    var inputRevision = 0L; private set
    var passkeyObserved = false; private set
    var consentPending = false; private set
    var protectedReadSucceeded = false; private set
    var physicalCodeConfirmed = false; private set
    var pendingId: Int? = null; private set
    private var trustedWindow = false
    private var nextId = 1
    val authorized get() = trustedWindow || (passkeyObserved && protectedReadSucceeded && physicalCodeConfirmed)
    fun accepts(epoch: Long) = epoch == generation
    fun invalidate() { generation++; inputRevision++; passkeyObserved = false; consentPending = false; protectedReadSucceeded = false; physicalCodeConfirmed = false; trustedWindow = false; pendingId = null }
    fun setTrustedWindow(open: Boolean) { trustedWindow = open; if (!open) pendingId = null }
    fun pairing(epoch: Long, variant: Int): Boolean {
        if (!accepts(epoch)) return false
        inputRevision++
        protectedReadSucceeded = false; physicalCodeConfirmed = false
        if (variant == CONSENT) { // AOSP BLE security-request consent precedes (but does not prove) code entry.
            passkeyObserved = false; consentPending = true; return true
        }
        consentPending = false
        if (variant != PIN_INPUT && variant != PASSKEY_INPUT) { passkeyObserved = false; return false }
        passkeyObserved = true
        return true
    }
    fun readProtected(epoch: Long) { if (accepts(epoch)) protectedReadSucceeded = true }
    fun confirmPhysicalCode(epoch: Long) { if (accepts(epoch)) physicalCodeConfirmed = true }
    fun begin(): Int {
        check(authorized && pendingId == null) { "Frische Passkey-Kopplung und physische Codebestätigung erforderlich." }
        check(nextId > 0) { "Anfrage-IDs dieser Sitzung erschöpft. Neu koppeln." }
        val id = nextId
        nextId = if (nextId == Int.MAX_VALUE) 0 else nextId + 1
        pendingId = id
        return id
    }
    fun acceptsReply(epoch: Long, reply: SetupReply): Boolean = accepts(epoch) && reply.id == pendingId
    fun finish(epoch: Long, id: Int) { if (accepts(epoch) && pendingId == id) pendingId = null }
}
