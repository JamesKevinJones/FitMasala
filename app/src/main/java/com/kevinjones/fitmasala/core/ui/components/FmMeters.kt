package com.kevinjones.fitmasala.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kevinjones.fitmasala.core.ui.theme.FmMotion
import com.kevinjones.fitmasala.core.ui.theme.FmRadius
import com.kevinjones.fitmasala.core.ui.theme.fm

/**
 * Progress meters. The one place in the app where the visual IS the data, so
 * the rules are informational before they are aesthetic:
 *
 * 1. **Overshoot must be visible.** A ring that silently caps at 100% hides the
 *    single most useful fact a calorie tracker has - that you went over. The
 *    excess is drawn in a second colour on top of the full ring.
 * 2. **Rounded caps, and a visible track.** An empty ring and a missing ring
 *    have to look different, or "nothing logged yet" reads as "zero progress".
 * 3. **Fill animates once, on arrival.** 600ms is long enough to read as a
 *    value arriving rather than a value flickering.
 */
@Composable
fun MacroRing(
    value: Float,
    target: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    strokeWidth: Dp = 8.dp,
    label: String? = null,
    centerContent: (@Composable () -> Unit)? = null,
) {
    val rawFraction = if (target > 0f) value / target else 0f
    val fraction by animateFloatAsState(
        targetValue = rawFraction.coerceIn(0f, 1f),
        animationSpec = tween(FmMotion.MeterFill, easing = FmMotion.EaseOutQuart),
        label = "ringFill",
    )
    val overflow by animateFloatAsState(
        targetValue = (rawFraction - 1f).coerceIn(0f, 1f),
        animationSpec = tween(FmMotion.MeterFill, easing = FmMotion.EaseOutQuart),
        label = "ringOverflow",
    )

    val track = MaterialTheme.fm.track
    val over = MaterialTheme.colorScheme.error

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val stroke = strokeWidth.toPx()
                val inset = stroke / 2f
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                val topLeft = Offset(inset, inset)

                drawArc(
                    color = track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                if (fraction > 0f) {
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
                // Overshoot rides on top of the completed ring.
                if (overflow > 0f) {
                    drawArc(
                        color = over,
                        startAngle = -90f,
                        sweepAngle = 360f * overflow,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }
            centerContent?.invoke()
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.fm.textSecondary,
            )
        }
    }
}

/** Linear meter, same overshoot rule. Used for macro rows and workout volume. */
@Composable
fun FmMeter(
    value: Float,
    target: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
) {
    val rawFraction = if (target > 0f) value / target else 0f
    val fraction by animateFloatAsState(
        targetValue = rawFraction.coerceIn(0f, 1f),
        animationSpec = tween(FmMotion.MeterFill, easing = FmMotion.EaseOutQuart),
        label = "meterFill",
    )
    val isOver = rawFraction > 1f
    val over = MaterialTheme.colorScheme.error

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(FmRadius.Pill)
            .background(MaterialTheme.fm.track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(height)
                .clip(FmRadius.Pill)
                .background(if (isOver) over else color),
        )
    }
}
