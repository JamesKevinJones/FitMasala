package com.kevinjones.fitmasala.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kevinjones.fitmasala.core.ui.theme.Fm
import com.kevinjones.fitmasala.core.ui.theme.FmMotion
import com.kevinjones.fitmasala.core.ui.theme.FmRadius
import com.kevinjones.fitmasala.core.ui.theme.fm
import kotlin.math.cos
import kotlin.math.sin

/**
 * One katori on the thali: how full it is against its target, plus [adding] -
 * a meal under review, poured in pale on top until it is logged.
 */
@Immutable
data class Katori(
    val value: Float,
    val target: Float,
    val color: Color,
    val label: String,
    val adding: Float = 0f,
)

/**
 * The day as a steel thali.
 *
 * The rim is the calorie meter; the three katoris are protein, carbs and fat,
 * each filling from the bottom like dal poured into a bowl. It replaces a row
 * of three identical rings, which said "fitness app" and nothing about whose
 * plate this is.
 *
 * Same informational rules as [MacroRing]: a visible empty track, and overshoot
 * drawn in the error colour - a katori that overflows grows a red lip.
 *
 * [kcalAdding] and each [Katori.adding] draw a meal that is not logged yet as a
 * paler pour above what is, so the photo review shows where this meal leaves
 * the day - including going over - before anything is saved.
 */
@Composable
fun FmThali(
    kcal: Float,
    kcalTarget: Float,
    katoris: List<Katori>,
    modifier: Modifier = Modifier,
    size: Dp = 196.dp,
    kcalAdding: Float = 0f,
) {
    val spec = tween<Float>(FmMotion.MeterFill, easing = FmMotion.EaseOutQuart)
    fun ratio(v: Float, t: Float) = if (t > 0f) v / t else 0f
    val rawRim = ratio(kcal, kcalTarget)
    val rawRimWith = ratio(kcal + kcalAdding, kcalTarget)
    val rim by animateFloatAsState(rawRim.coerceIn(0f, 1f), spec, label = "rim")
    val rimWith by animateFloatAsState(rawRimWith.coerceIn(0f, 1f), spec, label = "rimWith")
    val rimOver by animateFloatAsState((rawRimWith - 1f).coerceIn(0f, 1f), spec, label = "rimOver")
    // Fixed-size list, so these calls stay in the same order every composition.
    val fills = katoris.map { k ->
        animateFloatAsState(ratio(k.value, k.target).coerceIn(0f, 1f), spec, label = "katori-${k.label}").value
    }
    val pours = katoris.map { k ->
        animateFloatAsState(
            ratio(k.value + k.adding, k.target).coerceIn(0f, 1f),
            spec,
            label = "pour-${k.label}",
        ).value
    }

    val plate = MaterialTheme.colorScheme.surfaceContainerHigh
    val well = MaterialTheme.colorScheme.surfaceContainerLowest
    val steel = MaterialTheme.fm.borderStrong
    val track = MaterialTheme.fm.track
    val primary = MaterialTheme.colorScheme.primary
    val over = MaterialTheme.colorScheme.error

    val description = buildString {
        append("${kcal.toInt()} of ${kcalTarget.toInt()} calories")
        if (kcalAdding > 0f) append(", plus ${kcalAdding.toInt()} from this meal")
        append(". ")
        katoris.forEach {
            append("${it.label} ${it.value.toInt()}")
            if (it.adding > 0f) append(" plus ${it.adding.toInt()}")
            append(" of ${it.target.toInt()} grams. ")
        }
    }

    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val r = this.size.minDimension / 2f
        val c = center
        val rimStroke = r * 0.075f

        // Plate and its raised lip.
        drawCircle(plate, radius = r - rimStroke / 2f, center = c)
        drawCircle(steel, radius = r * 0.80f, center = c, style = Stroke(width = 1.dp.toPx()))

        // Calorie rim: track, fill, then overshoot on top.
        val arcInset = rimStroke / 2f
        val arcSize = Size(this.size.width - rimStroke, this.size.height - rimStroke)
        val topLeft = Offset(arcInset, arcInset)
        fun rimArc(color: Color, sweep: Float) = drawArc(
            color, -90f, sweep, false, topLeft, arcSize,
            style = Stroke(rimStroke, cap = StrokeCap.Round),
        )
        rimArc(track, 360f)
        // The pour first, so the logged arc's round cap sits on top of it.
        if (rimWith > rim) rimArc(primary.copy(alpha = PourAlpha), 360f * rimWith)
        if (rim > 0f) rimArc(primary, 360f * rim)
        if (rimOver > 0f) rimArc(over, 360f * rimOver)

        // Katoris sit in a triangle on the plate, the way a thali is laid:
        // two up top, one below.
        val bowlR = r * 0.27f
        val spread = r * 0.42f
        katoris.forEachIndexed { i, k ->
            val angle = Math.toRadians((-150.0 + i * 120.0)).toFloat()
            val bc = Offset(c.x + spread * cos(angle), c.y + spread * sin(angle))
            val overflowing = k.target > 0f && k.value + k.adding > k.target
            drawKatori(bc, bowlR, fills[i], pours[i], overflowing, k.color, well, steel, over)
        }
    }
}

/**
 * A katori's key: its colour, its name, and how far along it is - with the
 * meal under review counted in, and named, when there is one.
 */
@Composable
fun FmKatoriLegend(katori: Katori, modifier: Modifier = Modifier) {
    val total = katori.value + katori.adding
    val full = katori.target > 0f && total >= katori.target
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(Fm.tight).clip(FmRadius.Pill).background(katori.color))
            Text(katori.label, style = MaterialTheme.typography.labelMedium)
        }
        Text(
            text = "${total.toInt()} / ${katori.target.toInt()} g",
            style = MaterialTheme.typography.labelSmall,
            color = if (full) katori.color else MaterialTheme.fm.textSecondary,
        )
        if (katori.adding > 0f) {
            Text(
                text = "+${katori.adding.toInt()} g this meal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.fm.textMuted,
            )
        }
    }
}

private fun DrawScope.drawKatori(
    center: Offset,
    radius: Float,
    fill: Float,
    pour: Float,
    overflowing: Boolean,
    color: Color,
    well: Color,
    steel: Color,
    over: Color,
) {
    drawCircle(well, radius, center)
    if (pour > 0f) {
        val bowl = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(center, radius))
        }
        clipPath(bowl) {
            fun level(to: Float, tint: Color) {
                val top = center.y + radius - 2f * radius * to
                drawRect(
                    color = tint,
                    topLeft = Offset(center.x - radius, top),
                    size = Size(radius * 2f, center.y + radius - top),
                )
            }
            if (pour > fill) level(pour, color.copy(alpha = PourAlpha))
            if (fill > 0f) level(fill, color)
        }
    }
    // Steel rim; red when the katori has run over its target.
    drawCircle(
        color = if (overflowing) over else steel,
        radius = radius,
        center = center,
        style = Stroke(width = if (overflowing) radius * 0.12f else radius * 0.08f),
    )
}

/** How strongly an unlogged meal shows: clearly there, clearly not yet eaten. */
private const val PourAlpha = 0.42f
