package com.example.orb

import android.bluetooth.BluetoothDevice

/** Storage boundary; production uses Android Keystore-backed TrustedOrbPeer. */
interface OrbTrustStore {
    val address: String?
    fun key(): ByteArray?
    fun matches(peer: BluetoothDevice): Boolean
    fun enroll(peer: BluetoothDevice, proofKey: ByteArray): Boolean
}
