package com.kevinjones.fitmasala.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kevinjones.fitmasala.data.local.entity.LoggedMealEntity
import com.kevinjones.fitmasala.data.local.relation.DailyCalories
import com.kevinjones.fitmasala.data.local.relation.DailyMacroTotals
import com.kevinjones.fitmasala.data.local.relation.DayMealCount
import com.kevinjones.fitmasala.data.local.relation.FrequentMeal
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(meal: LoggedMealEntity): Long

    @Update
    suspend fun update(meal: LoggedMealEntity)

    @Delete
    suspend fun delete(meal: LoggedMealEntity)

    @Query("SELECT * FROM logged_meals WHERE id = :id")
    suspend fun byId(id: Long): LoggedMealEntity?

    @Query("SELECT * FROM logged_meals WHERE dayEpoch = :dayEpoch ORDER BY eatenAt ASC")
    fun observeDay(dayEpoch: Long): Flow<List<LoggedMealEntity>>

    /**
     * COALESCE, not nullable columns: an empty day must return a zeroed row, not
     * a row of nulls that every caller then has to defend against.
     */
    @Query(
        """
        SELECT
            COALESCE(SUM(calories), 0.0) AS calories,
            COALESCE(SUM(proteinG), 0.0) AS proteinG,
            COALESCE(SUM(carbsG),   0.0) AS carbsG,
            COALESCE(SUM(fatG),     0.0) AS fatG,
            COALESCE(SUM(fiberG),   0.0) AS fiberG,
            COUNT(*) AS mealCount
        FROM logged_meals
        WHERE dayEpoch = :dayEpoch
        """,
    )
    fun observeDayTotals(dayEpoch: Long): Flow<DailyMacroTotals>

    @Query(
        """
        SELECT dayEpoch, COALESCE(SUM(calories), 0.0) AS calories
        FROM logged_meals
        WHERE dayEpoch BETWEEN :fromDayEpoch AND :toDayEpoch
        GROUP BY dayEpoch
        ORDER BY dayEpoch ASC
        """,
    )
    fun observeCalorieTrend(fromDayEpoch: Long, toDayEpoch: Long): Flow<List<DailyCalories>>

    @Query("SELECT * FROM logged_meals ORDER BY eatenAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<LoggedMealEntity>>

    /** Backs "log this again" — the same dish, re-eaten, without re-asking the AI. */
    @Query(
        """
        SELECT * FROM logged_meals
        WHERE name LIKE '%' || :query || '%' OR nameLocal LIKE '%' || :query || '%'
        GROUP BY name
        ORDER BY eatenAt DESC
        LIMIT :limit
        """,
    )
    suspend fun searchDistinctByName(query: String, limit: Int = 20): List<LoggedMealEntity>

    /**
     * The Chef tab's whole premise: most of what anyone eats, they have eaten
     * before.
     *
     * Averages the macros rather than taking the most recent, so one unusually
     * large portion does not become the default for every future log. Ordered by
     * frequency first and recency second - a dish eaten forty times last month
     * should outrank one eaten twice yesterday.
     */
    @Query(
        """
        SELECT
            name AS name,
            MAX(nameLocal) AS nameLocal,
            MAX(region) AS region,
            MAX(portionUnit) AS portionUnit,
            COUNT(*) AS timesLogged,
            MAX(eatenAt) AS lastEatenAt,
            AVG(calories) AS avgCalories,
            AVG(proteinG) AS avgProteinG,
            AVG(portionQuantity) AS avgPortionQuantity
        FROM logged_meals
        GROUP BY name
        ORDER BY timesLogged DESC, lastEatenAt DESC
        LIMIT :limit
        """,
    )
    fun observeFrequentMeals(limit: Int = 12): Flow<List<FrequentMeal>>

    /**
     * Days and how many meals each carries. Feeds the streak, which only counts
     * a day once it clears the same two-meal bar AdaptiveTdee needs - so the
     * filtering happens in the caller, not here.
     */
    @Query(
        """
        SELECT dayEpoch AS dayEpoch, COUNT(*) AS mealCount
        FROM logged_meals
        WHERE dayEpoch >= :fromDayEpoch
        GROUP BY dayEpoch
        ORDER BY dayEpoch ASC
        """,
    )
    fun observeDayMealCounts(fromDayEpoch: Long): Flow<List<DayMealCount>>

    /** Daily calorie intake for the adaptive-TDEE window. */
    @Query(
        """
        SELECT dayEpoch AS dayEpoch, COALESCE(SUM(calories), 0.0) AS calories
        FROM logged_meals
        WHERE dayEpoch >= :fromDayEpoch
        GROUP BY dayEpoch
        ORDER BY dayEpoch ASC
        """,
    )
    fun observeDailyIntake(fromDayEpoch: Long): Flow<List<DailyCalories>>

    @Query("DELETE FROM logged_meals WHERE id = :id")
    suspend fun deleteById(id: Long)
}
