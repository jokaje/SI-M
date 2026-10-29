package com.example.data

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.model.AvatarFavorite
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID
import java.util.zip.CRC32

@RunWith(RobolectricTestRunner::class)
class AvatarFavoritesTest {
    private val dna = ByteArray(48).apply {
        "AVD1".toByteArray().copyInto(this); this[4] = 1
        val bytes = this
        val crc = CRC32().apply { update(bytes, 0, 44) }.value
        for (i in 0..3) this[44+i] = (crc ushr (i*8)).toByte()
    }.joinToString("") { "%02x".format(it) }

    @Test fun `named favorite survives preferences reload without changing prior avatar profile`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("si_m_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val prefs = AppPrefs(context)
        val prior = com.example.model.CreatureDna.defaultCreature()
        prefs.saveAvatar(prior)
        val entry = AvatarFavorite(UUID.randomUUID().toString(), "Mein Wesen", dna)
        prefs.saveFavorites(listOf(entry))
        assertEquals(listOf(entry), AppPrefs(context).loadFavorites())
        assertEquals(prior.profile.personalityProfile, AppPrefs(context).loadAvatar()?.profile?.personalityProfile)
    }
    @Test fun `library is bounded and malformed DNA cannot persist`() {
        val prefs = AppPrefs(ApplicationProvider.getApplicationContext())
        assertThrows(IllegalArgumentException::class.java) {
            prefs.saveFavorites((1..33).map { AvatarFavorite(UUID.randomUUID().toString(), "Wesen", dna) })
        }
        assertThrows(IllegalArgumentException::class.java) {
            prefs.saveFavorites(listOf(AvatarFavorite(UUID.randomUUID().toString(), "Wesen", "00")))
        }
    }
}
