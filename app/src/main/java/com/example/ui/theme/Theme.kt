package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = CyanPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
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
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = MutedText,
    outline = LightBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimaryLight,
    onPrimary = DeepSlate,
    primaryContainer = Color(0xFF0369A1),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF818CF8),
    onSecondary = DeepSlate,
    secondaryContainer = Color(0xFF312E81),
    onSecondaryContainer = Color(0xFFEEF2FF),
    tertiary = Color(0xFF34D399),
    onTertiary = DeepSlate,
    background = DarkSurface,
    onBackground = Color(0xFFF8FAFC),
    surface = DarkCard,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = DarkBorder
)

@Composable
fun SIMTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent bright futuristic identity
    content: @Composable () -> Unit,
) {
    val colorScheme = simAppColorScheme(darkTheme)
    /* All application screens use fixed light panels and dark ink, including when Android
       requests night mode. Keep their Material defaults in the SAME owned palette. */
    /* val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    */
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/** One coherent application-owned palette for both OS modes; never mix dark defaults with white cards. */
internal fun simAppColorScheme(@Suppress("UNUSED_PARAMETER") systemDark: Boolean) = LightColorScheme
