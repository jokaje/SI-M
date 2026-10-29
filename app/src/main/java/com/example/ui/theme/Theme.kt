package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colours for SI-M surfaces. Screens must read these instead of hard-coded
 * hex values, so switching light/dark never mixes ink and panel from different palettes.
 */
data class SimPalette(
    /** Screen background behind the cards. */
    val surface: Color,
    /** Card / panel background. */
    val card: Color,
    /** Secondary panel background (chips, hints, nested rows). */
    val cardAlt: Color,
    /** Primary text on cards. */
    val ink: Color,
    /** Secondary text on cards. */
    val body: Color,
    /** Muted labels and captions (still >= 4.5:1 on [card]). */
    val muted: Color,
    /** Brand accent for buttons, links and selected states. */
    val accent: Color,
    /** Content colour on top of [accent]. */
    val onAccent: Color,
    /** Tinted accent surface (selection, chips). */
    val accentSoft: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    /** Info banner background/ink pair. */
    val infoBg: Color,
    val infoInk: Color,
    val border: Color,
    /** Tinted success surface (confirmation banners). */
    val successSoft: Color,
    /** Secondary brand colour (indigo) for secondary accents. */
    val accentAlt: Color,
)

private val LightPalette = SimPalette(
    surface = LightSurface,
    card = LightCard,
    cardAlt = Color(0xFFF1F5F9),
    ink = DeepSlate,
    body = Color(0xFF334155),
    muted = Color(0xFF475569),
    accent = CyanPrimary,
    onAccent = Color.White,
    accentSoft = Color(0xFFE0F2FE),
    success = Color(0xFF15803D),
    warning = Color(0xFFB45309),
    danger = Color(0xFFB91C1C),
    infoBg = Color(0xFFEFF6FF),
    infoInk = Color(0xFF1E3A8A),
    border = LightBorder,
    successSoft = Color(0xFFDCFCE7),
    accentAlt = IndigoAccent,
)

private val DarkPalette = SimPalette(
    surface = DarkSurface,
    card = DarkCard,
    cardAlt = DarkCardAlt,
    ink = DarkInk,
    body = DarkBody,
    muted = DarkMuted,
    accent = DarkAccent,
    onAccent = DarkOnAccent,
    accentSoft = DarkAccentSoft,
    success = DarkSuccess,
    warning = DarkWarning,
    danger = DarkDanger,
    infoBg = DarkInfoBg,
    infoInk = DarkInfoInk,
    border = DarkBorder,
    successSoft = Color(0xFF10331F),
    accentAlt = Color(0xFF818CF8),
)

/** Palette for a given mode; kept pure so unit tests can assert both variants. */
internal fun simPaletteFor(dark: Boolean): SimPalette = if (dark) DarkPalette else LightPalette

/** Which palette the current composition runs in. Set by [SIMTheme], read via [simPalette]. */
internal val LocalSimDark = staticCompositionLocalOf { false }

/** The active semantic palette. */
@Composable
fun simPalette(): SimPalette {
    val dark = LocalSimDark.current
    return remember(dark) { simPaletteFor(dark) }
}

private val LightColorScheme = lightColorScheme(
    primary = CyanPrimary,
    onPrimary = Color.White,
    primaryContainer = LightPalette.accentSoft,
    onPrimaryContainer = CyanPrimary,
    secondary = IndigoAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEF2FF),
    onSecondaryContainer = Color(0xFF3730A3),
    tertiary = OrbGazeEmerald,
    onTertiary = Color.White,
    background = LightSurface,
    onBackground = DeepSlate,
    surface = LightCard,
    onSurface = DeepSlate,
    surfaceVariant = LightPalette.cardAlt,
    onSurfaceVariant = LightPalette.body,
    outline = LightBorder,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkOnAccent,
    primaryContainer = DarkAccentSoft,
    onPrimaryContainer = DarkInfoInk,
    secondary = Color(0xFF818CF8),
    onSecondary = DarkOnAccent,
    secondaryContainer = Color(0xFF312E81),
    onSecondaryContainer = Color(0xFFEEF2FF),
    tertiary = DarkSuccess,
    onTertiary = DarkOnAccent,
    background = DarkSurface,
    onBackground = DarkInk,
    surface = DarkCard,
    onSurface = DarkInk,
    surfaceVariant = DarkCardAlt,
    onSurfaceVariant = DarkBody,
    outline = DarkBorder,
)

/** Application-owned colour scheme for either mode; never mixes OS defaults with app panels. */
internal fun simAppColorScheme(systemDark: Boolean) = if (systemDark) DarkColorScheme else LightColorScheme

@Composable
fun SIMTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSimDark provides darkTheme) {
        MaterialTheme(
            colorScheme = simAppColorScheme(darkTheme),
            typography = Typography,
            content = content
        )
    }
}
