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
import com.kevinjones.fitmasala.presentation.snap.noDishMessage
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

    // --- Editing on the review sheet (#6) ---

    private fun roti() = dish("Phulka", 360.0, quantity = 3.0, unit = "ROTI", protein = 12.0)

    @Test
    fun steppingAPortionScalesEveryMacroFromTheModelsNumbers() {
        val two = review(roti()).editDish(0) { it.steppedDown().steppedDown() }.dishes[0]

        assertEquals(2.0, two.quantity, 0.001)
        assertEquals("2 roti", two.portionLabel)
        // 2 of 3 roti is two thirds of everything.
        assertEquals(240.0, two.macros.calories, 0.001)
        assertEquals(8.0, two.macros.proteinG, 0.001)
        assertEquals(20.0, two.macros.carbsG, 0.001)
    }

    @Test
    fun steppingAwayAndBackReturnsExactlyTheModelsAnswer() {
        val there = review(roti()).editDish(0) { it.steppedUp().steppedUp().steppedDown().steppedDown() }.dishes[0]
        assertEquals(360.0, there.macros.calories, 1e-9)
        assertEquals(3.0, there.quantity, 1e-9)
    }

    @Test
    fun aPortionNeverStepsToZero() {
        val half = review(dish("Dal", 200.0, quantity = 0.5)).dishes[0]
        assertFalse(half.canStepDown)
        assertEquals(0.5, half.steppedDown().quantity, 0.001)

        // A model portion smaller than one step is never raised by stepping down.
        val sliver = review(dish("Pickle", 20.0, quantity = 0.3)).dishes[0]
        assertEquals(0.3, sliver.steppedDown().quantity, 0.001)
    }

    @Test
    fun stepsFollowTheUnit() {
        assertEquals(0.5, review(dish("Dal", 240.0)).dishes[0].step, 0.0)
        assertEquals(10.0, review(dish("Paneer", 300.0, quantity = 150.0, unit = "GRAMS")).dishes[0].step, 0.0)
        assertEquals(50.0, review(dish("Lassi", 200.0, quantity = 250.0, unit = "MILLILITRES")).dishes[0].step, 0.0)
    }

    @Test
    fun renamingTrimsAndABlankNameIsIgnored() {
        val state = review(dish("Dal", 240.0))
        assertEquals("Dal makhani", state.editDish(0) { it.renamed("  Dal makhani ") }.dishes[0].name)
        assertEquals("Dal", state.editDish(0) { it.renamed("   ") }.dishes[0].name)
    }

    @Test
    fun aRemovedDishLeavesTheTotalTheCountAndTheLogAndCanComeBack() {
        val state = review(dish("Dal", 240.0), dish("Roti", 240.0))
        val removed = state.editDish(1) { it.copy(removed = true) }

        assertEquals(240.0, removed.total.calories, 0.001)
        assertEquals(1, removed.toLoggedDishes().size)
        assertEquals("Lunch logged · 1 dish · 240 kcal", removed.summary())

        val restored = removed.editDish(1) { it.copy(removed = false) }
        assertEquals(480.0, restored.total.calories, 0.001)
    }

    @Test
    fun removingEveryDishDisablesLogging() {
        val state = review(dish("Dal", 240.0)).editDish(0) { it.copy(removed = true) }
        assertFalse(state.canLog)
    }

    @Test
    fun nothingChangesWhileLogging() {
        val logging = review(roti()).copy(logging = true)
        assertEquals(logging, logging.editDish(0) { it.steppedDown() })
    }

    @Test
    fun aCorrectedDishIsLoggedAsAnEstimateWithItsNewPortionAndTheModelsWording() {
        val row = review(roti())
            .editDish(0) { it.steppedDown().steppedDown().renamed("Tandoori roti") }
            .toLoggedDishes()
            .single()

        assertEquals("Tandoori roti", row.name)
        assertEquals(2.0, row.portionQuantity, 0.001)
        assertEquals(PortionUnit.ROTI, row.portionUnit)
        assertEquals(240.0, row.macros.calories, 0.001)
        // Counting roti is a correction, not weighing: still an Estimate.
        assertTrue(row.isAiEstimate)
        assertEquals(0.6, row.estimateConfidence!!, 0.001)
        // The model's own wording survives beside the correction.
        assertEquals("3.0 ROTI", row.portionNote)
    }

    @Test
    fun aFallbackDishStepsInServings() {
        val row = review(dish("Thali", 700.0, quantity = 0.0))
            .editDish(0) { it.steppedUp() }
            .toLoggedDishes()
            .single()

        assertEquals(PortionUnit.SERVING, row.portionUnit)
        assertEquals(1.5, row.portionQuantity, 0.001)
        assertEquals(1050.0, row.macros.calories, 0.001)
    }

    @Test
    fun stepperButtonsSayWhatTheyDo() {
        val roti = review(roti()).dishes[0]
        assertEquals("Add half a roti to Phulka", roti.stepDescription(up = true))
        assertEquals("Remove half a roti from Phulka", roti.stepDescription(up = false))
        val paneer = review(dish("Paneer", 300.0, quantity = 150.0, unit = "GRAMS")).dishes[0]
        assertEquals("Remove 10 g from Paneer", paneer.stepDescription(up = false))
        val thali = review(dish("Thali", 700.0, quantity = 0.0)).dishes[0]
        assertEquals("Add half a serving to Thali", thali.stepDescription(up = true))
    }

    // --- Typed dishes (#7) ---

    @Test
    fun typedDishesJoinTheEndOfTheSheetAndTheTotal() {
        val state = review(dish("Dal", 240.0), dish("Roti", 240.0))
            .editDish(0) { it.steppedUp() }
            .copy(addingDish = true)
            .withAddedDishes(listOf(dish("Ghee", 45.0, quantity = 1.0, unit = "TABLESPOON")), listOf("Check the ghee"))

        assertEquals(listOf("Dal", "Roti", "Ghee"), state.dishes.map { it.name })
        // The edit made before the add is kept, at the same index.
        assertEquals(1.5, state.dishes[0].quantity, 0.001)
        assertEquals(360.0 + 240.0 + 45.0, state.total.calories, 0.001)
        assertEquals(listOf("Check the ghee"), state.advisories)
        assertFalse(state.addingDish)
        assertEquals(3, state.toLoggedDishes().size)
    }

    @Test
    fun nothingIsLoggedWhileATypedDishIsStillBeingEstimated() {
        assertFalse(review(dish("Dal", 240.0)).copy(addingDish = true).canLog)
    }

    @Test
    fun anEmptyPhotoCanBeFilledByTypingTheMeal() {
        val state = review().withAddedDishes(listOf(dish("Rajma chawal", 520.0)), emptyList())
        assertTrue(state.canLog)
        assertEquals(520.0, state.total.calories, 0.001)
    }

    @Test
    fun aTypedDishThatAddsNothingSaysWhy() {
        assertEquals(
            "\"my keys\" doesn't read as food. Name the dish and how much, like \"1 tsp ghee\".",
            noDishMessage(" my keys ", containsFood = false),
        )
        assertEquals(
            "No dish could be picked out of \"stuff\". Try one food at a time.",
            noDishMessage("stuff", containsFood = true),
        )
    }
}
