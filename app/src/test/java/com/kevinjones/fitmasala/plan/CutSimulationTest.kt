package com.kevinjones.fitmasala.plan

import com.kevinjones.fitmasala.domain.plan.ActivityLevel
import com.kevinjones.fitmasala.domain.plan.AdaptiveTdee
import com.kevinjones.fitmasala.domain.plan.BodyComposition
import com.kevinjones.fitmasala.domain.plan.BodyFatEstimator
import com.kevinjones.fitmasala.domain.plan.BodyFatSource
import com.kevinjones.fitmasala.domain.plan.CutAggression
import com.kevinjones.fitmasala.domain.plan.IntakeEntry
import com.kevinjones.fitmasala.domain.plan.PlanEngine
import com.kevinjones.fitmasala.domain.plan.PlanVerdict
import com.kevinjones.fitmasala.domain.plan.Sex
import com.kevinjones.fitmasala.domain.plan.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * End-to-end simulation of a real cut.
 *
 * A simulated body with a KNOWN true TDEE runs for months. Each day it eats what
 * the plan prescribes, its weight moves by real physiology, and a noisy scale
 * reading goes back into the engine. Every week the plan is recomputed from that
 * logged history alone.
 *
 * Nothing is mocked - the same PlanEngine, AdaptiveTdee, WeightTrend and
 * MacroSolver the app ships drive the loop. The unit tests prove each piece in
 * isolation; this proves the FEEDBACK LOOP is stable, which is the property that
 * actually matters and the one no unit test can show.
 */
class CutSimulationTest {

    private val startWeight = 85.0
    private val startBodyFat = 22.0

    /** The simulated person's real maintenance. The engine is never told this. */
    private fun trueTdee(weightKg: Double) = 34.0 * weightKg

    private class Sim {
        val weights = mutableListOf<WeightEntry>()
        val intake = mutableListOf<IntakeEntry>()
        var trueWeight = 0.0
        var leanMass = 0.0
        val bodyFatPercent: Double get() = ((trueWeight - leanMass) / trueWeight) * 100.0
    }

    private fun Sim.adaptive() = AdaptiveTdee.estimate(
        weights = weights,
        intake = intake,
        fallback = AdaptiveTdee.FormulaFallback(
            weightKg = trueWeight,
            basalMetabolicRate = BodyFatEstimator.basalMetabolicRateFromLeanMass(leanMass),
            activity = ActivityLevel.MODERATE,
        ),
    )

    private fun Sim.plan(aggression: CutAggression = CutAggression.STANDARD) =
        PlanEngine.buildPlan(
            body = BodyComposition(trueWeight, bodyFatPercent, BodyFatSource.NAVY_TAPE),
            sex = Sex.MALE,
            goalBodyFatPercent = 12.0,
            aggression = aggression,
            adaptive = adaptive(),
        )

    /**
     * @param logBias 1.0 = perfect logging. 0.85 = every estimate reads 15%
     *   light, which is roughly what photo-based logging actually looks like.
     */
    private fun runCut(
        weeks: Int,
        logBias: Double,
        aggression: CutAggression = CutAggression.STANDARD,
        seed: Int = 20260822, // seeded: the simulation must be reproducible
    ): Sim {
        val rng = Random(seed)
        val sim = Sim().apply {
            trueWeight = startWeight
            leanMass = startWeight * (1 - startBodyFat / 100.0)
        }

        var day = 20_000L
        var target = 2300 // seed value for week 1, before any history exists

        repeat(weeks) {
            repeat(7) {
                // The person eats until their LOGGED total hits the target, so
                // real intake is inflated by exactly the logging bias.
                val realIntake = target / logBias
                val tdee = trueTdee(sim.trueWeight)
                val deltaKg = (realIntake - tdee) / AdaptiveTdee.KCAL_PER_KG

                sim.trueWeight += deltaKg
                // 10% of lost tissue is lean, matching the engine's assumption.
                if (deltaKg < 0) sim.leanMass += deltaKg * 0.10

                // Scale noise: water, sodium, gut content. Roughly +/- 0.8kg.
                val observed = sim.trueWeight + (rng.nextDouble() - 0.5) * 1.6
                sim.weights += WeightEntry(day, observed)
                sim.intake += IntakeEntry(day, target.toDouble(), mealCount = 3)
                day++
            }
            target = sim.plan(aggression).dailyTarget.calories
        }
        return sim
    }

