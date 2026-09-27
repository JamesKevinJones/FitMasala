package com.kevinjones.fitmasala.snap

import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.data.remote.dto.MacrosDto
import com.kevinjones.fitmasala.data.remote.dto.PhotoEstimateDto
import com.kevinjones.fitmasala.data.remote.dto.PhotoItemDto
import com.kevinjones.fitmasala.data.remote.dto.RecipeDto
import com.kevinjones.fitmasala.data.remote.toLoggedMeal
import com.kevinjones.fitmasala.data.remote.toLoggedMeals
import com.kevinjones.fitmasala.presentation.snap.SnapMealState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every Estimate names the model that produced it, so a change of estimator -
 * and the bias shift that comes with it - shows up in the log (#20).
 */
class EstimateModelTest {

    private fun dish(name: String) = PhotoItemDto(
        name = name,
        region = "PUNJABI",
        portionEstimate = "1 katori",
        portionQuantity = 1.0,
        portionUnit = "KATORI",
        portionBasis = "plate rim",
        macros = MacrosDto(200.0, 10.0, 30.0, 8.0, 4.0),
        confidence = "medium",
        uncertaintyNote = "oil not visible",
    )

    private fun estimate(vararg dishes: PhotoItemDto) = PhotoEstimateDto(
        containsFood = true,
        items = dishes.toList(),
        totalMacros = MacrosDto(400.0, 0.0, 0.0, 0.0, 0.0),
        overallConfidence = "medium",
    )

    private fun review(model: String?, vararg dishes: PhotoItemDto) = SnapMealState.Review(
        photoPath = "/data/files/meal-photos/meal-1.jpg",
        eatenAt = 1_700_000_000_000L,
        mealType = MealType.LUNCH,
        estimate = estimate(*dishes),
        advisories = emptyList(),
        model = model,
    )

    @Test
    fun photoDishesCarryThePhotoCallsModel() {
        val rows = review("claude-opus-5", dish("Dal"), dish("Roti")).toLoggedDishes()
        assertEquals(listOf("claude-opus-5", "claude-opus-5"), rows.map { it.estimateModel })
    }

    @Test
    fun aTypedDishCarriesTheModelOfItsOwnCall() {
        val rows = review("claude-opus-5", dish("Dal"))
            .withAddedDishes(listOf(dish("Ghee")), emptyList(), model = "gemini-flash")
            .toLoggedDishes()
        assertEquals(listOf("Dal", "Ghee"), rows.map { it.name })
        assertEquals(listOf("claude-opus-5", "gemini-flash"), rows.map { it.estimateModel })
    }

    @Test
    fun removingADishKeepsEachRemainingRowWithItsOwnModel() {
        val rows = review("claude-opus-5", dish("Dal"), dish("Rice"))
            .withAddedDishes(listOf(dish("Ghee")), emptyList(), model = "gemini-flash")
            .editDish(1) { it.copy(removed = true) }
            .toLoggedDishes()
        assertEquals(listOf("Dal" to "claude-opus-5", "Ghee" to "gemini-flash"), rows.map { it.name to it.estimateModel })
    }

    @Test
    fun theMappersRecordTheModelAndDefaultToNone() {
        val photo = estimate(dish("Dal"))
        assertEquals("claude-opus-5", photo.toLoggedMeals(MealType.LUNCH, null, 0L, "claude-opus-5").single().estimateModel)
        assertNull(photo.toLoggedMeals(MealType.LUNCH, null, 0L).single().estimateModel)

        val recipe = RecipeDto(
            title = "Rajma",
            region = "PUNJABI",
            provenanceNote = "home style",
            cookingMethod = "TADKA",
            servings = 4,
            ingredients = emptyList(),
            instructions = emptyList(),
            techniqueNotes = emptyList(),
            macrosPerServing = MacrosDto(350.0, 15.0, 50.0, 8.0, 10.0),
            portionDescription = "1 katori",
        )
        assertEquals(
            "claude-opus-5",
            recipe.toLoggedMeal(MealType.DINNER, 1.0, sourceRecipeId = 7L, eatenAt = 0L, estimateModel = "claude-opus-5").estimateModel,
        )
    }
}
