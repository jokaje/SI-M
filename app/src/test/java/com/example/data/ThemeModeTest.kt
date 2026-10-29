package com.example.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class ThemeModeTest {

    @Test
    fun `system mode follows the android setting`() {
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
    }

    @Test
    fun `explicit modes ignore the android setting`() {
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
    }

    @Test
    fun `preference defaults to system and survives a round trip`() {
        val prefs = AppPrefs(ApplicationProvider.getApplicationContext())
        assertEquals(ThemeMode.SYSTEM, prefs.themeMode)

        prefs.themeMode = ThemeMode.DARK
        assertEquals(ThemeMode.DARK, prefs.themeMode)
        // a second instance reads the stored value, not an in-memory default
        assertEquals(ThemeMode.DARK, AppPrefs(ApplicationProvider.getApplicationContext()).themeMode)

        prefs.themeMode = ThemeMode.LIGHT
        assertEquals(ThemeMode.LIGHT, prefs.themeMode)

        prefs.themeMode = ThemeMode.SYSTEM
        assertEquals(ThemeMode.SYSTEM, prefs.themeMode)
    }

    @Test
    fun `unknown stored value falls back to system`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("si_m_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putString("theme_mode", "SEPIA").commit()
        assertEquals(ThemeMode.SYSTEM, AppPrefs(context).themeMode)
    }
}
