package com.kevinjones.fitmasala.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kevinjones.fitmasala.core.ui.theme.FmMotion

/**
 * Shared motion helpers.
 *
 * The governing rule is that motion must carry meaning. Each of these exists
 * because a static version loses information:
 *
 * - A number that counts up shows the value ARRIVING, which distinguishes
 *   "loaded and it is 1,590" from "still loading".
 * - A staggered list shows ORDER, which a list that appears all at once does not.
 * - A settle on first paint shows that content is composed rather than painted.
 *
 * Anything that would only be decoration is deliberately absent.
 */

/**
 * Counts an integer up to its target.
 *
 * Duration scales with the size of the change, so a jump from 0 to 1,590 reads
 * as an arrival while a change from 1,590 to 1,610 is nearly instant. A fixed
 * duration makes small updates feel sluggish and large ones feel abrupt.
 */
@Composable
fun animatedCount(target: Int, maxDurationMs: Int = 900): Int {
    var previous by remember { mutableStateOf(0) }
    val delta = kotlin.math.abs(target - previous)
    val duration = when {
        delta == 0 -> 0
        delta < 50 -> 200
        delta < 500 -> 500
        else -> maxDurationMs
    }
    val value by animateIntAsState(
        targetValue = target,
        animationSpec = tween(duration, easing = FmMotion.EaseOutQuart),
        label = "count",
    )
    LaunchedEffect(target) { previous = target }
    return value
}

/**
 * Entry animation for a list item: fades and rises into place, staggered by
 * index.
 *
 * The stagger is capped at eight items. Beyond that the last item would wait
 * nearly half a second for no benefit - and a list of forty rows animating in
 * sequence is a loading screen pretending to be a list.
 */
@Composable
fun Modifier.enterFromBelow(
    index: Int = 0,
    perItemDelayMs: Int = 45,
    rise: Dp = 12.dp,
): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    val delay = (index.coerceAtMost(8)) * perItemDelayMs
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(FmMotion.Surface, delayMillis = delay, easing = FmMotion.EaseOutQuart),
        label = "enter",
    )
    val risePx = with(LocalDensity.current) { rise.toPx() }

    // graphicsLayer, not offset/padding: translation and alpha are composited on
    // the render thread and skip layout entirely. Animating offset would
    // re-measure the whole list every frame.
    return this
        .graphicsLayer {
            translationY = (1f - progress) * risePx
            alpha = progress
        }
}

/** Fades content in on first composition. For a single element, not a list. */
@Composable
fun Modifier.fadeInOnce(durationMs: Int = FmMotion.Surface): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMs, easing = FmMotion.EaseOutQuart),
        label = "fadeInOnce",
    )
    return this.alpha(alpha)
}
