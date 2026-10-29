package com.example.orb

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** App-visible mutual proof. Never log or stringify key/nonces. */
object OrbProof {
    fun mac(key: ByteArray, data: ByteArray): ByteArray {
        require(key.size == 32)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
    fun role(key: ByteArray, role: String, app: ByteArray, device: ByteArray): ByteArray {
        require(app.size == 32 && device.size == 32 && role in setOf("KI-M-ORB-PROOF-V1", "KI-M-PHONE-PROOF-V1"))
        return mac(key, role.toByteArray(Charsets.US_ASCII) + byteArrayOf(0) + app + device)
    }
    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    fun unhex(value: String): ByteArray {
        require(value.length % 2 == 0 && value.matches(Regex("[0-9a-f]+")))
        return ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
    fun statusMessage(app: ByteArray, device: ByteArray, status: CompanionStatus): ByteArray {
        require(app.size == 32 && device.size == 32 && status.seq in 1..Int.MAX_VALUE)
        return ("KI-M-STATUS-V1\n${hex(app)}\n${hex(device)}\n${status.seq}\n${status.characterId}\n${status.mode}\n" +
            "${if (status.setupOpen) 1 else 0}\n${if (status.owned) 1 else 0}\n").toByteArray(Charsets.US_ASCII)
    }
    fun verifyStatus(key: ByteArray, app: ByteArray, device: ByteArray, status: CompanionStatus): Boolean =
        status.proof.length == 64 && status.proof.matches(Regex("[0-9a-f]{64}")) &&
            MessageDigest.isEqual(mac(key, statusMessage(app, device, status)), unhex(status.proof))
}

/** Per-connection proof and monotonic authenticated status admission. */
class CompanionProofSession(private val key: ByteArray, val appNonce: ByteArray, val deviceNonce: ByteArray) {
    private var latest: CompanionStatus? = null
    init { require(key.size == 32 && appNonce.size == 32 && deviceNonce.size == 32) }
    fun accept(status: CompanionStatus): Boolean {
        require(status.owned && status.seq > 0 && OrbProof.verifyStatus(key, appNonce, deviceNonce, status))
        val prior = latest
        require(prior == null || status.seq >= prior.seq)
        require(prior == null || status.seq != prior.seq || status == prior)
        val changed = prior == null || status.seq > prior.seq
        latest = status
        return changed
    }
    fun clear() { key.fill(0); appNonce.fill(0); deviceNonce.fill(0); latest = null }
}
