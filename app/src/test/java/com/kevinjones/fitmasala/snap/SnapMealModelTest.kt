package com.kevinjones.fitmasala.snap

import com.kevinjones.fitmasala.core.util.mealTypeAt
import com.kevinjones.fitmasala.core.util.mealTypeForHour
import com.kevinjones.fitmasala.data.local.entity.MealSource
import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.data.local.entity.PortionUnit
import com.kevinjones.fitmasala.data.remote.dto.MacrosDto
import com.kevinjones.fitmasala.data.remote.dto.PhotoEstimateDto
import com.kevinjones.fitmasala.data.remote.dto.PhotoItemDto
import com.kevinjones.fitmasala.presentation.snap.SnapMealState
import com.kevinjones.fitmasala.presentation.snap.portionLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * The rules of "Snap a meal" that do not need a camera or a screen: which Meal a
 * photo defaults to, what the total is, what gets written, and when writing is
 * allowed at all.
 */
class SnapMealModelTest {

    private fun dish(
        name: String,
        kcal: Double,
        quantity: Double = 1.0,
        unit: String = "KATORI",
        protein: Double = 10.0,
    ) = PhotoItemDto(
        name = name,
        region = "PUNJABI",
        portionEstimate = "$quantity $unit",
        portionQuantity = quantity,
        portionUnit = unit,
        portionBasis = "plate rim",
        macros = MacrosDto(calories = kcal, proteinG = protein, carbsG = 30.0, fatG = 8.0, fiberG = 4.0),
        confidence = "medium",
        uncertaintyNote = "oil not visible",
    )

    private fun review(
        vararg dishes: PhotoItemDto,
        mealType: MealType = MealType.LUNCH,
        statedTotal: Double = 999.0,
    ) = SnapMealState.Review(
        photoPath = "/data/files/meal-photos/meal-1.jpg",
        eatenAt = 1_700_000_000_000L,
        mealType = mealType,
        estimate = PhotoEstimateDto(
            containsFood = dishes.isNotEmpty(),
            items = dishes.toList(),
            totalMacros = MacrosDto(statedTotal, 0.0, 0.0, 0.0, 0.0),
            overallConfidence = "medium",
        ),
        advisories = emptyList(),
    )

    @Test
    fun mealTypeDefaultsFollowTheClock() {
        assertEquals(MealType.BREAKFAST, mealTypeForHour(4))
        assertEquals(MealType.BREAKFAST, mealTypeForHour(10))
        assertEquals(MealType.LUNCH, mealTypeForHour(11))
        assertEquals(MealType.LUNCH, mealTypeForHour(15))
        assertEquals(MealType.SNACK, mealTypeForHour(16))
        assertEquals(MealType.SNACK, mealTypeForHour(18))
        assertEquals(MealType.DINNER, mealTypeForHour(19))
        assertEquals(MealType.DINNER, mealTypeForHour(23))
        // 2am is the tail of the day before, not breakfast.
        assertEquals(MealType.SNACK, mealTypeForHour(2))
    }

    @Test
    fun mealTypeAtReadsTheHourInTheGivenZone() {
        val oneFifteenPm = LocalDateTime.of(2026, 9, 27, 13, 15).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(MealType.LUNCH, mealTypeAt(oneFifteenPm, ZoneOffset.UTC))
        // The same instant is 18:45 in India.
        assertEquals(MealType.SNACK, mealTypeAt(oneFifteenPm, ZoneOffset.ofHoursMinutes(5, 30)))
    }

    @Test
    fun theTotalIsTheSumOfTheDishesNotTheModelsStatedTotal() {
        val state = review(dish("Dal", 240.0, protein = 14.0), dish("Roti", 240.0, protein = 8.0), statedTotal = 999.0)

        assertEquals(480.0, state.total.calories, 0.001)
        assertEquals(22.0, state.total.proteinG, 0.001)
    }

    @Test
    fun loggedDishesCarryTheChosenMealTypeTheirPhotoAndTheirPortion() {
        val state = review(dish("Dal", 240.0), dish("Phulka", 240.0, quantity = 2.0, unit = "ROTI"))
            .copy(mealType = MealType.DINNER)

        val rows = state.toLoggedDishes()

        assertEquals(2, rows.size)
        assertTrue(rows.all { it.mealType == MealType.DINNER })
        assertTrue(rows.all { it.source == MealSource.PHOTO && it.isAiEstimate })
        assertTrue(rows.all { it.photoPath == "/data/files/meal-photos/meal-1.jpg" })
        assertTrue(rows.all { it.eatenAt == 1_700_000_000_000L })
        assertEquals(2.0, rows[1].portionQuantity, 0.001)
        assertEquals(PortionUnit.ROTI, rows[1].portionUnit)
    }

    @Test
    fun nothingCanBeLoggedWithoutDishesOrWhileAlreadyLogging() {
        assertTrue(review(dish("Dal", 240.0)).canLog)
        // containsFood = false comes back as a result with no dishes.
        assertFalse(review().canLog)
        assertFalse(review(dish("Dal", 240.0)).copy(logging = true).canLog)
    }

    @Test
    fun theSummaryNamesTheMealTheDishCountAndTheCalories() {
        assertEquals(
            "Lunch logged · 2 dishes · 480 kcal",
            review(dish("Dal", 240.0), dish("Roti", 240.4)).summary(),
        )
        assertEquals("Snack logged · 1 dish · 150 kcal", review(dish("Chai", 150.0), mealType = MealType.SNACK).summary())
    }

    @Test
    fun portionsReadTheWayTheyAreSaid() {
        assertEquals("2 roti", dish("Phulka", 240.0, quantity = 2.0, unit = "ROTI").portionLabel())
        assertEquals("1.5 katori", dish("Dal", 240.0, quantity = 1.5, unit = "KATORI").portionLabel())
        assertEquals("1 piece", dish("Samosa", 260.0, quantity = 1.0, unit = "PIECE").portionLabel())
        assertEquals("3 pieces", dish("Idli", 180.0, quantity = 3.0, unit = "PIECE").portionLabel())
        assertEquals("180 g", dish("Paneer", 300.0, quantity = 180.0, unit = "GRAMS").portionLabel())
    }

    @Test
    fun anUntrustworthyPortionReadsAsOneServingLikeTheRowItBecomes() {
        assertEquals("1 serving", dish("Thali", 700.0, quantity = 0.0, unit = "KATORI").portionLabel())
        assertEquals("1 serving", dish("Thali", 700.0, quantity = 2.0, unit = "THALI_SECTION").portionLabel())
    }
}
