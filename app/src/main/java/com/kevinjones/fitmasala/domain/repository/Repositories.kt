package com.kevinjones.fitmasala.domain.repository

import com.kevinjones.fitmasala.data.local.entity.BodyMetricEntity
import com.kevinjones.fitmasala.data.local.entity.LoggedMealEntity
import com.kevinjones.fitmasala.data.local.entity.Macros
import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.data.local.entity.PortionUnit
import com.kevinjones.fitmasala.data.local.relation.DailyMacroTotals
import com.kevinjones.fitmasala.data.local.relation.FrequentMeal
import com.kevinjones.fitmasala.domain.plan.AdaptiveState
import com.kevinjones.fitmasala.domain.plan.PlanAdjustment
import com.kevinjones.fitmasala.domain.plan.PlanProjection
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
    fun observeFrequentMeals(limit: Int = 12): Flow<List<FrequentMeal>>

    /** Re-logs a dish the user has eaten before, at a given portion count. */
    suspend fun logAgain(source: FrequentMeal, portions: Double, mealType: MealType): Long

    /** Logs a meal from an AI recipe. */
    suspend fun logRecipe(
        name: String,
        region: String,
        macros: Macros,
        portions: Double,
        mealType: MealType,
        sourceRecipeId: Long? = null,
    ): Long

    suspend fun logManual(
        name: String,
        mealType: MealType,
        portionQuantity: Double,
        portionUnit: PortionUnit,
        calories: Double,
        proteinG: Double,
        carbsG: Double,
        fatG: Double,
    ): Long

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
)

interface PlanRepository {
    fun observePlan(): Flow<PlanSnapshot>
    fun observeLatestMetric(): Flow<BodyMetricEntity?>
    suspend fun logWeight(weightKg: Double, waistCm: Double? = null, neckCm: Double? = null)
    suspend fun startCut(
        sex: String,
        heightCm: Double,
        ageYears: Int,
        activityLevel: String,
        aggression: String,
        startWeightKg: Double,
        startBodyFatPercent: Double,
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
