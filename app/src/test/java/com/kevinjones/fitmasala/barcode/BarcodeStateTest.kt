package com.kevinjones.fitmasala.barcode

import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.data.local.entity.PortionUnit
import com.kevinjones.fitmasala.data.remote.food.LabelMacros
import com.kevinjones.fitmasala.data.remote.food.LookupResult
import com.kevinjones.fitmasala.data.remote.food.PackagedFood
import com.kevinjones.fitmasala.presentation.barcode.BarcodeState
import com.kevinjones.fitmasala.presentation.barcode.PackagedPortion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class BarcodeStateTest {

    private val zone = ZoneOffset.UTC
    private val fourPm = LocalDateTime.of(2026, 9, 28, 16, 30).toInstant(zone).toEpochMilli()
    private val food = PackagedFood(
        "8901063093063", "Marie biscuits", null,
        LabelMacros(440.0, 7.5, 77.0, 11.0, null), PortionUnit.GRAMS, 20.0, "3 biscuits (20 g)",
    )

    @Test fun aFoundLabelStartsAtOneServingEatenNow() {
        val state = BarcodeState.after("8901063093063", LookupResult.Found(food), fourPm, zone) as BarcodeState.Found
        assertEquals(PackagedPortion.startingFor(food), state.portion)
        assertEquals(fourPm, state.eatenAt)
        assertEquals(MealType.SNACK, state.mealType)
    }

    @Test fun missingProductsAndMissingCaloriesBothAskForTheLabel() {
        val missing = BarcodeState.after("12345678", LookupResult.NotFound, fourPm, zone)
        assertTrue(missing is BarcodeState.NeedsLabel)
        assertTrue((missing as BarcodeState.NeedsLabel).message.contains("12345678"))
        val bare = BarcodeState.after("12345678", LookupResult.NoNutrition("Chakli"), fourPm, zone) as BarcodeState.NeedsLabel
        assertTrue(bare.message.startsWith("Chakli is on Open Food Facts"))
    }

    @Test fun aFailureKeepsTheBarcodeForRetry() {
        assertEquals(
            BarcodeState.Failed("12345678", "offline"),
            BarcodeState.after("12345678", LookupResult.Failed("offline"), fourPm, zone),
        )
    }

    @Test fun nothingChangesWhileSaving() {
        val saving = (BarcodeState.after("8901063093063", LookupResult.Found(food), fourPm, zone) as BarcodeState.Found)
            .copy(saving = true)
        assertEquals(saving, saving.withMealType(MealType.DINNER))
        assertEquals(saving, saving.withPortion(PackagedPortion::steppedUp))
    }
}
