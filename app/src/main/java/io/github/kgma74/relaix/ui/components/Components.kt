package io.github.kgma74.relaix.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kgma74.relaix.ui.theme.soft

/**
 * The Relaix mark: a speech bubble on an indigo tile — the same drawing as the
 * dashboard's favicon, so the phone and the console carry one identity.
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val tile = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier = modifier.size(size)) {
        val u = this.size.width / 32f
        drawRoundRect(color = tile, cornerRadius = CornerRadius(7f * u, 7f * u))
        // M8 10h16v10H14l-4 4v-4H8z
        val bubble = Path().apply {
            moveTo(8f * u, 10f * u)
            lineTo(24f * u, 10f * u)
            lineTo(24f * u, 20f * u)
            lineTo(14f * u, 20f * u)
            lineTo(10f * u, 24f * u)
            lineTo(10f * u, 20f * u)
            lineTo(8f * u, 20f * u)
            close()
        }
        drawPath(bubble, color = ink, style = Stroke(width = 2.2f * u, join = StrokeJoin.Round))
    }
}

/**
 * A small filled circle that reads as a state light.
 *
 * Pulses only while [pulsing]: motion means "something is in progress", so a
 * steady dot and a breathing one are two different pieces of information
 * rather than decoration.
 */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
    size: Int = 10,
) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val value by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseAlpha",
        )
        value
    } else {
        1f
    }

    val animatedColor by animateColorAsState(color, label = "dotColor")

    Box(
        modifier = modifier
            .size(size.dp)
            .alpha(alpha)
            .background(animatedColor, CircleShape),
    )
}

/**
 * Card used for every panel, so radius and border are decided once. A border
 * rather than a shadow: it holds up in both themes and on cheap screens.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = color,
        border = BorderStroke(1.dp, borderColor),
    ) {
        content()
    }
}

/** The quiet caption above a group of cards. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 8.dp),
    )
}

/**
 * One measurement: a label, a value, and an icon in a tinted tile.
 *
 * The value is the loud element and the label the quiet one — reversing that
 * is the usual way a dashboard becomes unreadable at arm's length.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    supporting: (@Composable () -> Unit)? = null,
) {
    SectionCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(soft(accent), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(value, style = MaterialTheme.typography.titleLarge)
                    supporting?.invoke()
                }
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Compact coloured pill for a state: a dot and a word. */
@Composable
fun StatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(soft(color), CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/**
 * Signal as four rising bars, for the normalised 0–4 level the scheduler
 * reads. Bars rather than "3/4": the shape is what people already know.
 */
@Composable
fun SignalBars(
    level: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val off = MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier.semantics { contentDescription = "Signal $level sur 4" },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (i in 1..4) {
            Box(
                Modifier
                    .width(4.dp)
                    .height((4 + i * 3).dp)
                    .background(if (i <= level) color else off, RoundedCornerShape(1.dp)),
            )
        }
    }
}

/**
 * Shown when a list has nothing in it, because an empty screen with no
 * explanation reads as a broken screen.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A thin rule between rows inside a card. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline),
    )
}
