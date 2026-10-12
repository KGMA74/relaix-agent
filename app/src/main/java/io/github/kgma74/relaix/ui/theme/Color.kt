package io.github.kgma74.relaix.ui.theme

import androidx.compose.ui.graphics.Color

// Relaix palette, shared with the web dashboard (server/ui/src/index.css) so
// the phone and the console read as one product: an indigo brand on cool
// neutrals. The brand colour is for identity and actions only; whether this
// node is working is said by the status colours below, never by indigo.

val Indigo600 = Color(0xFF4F46E5)
val Indigo400 = Color(0xFF818CF8)
val Indigo100 = Color(0xFFE0E7FF)
val Indigo900 = Color(0xFF312E81)

// Light
val BackgroundLight = Color(0xFFF5F6FA)
val CardLight = Color(0xFFFFFFFF)
val InkLight = Color(0xFF15171C)
val MutedInkLight = Color(0xFF5C6475)
val BorderLight = Color(0xFFDFE2EA)
val SubtleLight = Color(0xFFECEEF4)

// Dark
val BackgroundDark = Color(0xFF0E1116)
val CardDark = Color(0xFF161A21)
val InkDark = Color(0xFFE6E8EE)
val MutedInkDark = Color(0xFF8B93A3)
val BorderDark = Color(0xFF252B35)
val SubtleDark = Color(0xFF1D222B)

// Status colours named for meaning, not appearance, so no screen ever
// hard-codes "green" and every state stays legible in both themes.
val StatusOkLight = Color(0xFF15803D)
val StatusOkDark = Color(0xFF34D399)
val StatusWaitingLight = Color(0xFFB45309)
val StatusWaitingDark = Color(0xFFFBBF24)
val StatusBadLight = Color(0xFFC62828)
val StatusBadDark = Color(0xFFF87171)
