package com.kevinjones.fitmasala.plan

import com.kevinjones.fitmasala.domain.plan.ActivityLevel
import com.kevinjones.fitmasala.domain.plan.AdaptiveState
import com.kevinjones.fitmasala.domain.plan.BodyComposition
import com.kevinjones.fitmasala.domain.plan.BodyFatSource
import com.kevinjones.fitmasala.domain.plan.CutAggression
import com.kevinjones.fitmasala.domain.plan.PlanEngine
import com.kevinjones.fitmasala.domain.plan.PlanWarning
import com.kevinjones.fitmasala.domain.plan.Sex
import com.kevinjones.fitmasala.domain.plan.TdeeConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanEngineTest {

    private fun adaptive(maintenance: Int, weeklyChange: Double = -0.6) = AdaptiveState(
        maintenanceCalories = maintenance,
        trendWeightKg = 80.0,
        weeklyChangeKg = weeklyChange,
        confidence = TdeeConfidence.HIGH,
        warnings = emptyList(),
    )

    private fun body(weight: Double, bf: Double) =
        BodyComposition(weight, bf, BodyFatSource.NAVY_TAPE)

    @Test
    fun goalWeightAccountsForLeanMassLostAlongTheWay() {
        val start = body(weight = 85.0, bf = 22.0) // 18.7 fat / 66.3 lean
        val goal = PlanEngine.projectGoalWeight(start, goalBodyFatPercent = 12.0)

        // Naive "lean preserved" maths gives 66.3 / 0.88 = 75.3 kg. Because 10%
        // of the loss is lean, the real endpoint must be LOWER than that.
        assertTrue("expected below the lean-preserved estimate, got $goal", goal < 75.3)
        assertTrue(goal > 70.0)
    }

    @Test
    fun projectionUsesTheClampedRateNotTheRequestedOne() {
        // Small person, aggressive setting: the 25%-of-maintenance cap will bite.
        val plan = PlanEngine.buildPlan(
            body = body(weight = 60.0, bf = 25.0),
            sex = Sex.MALE,
            goalBodyFatPercent = 12.0,
            aggression = CutAggression.AGGRESSIVE,
            adaptive = adaptive(maintenance = 2000),
        )

        assertTrue(plan.warnings.contains(PlanWarning.DEFICIT_CAPPED_AT_TDEE_FRACTION))
        // Deficit capped at 500 => 0.4545 kg/week, not the requested 0.6.
        assertEquals(500, plan.dailyDeficit)
        assertEquals(0.4545, plan.weeklyRateKg, 0.01)
        // And the timeline must be derived from the rate that will actually happen.
        val impliedWeeks = plan.fatToLoseKg / plan.weeklyRateKg
        assertEquals(impliedWeeks, plan.estimatedWeeks, 0.001)
    }

    @Test
    fun caloriesNeverGoBelowTheSafetyFloor() {
        val plan = PlanEngine.buildPlan(
            body = body(weight = 55.0, bf = 14.0),
            sex = Sex.MALE,
            goalBodyFatPercent = 12.0,
            aggression = CutAggression.AGGRESSIVE,
            adaptive = adaptive(maintenance = 1700),
        )

        assertTrue(plan.dailyTarget.calories >= 1500)
        assertTrue(plan.warnings.contains(PlanWarning.CALORIES_AT_SAFETY_FLOOR))
    }

    @Test
    fun rateIsTaperedOnceBodyFatIsAlreadyLow() {
        val lean = PlanEngine.buildPlan(
            body = body(weight = 75.0, bf = 13.0),
            sex = Sex.MALE,
            goalBodyFatPercent = 12.0,
            aggression = CutAggression.STANDARD,
            adaptive = adaptive(maintenance = 2800),
        )
        val fatter = PlanEngine.buildPlan(
            body = body(weight = 75.0, bf = 24.0),
            sex = Sex.MALE,
            goalBodyFatPercent = 12.0,
            aggression = CutAggression.STANDARD,
            adaptive = adaptive(maintenance = 2800),
        )

        assertTrue(lean.warnings.contains(PlanWarning.RATE_REDUCED_FOR_LOW_BODY_FAT))
        assertFalse(fatter.warnings.contains(PlanWarning.RATE_REDUCED_FOR_LOW_BODY_FAT))
        assertTrue(lean.weeklyRateKg < fatter.weeklyRateKg)
    }

    @Test
    fun proteinIsSetFromLeanMassAndSurvivesTheDeficit() {
        val plan = PlanEngine.buildPlan(
            body = body(weight = 85.0, bf = 22.0),
            sex = Sex.MALE,
            goalBodyFatPercent = 12.0,
            aggression = CutAggression.STANDARD,
            adaptive = adaptive(maintenance = 2900),
        )
        val lean = 85.0 * 0.78

        // At least the 2.2 g/kg LBM base, and never pushed aside to fit carbs.
        assertTrue(plan.dailyTarget.proteinG >= (lean * 2.2).toInt() - 1)
        assertTrue(plan.dailyTarget.fatG >= (85.0 * 0.6).toInt() - 1)
        assertTrue(plan.dailyTarget.carbsG >= 0)
    }

    @Test
    fun macrosAddUpToTheCalorieTarget() {
        val plan = PlanEngine.buildPlan(
            body = body(weight = 78.0, bf = 18.0),
            sex = Sex.MALE,
            goalBodyFatPercent = 12.0,
            aggression = CutAggression.CONSERVATIVE,
            adaptive = adaptive(maintenance = 2600),
        )
        val t = plan.dailyTarget
        val summed = t.proteinKcal + t.carbKcal + t.fatKcal

        // Rounding to whole grams costs a few kcal; more than 30 is a bug.
        assertTrue("macros summed to $summed vs target ${t.calories}",
            kotlin.math.abs(summed - t.calories) <= 30)
    }

    @Test
    fun beingAlreadyLeanerThanGoalIsFlaggedNotProjected() {
        val plan = PlanEngine.buildPlan(
            body = body(weight = 70.0, bf = 10.0),
            sex = Sex.MALE,
            goalBodyFatPercent = 12.0,
            aggression = CutAggression.STANDARD,
            adaptive = adaptive(maintenance = 2600),
        )

        assertTrue(plan.warnings.contains(PlanWarning.ALREADY_AT_OR_BELOW_GOAL))
        assertFalse(plan.isAchievable)
        assertEquals(0.0, plan.estimatedWeeks, 0.001)
    }
}
