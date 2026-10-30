package com.example.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Local WLAN secrets so a repeat connection needs one tap instead of scan → pick → type.
 * Stored in Android-Keystore-backed encrypted preferences, never in plain preferences and
 * never sent anywhere except to the paired orb during provisioning.
 */
interface WifiSecrets {
    fun all(): Map<String, String>
    fun passwordFor(ssid: String): String?
    fun remember(ssid: String, password: String): Boolean
    fun forget(ssid: String)
    fun forgetAll()
}

class WifiCredentialStore(context: Context) : WifiSecrets {

    private val appContext = context.applicationContext
    private val preferences = runCatching {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "si_m_wifi_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull()

    val available: Boolean get() = preferences != null

    override fun all(): Map<String, String> {
        val raw = preferences?.all ?: return emptyMap()
        val out = LinkedHashMap<String, String>(raw.size)
        for ((key, value) in raw) {
            if (key != null && value is String && com.example.orb.WifiPasswordPolicy.validPassword(value)) {
                out[key] = value
            }
        }
        return out
    }

    override fun passwordFor(ssid: String): String? {
        val key = com.example.orb.WifiPasswordPolicy.normalizeSsid(ssid) ?: return null
        return preferences?.getString(key, null)
    }

    override fun remember(ssid: String, password: String): Boolean {
        val store = preferences ?: return false
        val key = com.example.orb.WifiPasswordPolicy.normalizeSsid(ssid) ?: return false
        if (!com.example.orb.WifiPasswordPolicy.validPassword(password)) return false
        return runCatching { store.edit().putString(key, password).commit() }.getOrDefault(false)
    }

    override fun forget(ssid: String) {
        val key = com.example.orb.WifiPasswordPolicy.normalizeSsid(ssid) ?: return
        runCatching { preferences?.edit()?.remove(key)?.apply() }
    }

    override fun forgetAll() {
        runCatching { preferences?.edit()?.clear()?.apply() }
    }
}
