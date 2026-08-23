package com.kevinjones.fitmasala.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * A radius scale, not one uniform radius. Uniform rounding everywhere is the
 * lazy default; the scale is a personality dial.
 *
 * Default body radius is [Card] (16dp). Everything else is a deliberate
 * deviation.
 *
 * Nested-radius rule: inner radius = outer radius - the gap between them. A
 * 16dp card with 8dp padding holds an 8dp inner surface. Getting this wrong is
 * what makes nested cards look slightly melted.
 */
object FmRadius {
    val Badge = RoundedCornerShape(6.dp)
    val Input = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(16.dp)
    val Sheet = RoundedCornerShape(24.dp)
    val Hero = RoundedCornerShape(28.dp)
    val Pill = RoundedCornerShape(percent = 50)

    /** Inner surface sitting inside a [Card] with 8dp of padding. */
    val CardInner = RoundedCornerShape(8.dp)
}

val FitMasalaShapes = Shapes(
    extraSmall = FmRadius.Badge,
    small = FmRadius.Input,
    medium = FmRadius.Card,
    large = FmRadius.Sheet,
    extraLarge = FmRadius.Hero,
)
