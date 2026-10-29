package com.example.data

import android.content.Context
import com.example.model.CharacterProfile
import com.example.model.CreatureDna
import com.example.model.CreatureEmotion
import com.example.model.AvatarDraft
import com.example.model.AvatarFavorite
import com.example.orb.AvatarDnaWire
import org.json.JSONArray
import org.json.JSONObject

/** Chosen appearance; SYSTEM follows the Android day/night setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK;
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

/** Non-secret local preferences: onboarding state and the app-side avatar. */
class AppPrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("si_m_prefs", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", null) ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
        set(value) { prefs.edit().putString("theme_mode", value.name).apply() }

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean("onboarding_completed", false)
        set(value) { prefs.edit().putBoolean("onboarding_completed", value).apply() }

    var onboardingStep: Int
        get() = prefs.getInt("onboarding_step", 1).coerceIn(1, 4)
        set(value) { prefs.edit().putInt("onboarding_step", value.coerceIn(1, 4)).apply() }

    var reminderShown: Boolean
        get() = prefs.getBoolean("reminder_shown", false)
        set(value) { prefs.edit().putBoolean("reminder_shown", value).apply() }

    fun saveAvatar(dna: CreatureDna) {
        prefs.edit().putString("avatar", encode(dna)).apply()
    }

    fun loadAvatar(): CreatureDna? {
        val raw = prefs.getString("avatar", null) ?: return null
        return runCatching { decode(raw) }.getOrNull()
    }

    fun clearAvatar() {
        prefs.edit().remove("avatar").apply()
    }

    /** Migrates once without deleting the old profile/voice/personality JSON. */
    fun loadFavorites(): List<AvatarFavorite> {
        val raw = prefs.getString("avatar_favorites_v1", null)
        if (raw == null) {
            val legacy = loadAvatar() ?: return emptyList()
            val migrated = runCatching { AvatarDraft.favorite(legacy.name, AvatarDraft.migrate(legacy)) }.getOrNull()
                ?: return emptyList()
            saveFavorites(listOf(migrated))
            return listOf(migrated)
        }
        return runCatching {
            val arr = JSONArray(raw)
            require(arr.length() <= 32)
            (0 until arr.length()).map { i ->
                val item = arr.getJSONObject(i)
                val id = item.getString("id")
                require(java.util.UUID.fromString(id).toString() == id)
                val name = item.getString("name")
                require(name.isNotBlank() && name.length <= 32)
                val dna = item.getString("dna")
                AvatarDnaWire.decode(dna)
                AvatarFavorite(id, name, dna)
            }.also { require(it.map(AvatarFavorite::id).distinct().size == it.size) }
        }.getOrDefault(emptyList())
    }

    fun saveFavorites(entries: List<AvatarFavorite>) {
        require(entries.size <= 32 && entries.map(AvatarFavorite::id).distinct().size == entries.size)
        val arr = JSONArray()
        entries.forEach { entry ->
            require(entry.name.isNotBlank() && entry.name.length <= 32)
            AvatarDnaWire.decode(entry.dna)
            arr.put(JSONObject().put("id", entry.id).put("name", entry.name).put("dna", entry.dna))
        }
        check(prefs.edit().putString("avatar_favorites_v1", arr.toString()).commit())
    }

    private fun encode(dna: CreatureDna): String = JSONObject().apply {
        put("id", dna.id)
        put("seed", dna.seed)
        put("name", dna.name)
        put("ears", dna.ears)
        put("crown", dna.crown)
        put("wings", dna.wings)
        put("tail", dna.tail)
        put("belly", dna.belly)
        put("eyes", dna.eyes)
        put("mouth", dna.mouth)
        put("particle", dna.particle)
        put("bodyHex", dna.bodyHex)
        put("skinHex", dna.skinHex)
        put("cyanHex", dna.cyanHex)
        put("irisHex", dna.irisHex)
        put("blushHex", dna.blushHex)
        put("bodyRx", dna.bodyRx.toDouble())
        put("bodyRy", dna.bodyRy.toDouble())
        put("headWiden", dna.headWiden.toDouble())
        put("neckPinch", dna.neckPinch.toDouble())
        put("bellyBulge", dna.bellyBulge.toDouble())
        put("emotion", dna.emotion.name)
        put("personality", dna.profile.personalityProfile)
        put("speech", dna.profile.speechStyle)
        put("depth", dna.profile.cognitiveDepth)
        put("curiosity", dna.profile.curiosityIndex)
        put("warmth", dna.profile.warmthFactor.toDouble())
        put("directive", dna.profile.internalSystemDirective)
    }.toString()

    private fun decode(raw: String): CreatureDna {
        val o = JSONObject(raw)
        return CreatureDna(
            id = o.getString("id"),
            seed = o.getInt("seed"),
            name = o.getString("name"),
            ears = o.getInt("ears"),
            crown = o.getInt("crown"),
            wings = o.getInt("wings"),
            tail = o.getInt("tail"),
            belly = o.getInt("belly"),
            eyes = o.getInt("eyes"),
            mouth = o.getInt("mouth"),
            particle = o.getInt("particle"),
            bodyHex = o.getString("bodyHex"),
            skinHex = o.getString("skinHex"),
            cyanHex = o.getString("cyanHex"),
            irisHex = o.getString("irisHex"),
            blushHex = o.getString("blushHex"),
            bodyRx = o.getDouble("bodyRx").toFloat(),
            bodyRy = o.getDouble("bodyRy").toFloat(),
            headWiden = o.getDouble("headWiden").toFloat(),
            neckPinch = o.getDouble("neckPinch").toFloat(),
            bellyBulge = o.getDouble("bellyBulge").toFloat(),
            emotion = runCatching { CreatureEmotion.valueOf(o.getString("emotion")) }
                .getOrDefault(CreatureEmotion.IDLE),
            profile = CharacterProfile(
                personalityProfile = o.getString("personality"),
                speechStyle = o.getString("speech"),
                cognitiveDepth = o.getString("depth"),
                curiosityIndex = o.getInt("curiosity"),
                warmthFactor = o.getDouble("warmth").toFloat(),
                internalSystemDirective = o.getString("directive")
            )
        )
    }
}
