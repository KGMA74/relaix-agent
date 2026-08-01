package io.github.kgma74.relaix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = RelaixTealLight,
    onPrimary = Color(0xFF00382F),
    primaryContainer = RelaixTealDark,
    onPrimaryContainer = RelaixTealLight,
    secondary = RelaixSlateLight,
    onSecondary = Color(0xFF10161A),
    // Drives the pill behind a selected navigation item; left undefined it
    // falls back to Material's purple and undoes the palette.
    secondaryContainer = Color(0xFF14403A),
    onSecondaryContainer = RelaixTealLight,
    tertiary = RelaixAmberLight,
    background = SurfaceDark,
    onBackground = Color(0xFFE2E8EB),
    surface = SurfaceDark,
    onSurface = Color(0xFFE2E8EB),
    surfaceVariant = SurfaceDarkElevated,
    onSurfaceVariant = Color(0xFF9FB0B8),
    error = RelaixRedLight,
    outline = Color(0xFF3A464C),
)

private val LightColorScheme = lightColorScheme(
    primary = RelaixTealDark,
    onPrimary = Color.White,
    primaryContainer = RelaixTealLight,
    onPrimaryContainer = Color(0xFF00201B),
    secondary = RelaixSlate,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEFEA),
    onSecondaryContainer = Color(0xFF00352E),
    tertiary = RelaixAmber,
    background = SurfaceLight,
    onBackground = Color(0xFF141A1C),
    surface = SurfaceLight,
    onSurface = Color(0xFF141A1C),
    surfaceVariant = SurfaceLightElevated,
    onSurfaceVariant = Color(0xFF4A5960),
    error = RelaixRed,
    outline = Color(0xFFC5D0D5),
)

/**
 * Semantic status colours, resolved once per theme.
 *
 * Screens ask for "this is a problem" rather than picking a red, so every
 * state stays legible in both themes — the red that reads well on white is
 * muddy on near-black, and the reverse.
 */
data class StatusColors(
    val ok: Color,
    val waiting: Color,
    val bad: Color,
)

val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(ok = StatusOkLight, waiting = StatusWaitingLight, bad = StatusBadLight)
}

/**
 * Dynamic colour is deliberately not used. This app is read as an instrument,
 * and letting "connected" become whatever the wallpaper suggests would undo
 * the one thing the palette exists for.
 */
@Composable
fun RelaixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val statusColors = if (darkTheme) {
        StatusColors(ok = StatusOkDark, waiting = StatusWaitingDark, bad = StatusBadDark)
    } else {
        StatusColors(ok = StatusOkLight, waiting = StatusWaitingLight, bad = StatusBadLight)
    }

    CompositionLocalProvider(LocalStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
