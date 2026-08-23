package com.kevinjones.fitmasala.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kevinjones.fitmasala.data.local.entity.BodyMetricEntity
import com.kevinjones.fitmasala.data.local.entity.PlanGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {

    /**
     * REPLACE is correct here and nowhere else in this project: `dayEpoch` is
     * uniquely indexed and re-weighing on the same morning should overwrite, not
     * append. Nothing holds a foreign key to body_metrics, so the id churn
     * REPLACE causes is harmless.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetric(metric: BodyMetricEntity): Long

    @Query("SELECT * FROM body_metrics WHERE dayEpoch = :dayEpoch")
    suspend fun metricForDay(dayEpoch: Long): BodyMetricEntity?

    @Query("SELECT * FROM body_metrics ORDER BY dayEpoch ASC")
    fun observeAllMetrics(): Flow<List<BodyMetricEntity>>

    /**
     * The trend window. Ordered ASC because the EMA has to run oldest-first —
     * feeding it newest-first produces a smoothed series that is quietly
     * backwards and a rate with the wrong sign.
     */
    @Query("SELECT * FROM body_metrics WHERE dayEpoch >= :fromDayEpoch ORDER BY dayEpoch ASC")
    fun observeMetricsSince(fromDayEpoch: Long): Flow<List<BodyMetricEntity>>

    @Query("SELECT * FROM body_metrics ORDER BY dayEpoch DESC LIMIT 1")
    fun observeLatestMetric(): Flow<BodyMetricEntity?>

    @Query("DELETE FROM body_metrics WHERE dayEpoch = :dayEpoch")
    suspend fun deleteMetricForDay(dayEpoch: Long)

    // --- Goals ---

    @Query("SELECT * FROM plan_goals WHERE isActive = 1 ORDER BY createdAt DESC LIMIT 1")
    fun observeActiveGoal(): Flow<PlanGoalEntity?>

    @Query("SELECT * FROM plan_goals WHERE isActive = 1 ORDER BY createdAt DESC LIMIT 1")
    suspend fun activeGoal(): PlanGoalEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGoal(goal: PlanGoalEntity): Long

    @Update
    suspend fun updateGoal(goal: PlanGoalEntity)

    @Query("UPDATE plan_goals SET isActive = 0 WHERE isActive = 1")
    suspend fun deactivateAllGoals()

    /**
     * Starting a new cut retires the old one rather than editing it. Editing in
     * place would rewrite the start weight, and every "down 6 kg since March"
     * figure derived from it.
     */
    @Transaction
    suspend fun startNewGoal(goal: PlanGoalEntity): Long {
        deactivateAllGoals()
        return insertGoal(goal.copy(isActive = true))
    }

    @Query(
        """
        UPDATE plan_goals
        SET cachedTargetCalories = :calories,
            cachedProteinG = :proteinG,
            cachedCarbsG = :carbsG,
            cachedFatG = :fatG,
            cachedMaintenanceCalories = :maintenance,
            cachedAtDayEpoch = :dayEpoch
        WHERE id = :goalId
        """,
    )
    suspend fun cacheTargets(
        goalId: Long,
        calories: Int,
        proteinG: Int,
        carbsG: Int,
        fatG: Int,
        maintenance: Int,
        dayEpoch: Long,
    )
}
