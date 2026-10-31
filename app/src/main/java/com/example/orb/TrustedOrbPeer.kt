package com.example.orb

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Single bonded owner plus independent app proof; old address-only cache is never migrated. */
class TrustedOrbPeer(context: Context) : OrbTrustStore {
    private val appContext = context.applicationContext
    private fun canInspectBond(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
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
    @SuppressLint("MissingPermission") // canInspectBond checks the runtime grant; SecurityException is caught.
    override fun matches(peer: BluetoothDevice): Boolean = runCatching {
        canInspectBond() && address != null && address == peer.address && peer.bondState == BluetoothDevice.BOND_BONDED
    }.getOrDefault(false)
    @SuppressLint("MissingPermission") // Same runtime grant and fail-closed SecurityException handling.
    override fun enroll(peer: BluetoothDevice, proofKey: ByteArray): Boolean {
        if (proofKey.size != 32 || !canInspectBond() || !runCatching { peer.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)) return false
        val existing = address
        if (existing != null && existing != peer.address) return false
        return runCatching { preferences?.edit()?.putString("bonded_identity", peer.address)
            ?.putString("proof_key", OrbProof.hex(proofKey))?.commit() == true }.getOrDefault(false)
    }
    // No silent replacement, bond removal, or migration of address-only v1 cache.
}
