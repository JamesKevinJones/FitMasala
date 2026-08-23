package com.kevinjones.fitmasala.domain.plan

import kotlin.math.roundToInt

/** One day's logged intake. */
data class IntakeEntry(val dayEpoch: Long, val calories: Double, val mealCount: Int)

/**
 * Back-calculates real maintenance calories from what actually happened.
 *
 * This is the answer to photo-based logging being imprecise. A camera estimate
 * that runs 25% optimistic does not have to be fixed to be useful: if intake is
 * logged the *same wrong way* every day, the error lands entirely in the
 * maintenance figure and cancels out of the deficit. What the engine cares about
 * is that today's target is the number that produced last fortnight's observed
 * rate of loss.
 *
 * The failure mode this cannot survive is INCONSISTENT logging — photographing
 * lunch but not the evening chai and biscuits. Hence the sparse-logging warning:
 * a day with one logged meal is not a logged day, and including it would inflate
 * the apparent deficit and push the target lower and lower for no reason.
 */
object AdaptiveTdee {

    /** kcal per kg of body tissue lost. ~7700 is the standard mixed-tissue figure. */
    const val KCAL_PER_KG = 7700.0

    private const val MIN_DAYS_FOR_ADAPTIVE = 14
    private const val MIN_MEALS_FOR_A_VALID_DAY = 2

    fun estimate(
        weights: List<WeightEntry>,
        intake: List<IntakeEntry>,
        fallback: FormulaFallback,
        windowDays: Int = 21,
    ): AdaptiveState {
        val warnings = mutableListOf<PlanWarning>()

        val trendWeight = WeightTrend.currentTrendWeight(weights)
            ?: fallback.weightKg
        val weeklyChange = WeightTrend.weeklyChangeKg(weights, windowDays)

        val latestDay = weights.maxOfOrNull { it.dayEpoch }
        val cutoff = (latestDay ?: 0L) - windowDays
        val usableIntake = intake.filter { it.dayEpoch >= cutoff && it.mealCount >= MIN_MEALS_FOR_A_VALID_DAY }

        val formulaTdee = (fallback.basalMetabolicRate * fallback.activity.multiplier).roundToInt()

        // Not enough of a record yet — say so rather than dressing a formula
        // guess up as a measurement.
        if (weeklyChange == null || usableIntake.size < MIN_DAYS_FOR_ADAPTIVE) {
            warnings += PlanWarning.INSUFFICIENT_DATA_FOR_ADAPTIVE_TDEE
            if (usableIntake.isNotEmpty() && usableIntake.size < MIN_DAYS_FOR_ADAPTIVE) {
                warnings += PlanWarning.INTAKE_LOGGING_TOO_SPARSE
            }
            return AdaptiveState(
                maintenanceCalories = formulaTdee,
                trendWeightKg = trendWeight,
                weeklyChangeKg = weeklyChange ?: 0.0,
                confidence = TdeeConfidence.FORMULA,
                warnings = warnings,
            )
        }

        val avgIntake = usableIntake.sumOf { it.calories } / usableIntake.size
        // Losing weight means intake was BELOW maintenance by the energy that
        // came off. weeklyChange is negative on a cut, so this adds.
        val dailyEnergyFromTissue = -(weeklyChange * KCAL_PER_KG) / 7.0
        val adaptive = avgIntake + dailyEnergyFromTissue

        if (WeightTrend.isStalled(weeklyChange)) warnings += PlanWarning.WEIGHT_TREND_STALLED

        // Clamped against the formula estimate. A wild adaptive number almost
        // always means broken logging rather than an exotic metabolism, and
        // acting on it would set an unsafe target.
        val clamped = adaptive.coerceIn(formulaTdee * 0.65, formulaTdee * 1.45)

        val confidence = when {
            usableIntake.size >= 28 -> TdeeConfidence.HIGH
            usableIntake.size >= 21 -> TdeeConfidence.MODERATE
            else -> TdeeConfidence.LOW
        }

        return AdaptiveState(
            maintenanceCalories = clamped.roundToInt(),
            trendWeightKg = trendWeight,
            weeklyChangeKg = weeklyChange,
            confidence = confidence,
            warnings = warnings,
        )
    }

    /** What to use before there is any real data. */
    data class FormulaFallback(
        val weightKg: Double,
        val basalMetabolicRate: Double,
        val activity: ActivityLevel,
    )
}
