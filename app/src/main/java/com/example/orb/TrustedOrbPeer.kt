package com.example.orb

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Single bonded owner plus independent app proof; old address-only cache is never migrated. */
class TrustedOrbPeer(context: Context) : OrbTrustStore {
    private val preferences = runCatching {
        val app = context.applicationContext
        EncryptedSharedPreferences.create(app, "trusted_orb_proof_v2",
            MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }.getOrNull()
    override val address: String? get() = runCatching {
        val prefs = preferences ?: return null
        val address = prefs.getString("bonded_identity", null)
        val key = prefs.getString("proof_key", null)
        if (address != null && key != null && key.matches(Regex("[0-9a-f]{64}"))) address else null
    }.getOrNull()
    override fun key(): ByteArray? = runCatching {
        val encoded = preferences?.getString("proof_key", null) ?: return null
        OrbProof.unhex(encoded).takeIf { it.size == 32 }
    }.getOrNull()
    override fun matches(peer: BluetoothDevice): Boolean = address != null &&
        address == peer.address && peer.bondState == BluetoothDevice.BOND_BONDED
    override fun enroll(peer: BluetoothDevice, proofKey: ByteArray): Boolean {
        if (proofKey.size != 32 || peer.bondState != BluetoothDevice.BOND_BONDED) return false
        val existing = address
        if (existing != null && existing != peer.address) return false
        return runCatching { preferences?.edit()?.putString("bonded_identity", peer.address)
            ?.putString("proof_key", OrbProof.hex(proofKey))?.commit() == true }.getOrDefault(false)
    }
    // No silent replacement, bond removal, or migration of address-only v1 cache.
}
