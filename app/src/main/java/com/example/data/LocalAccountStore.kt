package com.example.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Local, device-bound profile. No server, no account in the cloud.
 * The passcode is stored as a PBKDF2-HMAC-SHA256 hash inside
 * Android-Keystore-backed encrypted preferences.
 */
class LocalAccountStore(context: Context) {

    private val appContext = context.applicationContext
    private val preferences = runCatching {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "si_m_local_account",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull()

    val available: Boolean get() = preferences != null

    fun hasAccount(): Boolean = preferences?.getString(KEY_HASH, null) != null

    fun email(): String = preferences?.getString(KEY_EMAIL, "") ?: ""

    fun displayName(): String = preferences?.getString(KEY_NAME, "") ?: ""

    /** First start: creates the only local profile. Returns false if one already exists. */
    fun register(email: String, name: String, passcode: String): Boolean {
        val store = preferences ?: return false
        if (hasAccount()) return false
        val normalizedMail = email.trim().lowercase()
        val normalizedName = name.trim()
        if (!validEmail(normalizedMail) || normalizedName.isEmpty() || passcode.length < MIN_PASSCODE) return false

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(passcode, salt)
        return try {
            store.edit()
                .putString(KEY_EMAIL, normalizedMail)
                .putString(KEY_NAME, normalizedName)
                .putString(KEY_SALT, Base64.getEncoder().encodeToString(salt))
                .putString(KEY_HASH, Base64.getEncoder().encodeToString(hash))
                .commit()
        } finally {
            hash.fill(0)
            salt.fill(0)
        }
    }

    fun verify(email: String, passcode: String): Boolean {
        val store = preferences ?: return false
        val storedHash = store.getString(KEY_HASH, null) ?: return false
        val storedSalt = store.getString(KEY_SALT, null) ?: return false
        val storedEmail = store.getString(KEY_EMAIL, "") ?: ""
        if (storedEmail != email.trim().lowercase()) return false

        val salt = runCatching { Base64.getDecoder().decode(storedSalt) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(storedHash) }.getOrNull() ?: return false
        val candidate = derive(passcode, salt)
        return try {
            MessageDigest.isEqual(expected, candidate)
        } finally {
            candidate.fill(0)
            expected.fill(0)
            salt.fill(0)
        }
    }

    fun validEmail(value: String): Boolean =
        value.length in 3..254 &&
            value.count { it == '@' } == 1 &&
            value.substringBefore('@').isNotBlank() &&
            value.substringAfter('@').contains('.') &&
            value.none { it.isWhitespace() }

    private fun derive(passcode: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passcode.toCharArray(), salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val MIN_PASSCODE = 6
        private const val ITERATIONS = 210_000
        private const val KEY_EMAIL = "email"
        private const val KEY_NAME = "name"
        private const val KEY_SALT = "salt"
        private const val KEY_HASH = "hash"
    }
}
