package com.kevinjones.fitmasala.domain.plan

import kotlin.math.roundToInt

/**
 * The plan manager's brain. Given where the body is now and what has been
 * logged, produce today's calorie and macro target plus an honest projection.
 *
 * Every input is a plain value and every method is pure, so a full recompute is
 * a handful of floating-point operations -- it runs on whatever thread asks it,
 * in well under a millisecond, and needs no coroutine, no WorkManager job and no
 * network. The only expensive thing in this feature is the vision call for photo
 * logging; none of the planning is.
 */
object PlanEngine {

    /** Never prescribe a deficit larger than this share of maintenance. */
    private const val MAX_DEFICIT_FRACTION = 0.25

    /** Absolute floors. Below these, adherence and micronutrients both collapse. */
    private const val MIN_CALORIES_MALE = 1500
    private const val MIN_CALORIES_FEMALE = 1200

    /**
     * Below roughly 15% (male), the same percentage rate strips more muscle,
     * because proportionally less fat is available to fund the deficit. The rate
     * gets tapered rather than held.
     */
    private const val LOW_BODY_FAT_THRESHOLD_MALE = 15.0
    private const val LOW_BODY_FAT_THRESHOLD_FEMALE = 23.0

    /**
     * Even a well-run cut loses some lean mass. Assuming perfect preservation
     * makes the goal weight look lower and the timeline shorter than they will
     * be -- wrong in the direction that disappoints. 10% of lost tissue is
     * realistic for a resistance-trained person eating enough protein.
     */
    private const val LEAN_MASS_LOSS_FRACTION = 0.10

    fun buildPlan(
        body: BodyComposition,
        sex: Sex,
        goalBodyFatPercent: Double,
        aggression: CutAggression,
        adaptive: AdaptiveState,
    ): PlanProjection {
        val warnings = mutableListOf<PlanWarning>()
        warnings += adaptive.warnings

        val maintenance = adaptive.maintenanceCalories

        val lowBfThreshold =
            if (sex == Sex.MALE) LOW_BODY_FAT_THRESHOLD_MALE else LOW_BODY_FAT_THRESHOLD_FEMALE
        var ratePct = aggression.weeklyRatePctBodyweight
        if (body.bodyFatPercent < lowBfThreshold) {
            ratePct *= 0.65
            warnings += PlanWarning.RATE_REDUCED_FOR_LOW_BODY_FAT
        }
        val requestedWeeklyRateKg = body.weightKg * (ratePct / 100.0)

        val rawDailyDeficit = (requestedWeeklyRateKg * AdaptiveTdee.KCAL_PER_KG) / 7.0
        val maxDeficit = maintenance * MAX_DEFICIT_FRACTION
        var dailyDeficit = rawDailyDeficit
        if (dailyDeficit > maxDeficit) {
            dailyDeficit = maxDeficit
            warnings += PlanWarning.DEFICIT_CAPPED_AT_TDEE_FRACTION
        }

        val floor = if (sex == Sex.MALE) MIN_CALORIES_MALE else MIN_CALORIES_FEMALE
        var targetCalories = (maintenance - dailyDeficit).roundToInt()
        if (targetCalories < floor) {
            targetCalories = floor
            dailyDeficit = (maintenance - floor).toDouble().coerceAtLeast(0.0)
            warnings += PlanWarning.CALORIES_AT_SAFETY_FLOOR
        }

        // The rate the plan will ACTUALLY produce after clamping, not the rate
        // that was asked for. Projecting from the requested rate is how an app
        // promises a date it has already made impossible.
        val effectiveWeeklyRateKg = (dailyDeficit * 7.0) / AdaptiveTdee.KCAL_PER_KG

        val goalWeightKg = projectGoalWeight(body, goalBodyFatPercent)
        val fatToLoseKg = body.weightKg - goalWeightKg
        if (fatToLoseKg <= 0) warnings += PlanWarning.ALREADY_AT_OR_BELOW_GOAL

        val weeks = if (fatToLoseKg > 0 && effectiveWeeklyRateKg > 0) {
            fatToLoseKg / effectiveWeeklyRateKg
        } else {
            0.0
        }

        val deficitFraction = if (maintenance > 0) dailyDeficit / maintenance else 0.0
        val (macros, macroWarnings) = MacroSolver.solve(targetCalories, body, deficitFraction)
        warnings += macroWarnings

        return PlanProjection(
            current = body,
            goalBodyFatPercent = goalBodyFatPercent,
            projectedGoalWeightKg = goalWeightKg,
            fatToLoseKg = fatToLoseKg,
            weeklyRateKg = effectiveWeeklyRateKg,
            estimatedWeeks = weeks,
            dailyTarget = macros,
            maintenanceCalories = maintenance,
            dailyDeficit = dailyDeficit.roundToInt(),
            warnings = warnings.distinct(),
        )
    }

