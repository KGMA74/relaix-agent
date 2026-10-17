package io.github.kgma74.relaix.ui.theme

import androidx.compose.ui.graphics.Color

// Relaix palette, shared with the web dashboard (server/ui/src/index.css) so
// the phone and the console read as one product: an instrument's look, warm
// paper or graphite, ink for what you press. Colour is kept for meaning:
// whether this node is working is said by the status colours below, and
// nothing else on a screen competes with them.

// Light: paper and ink.
val BackgroundLight = Color(0xFFF3F1EC)
val CardLight = Color(0xFFFBFAF7)
val InkLight = Color(0xFF1B1A17)
val MutedInkLight = Color(0xFF66625A)
val BorderLight = Color(0xFFDBD7CD)
val SubtleLight = Color(0xFFEBE8E1)

// Dark: graphite.
val BackgroundDark = Color(0xFF141412)
val CardDark = Color(0xFF1B1B18)
val InkDark = Color(0xFFE9E6DF)
val MutedInkDark = Color(0xFF9C978C)
val BorderDark = Color(0xFF2F2E2A)
val SubtleDark = Color(0xFF262622)

// Status colours named for meaning, not appearance, so no screen ever
// hard-codes "green" and every state stays legible in both themes.
val StatusOkLight = Color(0xFF1D6B45)
val StatusOkDark = Color(0xFF62C08D)
val StatusWaitingLight = Color(0xFF8F5A00)
val StatusWaitingDark = Color(0xFFE0A840)
val StatusBadLight = Color(0xFFB3261E)
val StatusBadDark = Color(0xFFF2786C)
