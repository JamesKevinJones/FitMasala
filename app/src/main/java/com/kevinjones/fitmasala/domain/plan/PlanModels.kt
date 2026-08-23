package com.kevinjones.fitmasala.domain.plan

/**
 * Cutting-plan domain model. Deliberately pure Kotlin — no Android, no Room, no
 * network — so all of it is unit-testable on the JVM and runs in microseconds on
 * the main thread. The only expensive work in this feature is the vision call;
 * none of the maths below needs to leave the CPU it starts on.
 */

enum class Sex { MALE, FEMALE }

enum class ActivityLevel(val multiplier: Double, val label: String) {
    SEDENTARY(1.2, "Desk job, no training"),
    LIGHT(1.375, "Training 1–3x/week"),
    MODERATE(1.55, "Training 3–5x/week"),
    HIGH(1.725, "Training 6–7x/week"),
    ATHLETE(1.9, "Physical job + training"),
}

/** How hard the cut runs. Rates are % of bodyweight per week. */
enum class CutAggression(val weeklyRatePctBodyweight: Double, val label: String) {
    CONSERVATIVE(0.4, "Slow — best muscle retention"),
    STANDARD(0.7, "Standard"),
    AGGRESSIVE(1.0, "Fast — expect strength loss"),
}

/**
 * A body-composition snapshot.
 *
 * [bodyFatPercent] is the number the whole plan pivots on, and it is the least
 * reliable input in the system — a tape measure is roughly ±3% against a DEXA.
 * That is why [source] is carried: the UI must show where the number came from,
 * because "12% by tape" and "12% by DEXA" can be four percentage points apart.
 */
data class BodyComposition(
    val weightKg: Double,
    val bodyFatPercent: Double,
    val source: BodyFatSource,
) {
    val fatMassKg: Double get() = weightKg * (bodyFatPercent / 100.0)
    val leanMassKg: Double get() = weightKg - fatMassKg

    init {
        require(weightKg > 0) { "weightKg must be positive" }
        require(bodyFatPercent in 1.0..70.0) { "bodyFatPercent out of plausible range" }
    }
}

enum class BodyFatSource { NAVY_TAPE, MANUAL_ENTRY, SMART_SCALE, DEXA, ESTIMATED }

/** Tape measurements, in centimetres. Hip is only needed for the female formula. */
data class TapeMeasurements(
    val heightCm: Double,
    val neckCm: Double,
    val waistCm: Double,
    val hipCm: Double? = null,
)

data class MacroTarget(
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
) {
    val proteinKcal get() = proteinG * 4
    val carbKcal get() = carbsG * 4
    val fatKcal get() = fatG * 9
}

/**
 * Why a plan was clamped. Surfacing this matters: a target silently floored at
 * 1500 kcal while the UI still promises a date is how someone ends up believing
 * a timeline that the engine already knows is wrong.
 */
enum class PlanWarning {
    DEFICIT_CAPPED_AT_TDEE_FRACTION,
    CALORIES_AT_SAFETY_FLOOR,
    RATE_REDUCED_FOR_LOW_BODY_FAT,
    CARBS_SQUEEZED_TO_ZERO,
    ALREADY_AT_OR_BELOW_GOAL,
    INSUFFICIENT_DATA_FOR_ADAPTIVE_TDEE,
    WEIGHT_TREND_STALLED,
    INTAKE_LOGGING_TOO_SPARSE,
}

data class PlanProjection(
    val current: BodyComposition,
    val goalBodyFatPercent: Double,
    val projectedGoalWeightKg: Double,
    val fatToLoseKg: Double,
    val weeklyRateKg: Double,
    val estimatedWeeks: Double,
    val dailyTarget: MacroTarget,
    val maintenanceCalories: Int,
    val dailyDeficit: Int,
    val warnings: List<PlanWarning>,
) {
    val estimatedDays: Int get() = kotlin.math.ceil(estimatedWeeks * 7).toInt()
    val isAchievable: Boolean get() = fatToLoseKg > 0 && estimatedWeeks > 0
}

/** What the engine knows about how the cut is actually going. */
data class AdaptiveState(
    val maintenanceCalories: Int,
    val trendWeightKg: Double,
    val weeklyChangeKg: Double,
    val confidence: TdeeConfidence,
    val warnings: List<PlanWarning>,
)

/**
 * FORMULA means nobody has logged enough for the engine to know anything yet, so
 * it is guessing from height and weight. The UI must not present a formula-based
 * maintenance number with the same certainty as a measured one.
 */
enum class TdeeConfidence { FORMULA, LOW, MODERATE, HIGH }
