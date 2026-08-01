package io.github.kgma74.relaix.ui.theme

import androidx.compose.ui.graphics.Color

// Relaix palette. A teal-slate base rather than the scaffold's purple: this is
// an operator tool living on an always-on handset, and its whole job is to make
// one question — is this node working — answerable at a glance.

val RelaixTeal = Color(0xFF00A99B)
val RelaixTealDark = Color(0xFF00695F)
val RelaixTealLight = Color(0xFF6FE3D6)

val RelaixSlate = Color(0xFF3E5060)
val RelaixSlateLight = Color(0xFFB8C7D4)

val RelaixAmber = Color(0xFFB26A00)
val RelaixAmberLight = Color(0xFFFFCF8B)

val RelaixRed = Color(0xFFB3253A)
val RelaixRedLight = Color(0xFFFFB3B8)

// Status colours named for meaning, not appearance, so no screen ever
// hard-codes "green" and every state stays legible in both themes.
val StatusOkLight = RelaixTealDark
val StatusOkDark = RelaixTealLight
val StatusWaitingLight = RelaixAmber
val StatusWaitingDark = RelaixAmberLight
val StatusBadLight = RelaixRed
val StatusBadDark = RelaixRedLight

val SurfaceDark = Color(0xFF10161A)
val SurfaceDarkElevated = Color(0xFF1A2328)
val SurfaceLight = Color(0xFFF5F8F9)
val SurfaceLightElevated = Color(0xFFFFFFFF)
