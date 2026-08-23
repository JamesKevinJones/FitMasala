package com.kevinjones.fitmasala.plan

import com.kevinjones.fitmasala.domain.plan.ActivityLevel
import com.kevinjones.fitmasala.domain.plan.AdaptiveTdee
import com.kevinjones.fitmasala.domain.plan.IntakeEntry
import com.kevinjones.fitmasala.domain.plan.PlanWarning
import com.kevinjones.fitmasala.domain.plan.TdeeConfidence
import com.kevinjones.fitmasala.domain.plan.WeightEntry
import com.kevinjones.fitmasala.domain.plan.WeightTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveTdeeTest {

    private val fallback = AdaptiveTdee.FormulaFallback(
        weightKg = 80.0,
        basalMetabolicRate = 1800.0,
        activity = ActivityLevel.MODERATE, // -> 2790 formula TDEE
    )

    /** 30 days losing a steady 0.5 kg/week from a start of 80 kg. */
    private fun weights(days: Int = 40, weeklyKg: Double = -0.5) =
        (0 until days).map { d ->
            WeightEntry(dayEpoch = 20_000L + d, weightKg = 80.0 + weeklyKg * (d / 7.0))
        }

    private fun intake(days: Int = 40, kcal: Double = 2200.0, meals: Int = 3) =
        (0 until days).map { d -> IntakeEntry(20_000L + d, kcal, meals) }

    @Test
    fun withoutEnoughDataItSaysSoInsteadOfGuessingConfidently() {
        val state = AdaptiveTdee.estimate(
            weights = weights(days = 5),
            intake = intake(days = 5),
            fallback = fallback,
        )

        assertEquals(TdeeConfidence.FORMULA, state.confidence)
        assertEquals(2790, state.maintenanceCalories)
        assertTrue(state.warnings.contains(PlanWarning.INSUFFICIENT_DATA_FOR_ADAPTIVE_TDEE))
    }

    @Test
    fun maintenanceIsBackCalculatedFromObservedLoss() {
        val state = AdaptiveTdee.estimate(
            weights = weights(),
            intake = intake(kcal = 2200.0),
            fallback = fallback,
            windowDays = 30,
        )

        // 0.5 kg/week = 3850 kcal/week = 550 kcal/day from tissue.
        // Maintenance therefore sits near 2200 + 550 = 2750.
        assertEquals(2750.0, state.maintenanceCalories.toDouble(), 60.0)
        assertTrue(state.confidence == TdeeConfidence.HIGH)
    }

    /**
     * The headline claim for photo logging: a camera that reads consistently low
     * still produces the right deficit, because the bias lands in maintenance
     * and cancels out.
     */
    @Test
    fun consistentUnderLoggingIsAbsorbedIntoTheMaintenanceFigure() {
        val honest = AdaptiveTdee.estimate(weights(), intake(kcal = 2200.0), fallback)
        // Same real intake, but every photo estimate reads 20% light.
        val underLogged = AdaptiveTdee.estimate(weights(), intake(kcal = 1760.0), fallback)

        val honestDeficit = honest.maintenanceCalories - 2200
        val underLoggedDeficit = underLogged.maintenanceCalories - 1760

        assertEquals(honestDeficit.toDouble(), underLoggedDeficit.toDouble(), 25.0)
    }

    @Test
    fun daysWithASingleLoggedMealDoNotCountAsLoggedDays() {
        val state = AdaptiveTdee.estimate(
            weights = weights(),
            intake = intake(meals = 1), // photographed lunch, nothing else
            fallback = fallback,
        )

        assertEquals(TdeeConfidence.FORMULA, state.confidence)
        assertTrue(state.warnings.contains(PlanWarning.INSUFFICIENT_DATA_FOR_ADAPTIVE_TDEE))
    }

    @Test
    fun anAbsurdAdaptiveResultIsClampedTowardTheFormula() {
        // Weight falling implausibly fast for the logged intake -- broken data.
        val state = AdaptiveTdee.estimate(
            weights = weights(weeklyKg = -3.0),
            intake = intake(kcal = 2200.0),
            fallback = fallback,
        )

        assertTrue(state.maintenanceCalories <= kotlin.math.round(2790 * 1.45).toInt())
    }

    @Test
    fun trendIgnoresASingleSaltyDay() {
        val clean = weights(days = 21).toMutableList()
        val spiked = clean.toMutableList().also {
            it[15] = it[15].copy(weightKg = it[15].weightKg + 1.8)
        }

        val cleanRate = WeightTrend.weeklyChangeKg(clean)!!
        val spikedRate = WeightTrend.weeklyChangeKg(spiked)!!

        // One 1.8 kg water swing must not move the weekly rate by more than
        // a fraction of a kilo, or the plan will chase noise.
        assertTrue(kotlin.math.abs(cleanRate - spikedRate) < 0.25)
    }
}
