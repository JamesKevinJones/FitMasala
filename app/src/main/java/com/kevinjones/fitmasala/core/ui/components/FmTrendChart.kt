package com.kevinjones.fitmasala.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kevinjones.fitmasala.core.ui.theme.FmMotion
import com.kevinjones.fitmasala.core.ui.theme.fm

/**
 * Weight: raw weigh-ins as dots, the smoothed trend as a line, the goal as a
 * dashed rule.
 *
 * This is the app's central argument rendered as a picture. Every other tracker
 * plots raw daily weight and lets the user panic at a two-kilo water swing.
 * Showing BOTH - the scatter you actually stood on, and the trend the plan
 * actually uses - teaches the thing the plan depends on in one glance: the dots
 * jump, the line does not, and only the line means anything.
 *
 * Drawn on Canvas rather than pulled from a charting library. The library would
 * add a dependency, a theme to fight, and a default look shared with every other
 * app using it - for a chart that is two paths and a scatter.
 */
@Composable
fun FmTrendChart(
    /** Raw daily weights, oldest first. */
    raw: List<Float>,
    /** Smoothed trend, same length and order as [raw]. */
    trend: List<Float>,
    goal: Float,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (raw.isEmpty() || trend.size != raw.size) {
        FmEmptyChart(modifier, height)
        return
    }

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(raw.size) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        // Slower than a UI transition on purpose: the line DRAWING left to right
        // reads as time passing, which is what the axis actually is.
        animationSpec = tween(1100, easing = FmMotion.EaseOutQuart),
        label = "trendDraw",
    )

    val dotColor = MaterialTheme.fm.textMuted
    val goalColor = MaterialTheme.fm.progress
    val gridColor = MaterialTheme.fm.borderSubtle

    // Scale to the DATA, not to the goal.
    //
    // Including a goal 7kg below current weight squashed three kilos of real
    // movement into a ten-kilo axis, and the line read as flat while the user
    // was actually losing 0.6kg a week - the chart contradicting the number
    // printed above it. When the goal falls outside the visible range it gets an
    // edge marker instead of forcing the scale.
    val values = raw + trend
    val pad = ((values.max() - values.min()) * 0.25f).coerceAtLeast(0.3f)
    val minV = values.min() - pad
    val maxV = values.max() + pad
    val span = (maxV - minV).coerceAtLeast(0.1f)
    val goalInRange = goal in minV..maxV

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            fun x(i: Int) = size.width * (i.toFloat() / (raw.size - 1).coerceAtLeast(1))
            fun y(v: Float) = size.height * (1f - ((v - minV) / span))

            drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), 1f)

            // The goal, dashed so it reads as a target rather than data. Only
            // drawn when it is actually on screen.
            if (goalInRange) {
                val goalY = y(goal)
                drawLine(
                    color = goalColor.copy(alpha = 0.7f),
                    start = Offset(0f, goalY),
                    end = Offset(size.width, goalY),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                )
            }

            val visible = (raw.size * progress).toInt().coerceIn(1, raw.size)

            // Trend path, plus a soft fill beneath it for weight on the page.
            val line = Path().apply {
                moveTo(x(0), y(trend[0]))
                for (i in 1 until visible) lineTo(x(i), y(trend[i]))
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(x(visible - 1), size.height)
                lineTo(x(0), size.height)
                close()
            }
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    listOf(lineColor.copy(alpha = 0.14f), Color.Transparent),
                ),
            )
            drawPath(line, lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round))

            // Raw weigh-ins last, so they sit over the line. Small and faint:
            // they are context, and the trend is the answer.
            for (i in 0 until visible) {
                drawCircle(dotColor, radius = 3.5f, center = Offset(x(i), y(raw[i])))
            }

            // The live end of the trend, ringed so it separates from the dots.
            val lastX = x(visible - 1)
            val lastY = y(trend[visible - 1])
            drawCircle(lineColor, radius = 7f, center = Offset(lastX, lastY))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ChartKey("Weigh-ins", dotColor)
            ChartKey("Trend", lineColor)
            ChartKey(
                label = if (goalInRange) "Goal ${goal}kg" else "Goal ${goal}kg (below)",
                color = goalColor,
            )
        }
    }
}

@Composable
private fun ChartKey(label: String, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.fm.textSecondary,
        )
    }
}

@Composable
private fun FmEmptyChart(modifier: Modifier, height: Dp) {
    Column(modifier.fillMaxWidth().height(height), verticalArrangement = Arrangement.Center) {
        Text(
            text = "Weigh in for a few days and the trend appears here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.fm.textSecondary,
        )
    }
}
