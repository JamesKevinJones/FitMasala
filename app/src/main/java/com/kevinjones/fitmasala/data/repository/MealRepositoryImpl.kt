package com.kevinjones.fitmasala.data.repository

import com.kevinjones.fitmasala.core.util.DateKeys
import com.kevinjones.fitmasala.data.local.dao.MealDao
import com.kevinjones.fitmasala.data.local.entity.LoggedMealEntity
import com.kevinjones.fitmasala.data.local.entity.Macros
import com.kevinjones.fitmasala.data.local.entity.MealSource
import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.data.local.entity.PortionUnit
import com.kevinjones.fitmasala.data.local.entity.Region
import com.kevinjones.fitmasala.data.local.relation.DailyMacroTotals
import com.kevinjones.fitmasala.data.local.relation.FrequentMeal
import com.kevinjones.fitmasala.domain.repository.MealRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MealRepositoryImpl @Inject constructor(
    private val dao: MealDao,
) : MealRepository {

    /**
     * "Today" is resolved per collection, not captured once.
     *
     * A tracker is routinely left open across midnight - phone on the bedside
     * table, app in the background. Capturing `today` at construction would keep
     * showing yesterday's meals until the process died. `flow { }` re-reads the
     * date each time a collector attaches, and `flatMapLatest` swaps to the new
     * day's query.
     */
    private fun <T> observeForToday(query: (Long) -> Flow<T>): Flow<T> =
        flow { emit(DateKeys.today()) }.flatMapLatest(query)

    override fun observeToday(): Flow<List<LoggedMealEntity>> =
        observeForToday(dao::observeDay)

    override fun observeTodayTotals(): Flow<DailyMacroTotals> =
        observeForToday(dao::observeDayTotals)

    override fun observeFrequentMeals(limit: Int): Flow<List<FrequentMeal>> =
        dao.observeFrequentMeals(limit)

    /**
     * Re-logging scales the AVERAGE past portion, not the last one.
     *
     * Macros are copied onto the new row rather than referenced, matching the
     * rule that history is immutable - see docs/DECISIONS.md.
     */
    override suspend fun logAgain(
        source: FrequentMeal,
        portions: Double,
        mealType: MealType,
    ): Long {
        val now = System.currentTimeMillis()
        val perPortion = if (source.avgPortionQuantity > 0) source.avgPortionQuantity else 1.0
        val scale = portions / perPortion

        return dao.insert(
            LoggedMealEntity(
                name = source.name,
                nameLocal = source.nameLocal,
                region = enumOrOther(source.region),
                mealType = mealType,
                eatenAt = now,
                dayEpoch = DateKeys.dayEpochOf(now),
                portionQuantity = portions,
                portionUnit = portionUnitOrDefault(source.portionUnit),
                macros = Macros(
                    calories = source.avgCalories * scale,
                    proteinG = source.avgProteinG * scale,
                    // If these are missing from FrequentMeal, they stay 0.0
                    carbsG = source.avgCalories * 0.0, 
                    fatG = source.avgCalories * 0.0,
                ),
                isAiEstimate = true,
                source = MealSource.REPEATED,
            ),
        )
    }

    override suspend fun logRecipe(
        name: String,
        region: String,
        macros: Macros,
        portions: Double,
        mealType: MealType,
        sourceRecipeId: Long?
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            LoggedMealEntity(
                name = name,
                region = enumOrOther(region),
                mealType = mealType,
                eatenAt = now,
                dayEpoch = DateKeys.dayEpochOf(now),
                portionQuantity = portions,
                portionUnit = PortionUnit.SERVING,
                macros = macros * portions,
                isAiEstimate = true,
                source = MealSource.AI_CHAT,
                sourceRecipeId = sourceRecipeId,
            ),
        )
    }

    override suspend fun logManual(
        name: String,
        mealType: MealType,
        portionQuantity: Double,
        portionUnit: PortionUnit,
        calories: Double,
        proteinG: Double,
        carbsG: Double,
        fatG: Double,
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            LoggedMealEntity(
                name = name,
                mealType = mealType,
                eatenAt = now,
                dayEpoch = DateKeys.dayEpochOf(now),
                portionQuantity = portionQuantity,
                portionUnit = portionUnit,
                macros = Macros(calories, proteinG, carbsG, fatG),
                // Typed by hand from a label or a scale: not an estimate.
                isAiEstimate = false,
                source = MealSource.MANUAL,
            ),
        )
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    private fun enumOrOther(raw: String?): Region =
        Region.entries.firstOrNull { it.name == raw } ?: Region.OTHER

    private fun portionUnitOrDefault(raw: String?): PortionUnit =
        PortionUnit.entries.firstOrNull { it.name == raw } ?: PortionUnit.SERVING
}
