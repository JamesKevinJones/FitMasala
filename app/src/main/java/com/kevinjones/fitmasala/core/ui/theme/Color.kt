package com.kevinjones.fitmasala.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * App-specific colours Material 3 has no role for.
 *
 * Everything Material DOES have a role for lives in the ColorScheme in
 * `Theme.kt`, derived from the tonal palettes in `Tones.kt`. This file holds
 * only the additions: the macro triad and the streak tiers.
 *
 * Both come in a light and a dark set, because a chart colour chosen to read on
 * near-black is usually illegible on near-white. Deriving one from the other by
 * flipping lightness is what produces the washed-out charts common in apps that
 * bolted dark mode on afterwards.
 */

/**
 * Macro chart colours, fixed app-wide so the reader learns them once: protein
 * saffron, carbs wheat, fat terracotta. One warm family, separated by lightness
 * as well as hue so they survive red-green colour deficiency.
 */
val MacroProteinDark = Color(0xFFF4A43C)
val MacroCarbsDark = Color(0xFFE8C86A)
val MacroFatDark = Color(0xFFD96F4E)

// Around tone 45-50: dark enough to hold contrast on a near-white surface.
val MacroProteinLight = Color(0xFFA96900)
val MacroCarbsLight = Color(0xFF7A6A10)
val MacroFatLight = Color(0xFFB44424)

/** Streak tiers. The flame only warms as the streak is earned. */
val StreakColdDark = Color(0xFF8A8279)
val StreakWarmDark = Color(0xFFF4A43C)
val StreakHotDark = Color(0xFFE06A52)

val StreakColdLight = Color(0xFF6E6659)
val StreakWarmLight = Color(0xFFA96900)
val StreakHotLight = Color(0xFFB44424)
