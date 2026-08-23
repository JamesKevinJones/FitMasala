package com.kevinjones.fitmasala.data.repository

import com.kevinjones.fitmasala.core.util.DateKeys
import com.kevinjones.fitmasala.data.local.dao.MealDao
import com.kevinjones.fitmasala.data.local.dao.PlanDao
import com.kevinjones.fitmasala.data.local.entity.BodyMetricEntity
import com.kevinjones.fitmasala.data.local.entity.PlanGoalEntity
import com.kevinjones.fitmasala.data.local.relation.DailyCalories
import com.kevinjones.fitmasala.data.local.relation.DayMealCount
import com.kevinjones.fitmasala.domain.plan.ActivityLevel
import com.kevinjones.fitmasala.domain.plan.AdaptiveTdee
import com.kevinjones.fitmasala.domain.plan.BodyComposition
import com.kevinjones.fitmasala.domain.plan.BodyFatEstimator
import com.kevinjones.fitmasala.domain.plan.BodyFatSource
import com.kevinjones.fitmasala.domain.plan.CutAggression
import com.kevinjones.fitmasala.domain.plan.IntakeEntry
import com.kevinjones.fitmasala.domain.plan.PlanEngine
import com.kevinjones.fitmasala.domain.plan.Sex
import com.kevinjones.fitmasala.domain.plan.TapeMeasurements
import com.kevinjones.fitmasala.domain.plan.WeightEntry
import com.kevinjones.fitmasala.domain.plan.WeightTrend
import com.kevinjones.fitmasala.domain.repository.PlanRepository
import com.kevinjones.fitmasala.domain.repository.PlanSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the database meets the plan engine.
 *
 * A `combine` of four flows - the goal, the weigh-ins, daily intake, and how
 * many meals each day carries - recomputed into one snapshot. Every weigh-in and
 * every logged meal re-derives the target, which is what "adaptive" means here:
 * nothing is stored as a cached number that can drift out of step with the data
 * behind it.
 *
 * Recomputing on every emission is deliberate. PlanEngine is pure arithmetic
 * over a few dozen values, so there is no cache to invalidate and no stale state
 * to reason about.
 */
