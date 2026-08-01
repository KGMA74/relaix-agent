package io.github.kgma74.relaix.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
 * Card used for every panel, so elevation and radius are decided once.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
        ),
    ) {
        content()
    }
}

/**
 * One measurement: a label, a value, and an optional icon.
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
    accent: Color = MaterialTheme.colorScheme.onSurface,
) {
    SectionCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
            )
        }
    }
}

/**
 * Compact coloured label for a job outcome.
 */
@Composable
fun StatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontSize = 10.sp,
        )
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
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp),
        )
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
