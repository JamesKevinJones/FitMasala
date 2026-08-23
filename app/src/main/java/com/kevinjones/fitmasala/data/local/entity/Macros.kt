package com.kevinjones.fitmasala.data.local.entity

import androidx.room.ColumnInfo

/**
 * Embedded WITHOUT a column prefix, so the columns land as `calories`,
 * `proteinG` etc. That is deliberate: the daily-total queries sum these columns
 * directly, and `SUM(macro_calories)` in every aggregate is noise.
 *
 * Grams are stored as Double because the LLM returns fractional grams and
 * rounding at write time loses information you can never get back. Round at the
 * UI, never in the database.
 */
data class Macros(
    @ColumnInfo(name = "calories") val calories: Double = 0.0,
    @ColumnInfo(name = "proteinG") val proteinG: Double = 0.0,
    @ColumnInfo(name = "carbsG") val carbsG: Double = 0.0,
    @ColumnInfo(name = "fatG") val fatG: Double = 0.0,
    @ColumnInfo(name = "fiberG") val fiberG: Double = 0.0,
) {
    operator fun plus(other: Macros) = Macros(
        calories = calories + other.calories,
        proteinG = proteinG + other.proteinG,
        carbsG = carbsG + other.carbsG,
        fatG = fatG + other.fatG,
        fiberG = fiberG + other.fiberG,
    )

    operator fun times(factor: Double) = Macros(
        calories = calories * factor,
        proteinG = proteinG * factor,
        carbsG = carbsG * factor,
        fatG = fatG * factor,
        fiberG = fiberG * factor,
    )

    /**
     * Atwater check: 4/4/9 kcal per gram should land near the stated calories.
     * The LLM regularly returns macros that don't add up to its own calorie
     * figure — this is how the UI catches that instead of logging nonsense.
     */
    val derivedCalories: Double
        get() = proteinG * 4 + carbsG * 4 + fatG * 9

    fun isInternallyConsistent(tolerance: Double = 0.15): Boolean {
        if (calories <= 0.0) return false
        val drift = kotlin.math.abs(derivedCalories - calories) / calories
        return drift <= tolerance
    }

    companion object {
        val ZERO = Macros()
    }
}