@Singleton
class PlanRepositoryImpl @Inject constructor(
    private val planDao: PlanDao,
    private val mealDao: MealDao,
) : PlanRepository {

    /** Long enough for a least-squares trend, short enough to stay current. */
    private val windowDays = 42L

    override fun observePlan(): Flow<PlanSnapshot> {
        val from = DateKeys.daysAgo(windowDays)
        return combine(
            planDao.observeActiveGoal(),
            planDao.observeMetricsSince(from),
            mealDao.observeDailyIntake(from),
            mealDao.observeDayMealCounts(from),
        ) { goal, metrics, intake, mealCounts ->
            buildSnapshot(goal, metrics, intake, mealCounts)
        }
    }

    private fun buildSnapshot(
        goal: PlanGoalEntity?,
        metrics: List<BodyMetricEntity>,
        intake: List<DailyCalories>,
        mealCounts: List<DayMealCount>,
    ): PlanSnapshot {
        val weights = metrics.map { WeightEntry(it.dayEpoch, it.weightKg) }
        val trend = WeightTrend.smooth(weights)

        if (goal == null || weights.isEmpty()) {
            return PlanSnapshot(null, null, null, weights, trend, needsSetup = true)
        }

        val currentWeight = trend.lastOrNull()?.weightKg ?: metrics.last().weightKg

        // Body fat: the most recent measured value, else carried forward from
        // the start of the cut. Carrying forward drifts over time, but far less
        // than refusing to show a plan at all.
        val bodyFat = metrics.lastOrNull { it.bodyFatPercent != null }?.bodyFatPercent
            ?: goal.startBodyFatPercent
        val body = BodyComposition(
            weightKg = currentWeight,
            bodyFatPercent = bodyFat,
            source = sourceOf(metrics.lastOrNull { it.bodyFatSource != null }?.bodyFatSource),
        )

        // Meal counts joined onto intake, so AdaptiveTdee can discard days that
        // were only partly logged.
        val countsByDay = mealCounts.associate { it.dayEpoch to it.mealCount }
        val intakeEntries = intake.map {
            IntakeEntry(it.dayEpoch, it.calories, countsByDay[it.dayEpoch] ?: 0)
        }

        val sex = Sex.entries.firstOrNull { it.name == goal.sex } ?: Sex.MALE
        val activity = ActivityLevel.entries.firstOrNull { it.name == goal.activityLevel }
            ?: ActivityLevel.MODERATE
        val aggression = CutAggression.entries.firstOrNull { it.name == goal.aggression }
            ?: CutAggression.STANDARD

        val adaptive = AdaptiveTdee.estimate(
            weights = weights,
            intake = intakeEntries,
            fallback = AdaptiveTdee.FormulaFallback(
                weightKg = currentWeight,
                // Katch-McArdle once body fat is known: Mifflin systematically
                // over-estimates for a lean, muscular person - exactly who is
                // heading for 12%.
                basalMetabolicRate =
                    BodyFatEstimator.basalMetabolicRateFromLeanMass(body.leanMassKg),
                activity = activity,
            ),
        )

        val projection = PlanEngine.buildPlan(
            body = body,
            sex = sex,
            goalBodyFatPercent = goal.goalBodyFatPercent,
            aggression = aggression,
            adaptive = adaptive,
        )

        return PlanSnapshot(
            projection = projection,
            adaptive = adaptive,
            adjustment = PlanEngine.reviewProgress(projection, adaptive),
            weights = weights,
            trend = trend,
            needsSetup = false,
        )
    }

    override fun observeLatestMetric(): Flow<BodyMetricEntity?> = planDao.observeLatestMetric()

    override suspend fun logWeight(weightKg: Double, waistCm: Double?, neckCm: Double?) {
        val now = System.currentTimeMillis()
        val goal = planDao.activeGoal()

        // Derive body fat only from a COMPLETE tape set. Otherwise leave it null
        // so the last real measurement stands, rather than being overwritten by
        // a guess derived from partial input.
        val bodyFat = if (waistCm != null && neckCm != null && goal != null) {
            BodyFatEstimator.navy(
                sex = Sex.entries.firstOrNull { it.name == goal.sex } ?: Sex.MALE,
                m = TapeMeasurements(
                    heightCm = goal.heightCm,
                    neckCm = neckCm,
                    waistCm = waistCm,
                ),
            )
        } else {
            null
        }

        planDao.upsertMetric(
            BodyMetricEntity(
                dayEpoch = DateKeys.dayEpochOf(now),
                recordedAt = now,
                weightKg = weightKg,
                waistCm = waistCm,
                neckCm = neckCm,
                bodyFatPercent = bodyFat,
                bodyFatSource = bodyFat?.let { BodyFatSource.NAVY_TAPE.name },
            ),
        )
    }

    override suspend fun startCut(
        sex: String,
        heightCm: Double,
        ageYears: Int,
        activityLevel: String,
        aggression: String,
        startWeightKg: Double,
        startBodyFatPercent: Double,
        goalBodyFatPercent: Double,
    ) {
        planDao.startNewGoal(
            PlanGoalEntity(
                goalBodyFatPercent = goalBodyFatPercent,
                aggression = aggression,
                sex = sex,
                activityLevel = activityLevel,
                heightCm = heightCm,
                ageYears = ageYears,
                startWeightKg = startWeightKg,
                startBodyFatPercent = startBodyFatPercent,
                startedAtDayEpoch = DateKeys.today(),
            ),
        )
        // Seed the first weigh-in, so a new plan has a one-point trend instead
        // of an empty chart on the day it is created.
        logWeight(startWeightKg)
    }

    private fun sourceOf(raw: String?): BodyFatSource =
        BodyFatSource.entries.firstOrNull { it.name == raw } ?: BodyFatSource.ESTIMATED
}