    @Test
    fun twentyWeeksOfHonestLoggingMovesTowardTheGoal() {
        val sim = runCut(weeks = 20, logBias = 1.0)
        println(
            "honest logging: %.1fkg @ %.1f%% -> %.1fkg @ %.1f%%".format(
                startWeight, startBodyFat, sim.trueWeight, sim.bodyFatPercent,
            ),
        )

        assertTrue("should have lost weight", sim.trueWeight < startWeight)
        assertTrue("body fat should have fallen", sim.bodyFatPercent < startBodyFat)
        assertTrue("expected under 16% body fat, got ${sim.bodyFatPercent}", sim.bodyFatPercent < 16.0)
        // And it must not run away into starvation territory.
        assertTrue("lost implausibly fast", sim.trueWeight > 68.0)
    }

    /**
     * The headline claim for photo logging, tested end to end rather than
     * asserted in a comment: a consistent estimation bias cancels out of the
     * deficit because it lands entirely in the computed maintenance figure.
     */
    @Test
    fun consistentUnderLoggingLandsInTheSamePlaceAsHonestLogging() {
        val honest = runCut(weeks = 16, logBias = 1.0)
        val biased = runCut(weeks = 16, logBias = 0.85)

        println("honest:          %.1fkg @ %.1f%%".format(honest.trueWeight, honest.bodyFatPercent))
        println("15%% under-logged: %.1fkg @ %.1f%%".format(biased.trueWeight, biased.bodyFatPercent))

        // Within a kilo after four months, despite every meal logged 15% light.
        assertEquals(honest.trueWeight, biased.trueWeight, 1.0)
        assertEquals(honest.bodyFatPercent, biased.bodyFatPercent, 1.0)
    }

    @Test
    fun theEngineLearnsTheMaintenanceItWasNeverTold() {
        val sim = runCut(weeks = 12, logBias = 1.0)
        val learned = sim.adaptive().maintenanceCalories
        val actual = trueTdee(sim.trueWeight)

        println("true TDEE ${actual.toInt()} vs learned $learned")
        assertTrue(
            "learned $learned vs true ${actual.toInt()}",
            abs(learned - actual) / actual < 0.10,
        )
    }

    @Test
    fun aSteadyCutIsNeverToldToPanic() {
        val sim = runCut(weeks = 14, logBias = 1.0)
        val review = PlanEngine.reviewProgress(sim.plan(), sim.adaptive())

        println("verdict: ${review.verdict} - ${review.rationale}")
        assertTrue(
            "a steady cut should not be told to cut harder, got ${review.verdict}",
            review.verdict == PlanVerdict.ON_TRACK || review.verdict == PlanVerdict.TOO_SLOW,
        )
    }

    @Test
    fun proteinAndFatHoldUpAcrossTheWholeCut() {
        val sim = runCut(weeks = 18, logBias = 1.0)
        val t = sim.plan().dailyTarget

        println("final target: ${t.calories} kcal / ${t.proteinG}P ${t.carbsG}C ${t.fatG}F")

        // At the leanest point, protein per kg of lean mass must not sag.
        assertTrue("protein collapsed to ${t.proteinG}g", t.proteinG >= sim.leanMass * 2.2 - 2)
        assertTrue("fat below the hormonal floor", t.fatG >= sim.trueWeight * 0.6 - 2)
        assertTrue("carbs went negative", t.carbsG >= 0)

        val leanLost = startWeight * (1 - startBodyFat / 100.0) - sim.leanMass
        println("lean mass lost over 18 weeks: %.1fkg".format(leanLost))
        assertTrue("lost too much lean mass: $leanLost kg", leanLost < 4.0)
    }

    /** An aggressive setting must still respect the floors, not blow through them. */
    @Test
    fun aggressiveCutStillRespectsTheSafetyFloors() {
        val sim = runCut(weeks = 16, logBias = 1.0, aggression = CutAggression.AGGRESSIVE)
        val plan = sim.plan(CutAggression.AGGRESSIVE)

        println("aggressive: %.1fkg @ %.1f%%, target ${plan.dailyTarget.calories} kcal"
            .format(sim.trueWeight, sim.bodyFatPercent))

        assertTrue("dropped below the calorie floor", plan.dailyTarget.calories >= 1500)
        assertTrue("deficit exceeded 25% of maintenance",
            plan.dailyDeficit <= plan.maintenanceCalories * 0.25 + 1)
    }
}
