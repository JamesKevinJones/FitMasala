package com.kevinjones.fitmasala.domain.repository

import com.kevinjones.fitmasala.data.local.entity.BodyMetricEntity
import com.kevinjones.fitmasala.data.local.entity.LoggedMealEntity
import com.kevinjones.fitmasala.data.local.entity.Macros
import com.kevinjones.fitmasala.data.local.entity.MealSource
import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.data.local.entity.PortionUnit
import com.kevinjones.fitmasala.data.local.relation.DailyMacroTotals
import com.kevinjones.fitmasala.data.local.relation.FrequentMeal
import com.kevinjones.fitmasala.domain.plan.ActivityLevel
import com.kevinjones.fitmasala.domain.plan.AdaptiveState
import com.kevinjones.fitmasala.domain.plan.CutAggression
import com.kevinjones.fitmasala.domain.plan.PlanAdjustment
import com.kevinjones.fitmasala.domain.plan.PlanProjection
import com.kevinjones.fitmasala.domain.plan.Sex
import com.kevinjones.fitmasala.domain.plan.TapeMeasurements
import com.kevinjones.fitmasala.domain.plan.WeightEntry
import kotlinx.coroutines.flow.Flow

/**
 * Repository interfaces live in `domain` and are implemented in `data`, so the
 * ViewModels depend on the shape of the data rather than on Room.
 *
 * Everything observable returns a Flow. Nothing here exposes an entity the UI
 * has to interpret - a screen should never have to know that `dayEpoch` exists.
 */
interface MealRepository {
    fun observeToday(): Flow<List<LoggedMealEntity>>
    fun observeTodayTotals(): Flow<DailyMacroTotals>
    /** Any day's totals: a photo from earlier lands on the day it was taken. */
    fun observeDayTotals(dayEpoch: Long): Flow<DailyMacroTotals>
    fun observeFrequentMeals(limit: Int = 12): Flow<List<FrequentMeal>>

    /** Re-logs a dish the user has eaten before, at a given portion count. */
    suspend fun logAgain(source: FrequentMeal, portions: Double, mealType: MealType): Long

    /** Logs a meal from an AI recipe; [estimateModel] is the model that wrote it. */
    suspend fun logRecipe(
        name: String,
        region: String,
        macros: Macros,
        portions: Double,
        mealType: MealType,
        sourceRecipeId: Long? = null,
        estimateModel: String? = null,
    ): Long

    /**
     * One Dish typed by hand - not an Estimate. [eatenAt] defaults to now; a
     * photo whose estimate failed passes its own time and [photoPath], so the
     * meal lands on the day it was eaten and keeps its audit photo.
     */
    suspend fun logManual(
        name: String,
        mealType: MealType,
        portionQuantity: Double,
        portionUnit: PortionUnit,
        calories: Double,
        proteinG: Double,
        carbsG: Double,
        fatG: Double,
        eatenAt: Long = System.currentTimeMillis(),
        photoPath: String? = null,
        fiberG: Double = 0.0,
        /** MANUAL for typed numbers, BARCODE for a label from Open Food Facts. Never an Estimate. */
        source: MealSource = MealSource.MANUAL,
    ): Long

    /**
     * Logs the Dishes of one photographed Meal together. The rows arrive fully
     * built by the photo mapper, so what the review sheet showed is what is
     * written.
     */
    suspend fun logPhotoMeal(dishes: List<LoggedMealEntity>): List<Long>

    /** Whether a photo meal is already logged at exactly [eatenAt] - the duplicate check. */
    suspend fun hasPhotoMealAt(eatenAt: Long): Boolean

    suspend fun delete(id: Long)
}

/**
 * The screen-ready view of the cut. Combining the raw inputs here rather than in
 * a ViewModel means the same snapshot backs the dashboard, the plan screen and
 * anything added later, and the `PlanEngine` is called from exactly one place.
 */
data class PlanSnapshot(
    val projection: PlanProjection?,
    val adaptive: AdaptiveState?,
    val adjustment: PlanAdjustment?,
    val weights: List<WeightEntry>,
    val trend: List<WeightEntry>,
    /** True until a goal has been set - the screens show onboarding instead. */
    val needsSetup: Boolean,
    /** The day the active cut began, for "week N of the cut"; null without one. */
    val startedAtDayEpoch: Long? = null,
    /** The active cut's sex, so the weigh-in form knows whether hip is part of the tape. */
    val sex: Sex? = null,
    /** The active cut's settings, for the edit and restart forms; null without one. */
    val cut: ActiveCut? = null,
)

/** The active cut as the forms need it: what it started from and what it aims at. */
data class ActiveCut(
    val sex: Sex,
    val heightCm: Double,
    val ageYears: Int,
    val activity: ActivityLevel,
    val aggression: CutAggression,
    val goalBodyFatPercent: Double,
    val startWeightKg: Double,
    val startBodyFatPercent: Double,
    val startedAtDayEpoch: Long,
)

interface PlanRepository {
    fun observePlan(): Flow<PlanSnapshot>
    fun observeLatestMetric(): Flow<BodyMetricEntity?>
    suspend fun logWeight(
        weightKg: Double,
        waistCm: Double? = null,
        neckCm: Double? = null,
        hipCm: Double? = null,
    )

    /**
     * Starts a new cut, superseding any active one, and seeds its first weigh-in -
     * with the tape, when the start came from one, so that reading is on record.
     */
    suspend fun startCut(
        sex: String,
        heightCm: Double,
        ageYears: Int,
        activityLevel: String,
        aggression: String,
        startWeightKg: Double,
        startBodyFatPercent: Double,
        goalBodyFatPercent: Double,
        tape: TapeMeasurements? = null,
    )

    /**
     * Changes where the active cut is heading - goal, pace, activity, age - without
     * touching where it started. The old settings are retired, not overwritten, so
     * the start weight, start date and history stay as they were.
     */
    suspend fun updateCut(
        ageYears: Int,
        activityLevel: String,
        aggression: String,
        goalBodyFatPercent: Double,
    )
}

/** Streak and XP, derived from logged data rather than stored as a counter. */
data class ProgressSnapshot(
    val currentStreak: Int,
    val longestStreak: Int,
    val streakAtRisk: Boolean,
    val isMilestone: Boolean,
    val totalXp: Int,
    val level: Int,
    val xpToNextLevel: Int,
    val levelProgress: Float,
)

interface ProgressRepository {
    fun observeProgress(): Flow<ProgressSnapshot>
}
