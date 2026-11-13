package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.preferences.ThemePreset

private val ObsidianColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF002026),
    primaryContainer = ElectricCyanContainer,
    onPrimaryContainer = Color(0xFFB8F8FF),
    secondary = SignalViolet,
    onSecondary = Color.White,
    secondaryContainer = SignalVioletContainer,
    onSecondaryContainer = Color(0xFFE6DEFF),
    tertiary = CinemaAmber,
    onTertiary = Color(0xFF261900),
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    error = LiveCrimson,
    onError = Color.White
)

private val OledColorScheme = ObsidianColorScheme.copy(
    background = OledBg,
    surface = OledSurface,
    surfaceVariant = OledSurfaceVariant,
    outline = Color(0xFF26293B)
)

private val EmeraldColorScheme = ObsidianColorScheme.copy(
    primary = MatrixNeon,
    onPrimary = Color(0xFF002617),
    primaryContainer = Color(0xFF004D32),
    onPrimaryContainer = Color(0xFFC2FFE6),
    background = EmeraldBg,
    surface = EmeraldSurface,
    surfaceVariant = EmeraldSurfaceVariant,
    outline = Color(0xFF235242)
)

private val DaylightColorScheme = lightColorScheme(
    primary = DaylightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC2F2FF),
    onPrimaryContainer = Color(0xFF001F25),
    secondary = DaylightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3DFFF),
    onSecondaryContainer = Color(0xFF160067),
    tertiary = Color(0xFFB87800),
    onTertiary = Color.White,
    background = DaylightBg,
    onBackground = TextPrimaryLight,
    surface = DaylightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFC6D2E8),
    error = LiveCrimson,
    onError = Color.White
)

val BroadcastShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun NovaStreamTheme(
    themePreset: ThemePreset = ThemePreset.OBSIDIAN,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themePreset) {
        ThemePreset.OBSIDIAN -> ObsidianColorScheme
        ThemePreset.OLED_BLACK -> OledColorScheme
        ThemePreset.EMERALD_STUDIO -> EmeraldColorScheme
        ThemePreset.DAYLIGHT -> DaylightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = BroadcastShapes,
        content = content
    )
}