    /**
     * Goal weight, accounting for the lean mass that comes off with the fat.
     *
     * Solves for the endpoint where fat is [goalBodyFatPercent] of total, given
     * that every kg lost is [LEAN_MASS_LOSS_FRACTION] lean. Naively holding lean
     * mass constant understates the goal weight by a kilo or two.
     */
    fun projectGoalWeight(body: BodyComposition, goalBodyFatPercent: Double): Double {
        val goalFraction = goalBodyFatPercent / 100.0
        if (goalFraction <= 0.0 || goalFraction >= 1.0) return body.weightKg

        // fatEnd  = fat0  - (1 - leanLoss) * L
        // leanEnd = lean0 - leanLoss * L
        // goal:     fatEnd = goalFraction * (weight0 - L)
        val fat0 = body.fatMassKg
        val lean0 = body.leanMassKg
        val numerator = fat0 - goalFraction * (fat0 + lean0)
        val denominator = (1 - LEAN_MASS_LOSS_FRACTION) - goalFraction
        if (denominator <= 0.0) return body.weightKg

        val lost = numerator / denominator
        return (body.weightKg - lost).coerceAtLeast(lean0)
    }

    /**
     * Compares the observed rate against the plan and says what to change.
     *
     * Only fires with a real trend behind it. Adjusting targets off four days of
     * scale noise produces a plan that oscillates and never settles -- which is
     * indistinguishable, from the inside, from a plan that does not work.
     */
    fun reviewProgress(
        plan: PlanProjection,
        adaptive: AdaptiveState,
    ): PlanAdjustment {
        if (adaptive.confidence == TdeeConfidence.FORMULA) {
            return PlanAdjustment(
                verdict = PlanVerdict.NOT_ENOUGH_DATA,
                suggestedCalorieDelta = 0,
                rationale = "Keep logging. After fourteen days of weights and meals " +
                    "the plan uses your numbers instead of a formula.",
            )
        }

        val observed = -adaptive.weeklyChangeKg // positive while losing
        val expected = plan.weeklyRateKg
        val ratio = if (expected > 0) observed / expected else 0.0

        return when {
            WeightTrend.isStalled(adaptive.weeklyChangeKg) -> PlanAdjustment(
                verdict = PlanVerdict.STALLED,
                suggestedCalorieDelta = -150,
                rationale = "Trend weight is flat across three weeks. Drop 150 kcal " +
                    "from carbs and hold two weeks before touching it again.",
            )
            ratio > 1.5 -> PlanAdjustment(
                verdict = PlanVerdict.TOO_FAST,
                suggestedCalorieDelta = +150,
                rationale = "Losing faster than planned. At this body fat the extra " +
                    "speed comes out of muscle -- add 150 kcal back.",
            )
            ratio < 0.5 -> PlanAdjustment(
                verdict = PlanVerdict.TOO_SLOW,
                suggestedCalorieDelta = -100,
                rationale = "Losing slower than planned. Before cutting further, check " +
                    "every meal is logged -- an unlogged snack looks exactly like a " +
                    "slow metabolism.",
            )
            else -> PlanAdjustment(
                verdict = PlanVerdict.ON_TRACK,
                suggestedCalorieDelta = 0,
                rationale = "Tracking as planned. Change nothing.",
            )
        }
    }
}

enum class PlanVerdict { ON_TRACK, TOO_FAST, TOO_SLOW, STALLED, NOT_ENOUGH_DATA }

data class PlanAdjustment(
    val verdict: PlanVerdict,
    val suggestedCalorieDelta: Int,
    val rationale: String,
)
