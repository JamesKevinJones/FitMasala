package com.kevinjones.fitmasala.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Durations and curves, in one place so they cannot drift per screen.
 *
 * The governing rule is the delight-impact curve: the more often a user hits a
 * moment, the less it can carry. A button press happens hundreds of times a day
 * and gets 90ms with no flourish. A streak milestone happens once and earns a
 * full second.
 */
object FmMotion {

    /** Sharp start, smooth tail. The default for entrances and surfaces. */
    val EaseOutQuart: Easing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)

    /** Snappier still, no overshoot. Taps and presses. */
    val EaseOutExpo: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Symmetric, for things that travel between two states. */
    val EaseInOut: Easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

    /** Slight overshoot. Only where something should feel physical. */
    val EaseSpring: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    // ease-in is deliberately absent. On the way in it reads as hesitation.

    const val Press = 90          // per-action: must feel immediate
    const val Hover = 160
    const val Surface = 220       // cards, sheets appearing
    const val Screen = 300
    /** Exit is 60-80% of enter. Symmetric transitions feel sluggish leaving. */
    const val SurfaceExit = 160

    /** Rings and bars filling on first paint. Long enough to read as motion. */
    const val MeterFill = 600

    /** Rare milestones only - a streak day, a goal hit, a PR. */
    const val Celebrate = 900
}
