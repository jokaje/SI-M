package com.example.ui.theme

import org.junit.Assert.*
import org.junit.Test
import androidx.compose.ui.graphics.Color

class ThemeContrastTest {
    private fun luminance(c: Color): Double {
        fun channel(x: Float): Double { val v=x.toDouble(); return if (v <= .04045) v / 12.92 else Math.pow((v + .055) / 1.055, 2.4) }
        return .2126*channel(c.red)+.7152*channel(c.green)+.0722*channel(c.blue)
    }
    private fun contrast(a: Color,b: Color): Double {
        val x=luminance(a); val y=luminance(b)
        return (maxOf(x,y)+.05)/(minOf(x,y)+.05)
    }
    @Test fun fixedSurfaceAndInputRemainLegibleForSystemDarkAndLight() {
        for (dark in listOf(false,true)) {
            val scheme = simAppColorScheme(dark)
            assertTrue(contrast(scheme.onSurface, scheme.surface) >= 4.5)
            assertTrue(contrast(scheme.onBackground, scheme.background) >= 4.5)
            assertTrue(contrast(scheme.onPrimary, scheme.primary) >= 4.5)
            val p=inputFieldPalette(dark)
            for (ink in listOf(p.text,p.label,p.placeholder,p.icon,p.disabledText,p.disabledLabel,p.errorText,p.errorLabel))
                assertTrue("input contrast $ink", contrast(ink,p.container) >= 4.5)
            assertTrue(contrast(Color(0xFF0F172A),Color.White) >= 4.5)
        }
    }
}
