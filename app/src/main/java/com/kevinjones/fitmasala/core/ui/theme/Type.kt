package com.kevinjones.fitmasala.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Sentence case throughout. The previous theme set every label in uppercase
 * monospace with wide tracking, which is why the screen read as a warning
 * notice rather than an app - uppercase kills word-shape recognition, so each
 * label had to be spelled out rather than glanced at.
 *
 * Numbers get the display treatment, because in this app the number IS the
 * content. "1,840" should carry the same weight a track title carries in a
 * music app.
 */
private val Sans = DmSans
private val Display = DmSansDisplay

/**
 * Tabular figures for anything that updates in place - a calorie counter or a
 * rest timer must not jiggle as digits change width. Compose exposes this
 * through FontFeatureSetting rather than a typed API.
 */
private const val TabularFigures = "tnum"

/** Big numeric readouts: today's calories, weight, the rest timer. */
fun numeric(size: Int, weight: FontWeight = FontWeight.Bold) = TextStyle(
    fontFamily = Display,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * 1.05f).sp,
    letterSpacing = (-0.03).em,
    fontFeatureSettings = TabularFigures,
)

val FitMasalaTypography = Typography(
    // Screen titles. Tight tracking - large type needs less letter spacing,
    // and the default optical spacing looks loose above ~28sp.
    displayLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.035).em,
    ),
    displayMedium = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.03).em,
    ),
    displaySmall = numeric(28),

    headlineMedium = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.02).em,
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.015).em,
    ),

    // Section headers. Sentence case and bold, not uppercase mono.
    titleLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        letterSpacing = (-0.01).em,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),

    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),

    // Buttons. Sentence case, tight - "Ask AI chef", not "ASK AI CHEF".
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.01).em,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    ),
    /** Metadata under a value: "of 2,400 kcal". Small, quiet, never shouted. */
    labelSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.01.em,
        fontFeatureSettings = TabularFigures,
    ),
)
