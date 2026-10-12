package io.github.kgma74.relaix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

// `surface` is the card colour and `background` the page behind it: kept apart
// so a card reads as a card without leaning on elevation shadows.
private val DarkColorScheme = darkColorScheme(
    primary = Indigo400,
    onPrimary = Color(0xFF11122B),
    primaryContainer = Indigo900,
    onPrimaryContainer = Indigo100,
    secondary = MutedInkDark,
    onSecondary = BackgroundDark,
    // Drives the pill behind a selected navigation item; left undefined it
    // falls back to Material's purple and undoes the palette.
    secondaryContainer = Color(0xFF272A52),
    onSecondaryContainer = Color(0xFFC7D2FE),
    background = BackgroundDark,
    onBackground = InkDark,
    surface = CardDark,
    onSurface = InkDark,
    surfaceVariant = SubtleDark,
    onSurfaceVariant = MutedInkDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = CardDark,
    surfaceContainerHighest = SubtleDark,
    error = StatusBadDark,
    outline = BorderDark,
    outlineVariant = BorderDark,
)

private val LightColorScheme = lightColorScheme(
    primary = Indigo600,
    onPrimary = Color.White,
    primaryContainer = Indigo100,
    onPrimaryContainer = Indigo900,
    secondary = MutedInkLight,
    onSecondary = Color.White,
    secondaryContainer = Indigo100,
    onSecondaryContainer = Color(0xFF3730A3),
    background = BackgroundLight,
    onBackground = InkLight,
    surface = CardLight,
    onSurface = InkLight,
    surfaceVariant = SubtleLight,
    onSurfaceVariant = MutedInkLight,
    surfaceContainer = CardLight,
    surfaceContainerHigh = CardLight,
    surfaceContainerHighest = SubtleLight,
    error = StatusBadLight,
    outline = BorderLight,
    outlineVariant = BorderLight,
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
 * An opaque tint of [color] over the card: the background of a status chip or
 * a status banner. Opaque rather than an alpha, so it looks the same whatever
 * sits behind it.
 */
@Composable
fun soft(color: Color, amount: Float = 0.14f): Color =
    color.copy(alpha = amount).compositeOver(MaterialTheme.colorScheme.surface)

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
