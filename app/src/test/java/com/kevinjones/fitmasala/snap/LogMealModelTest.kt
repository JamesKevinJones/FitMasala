package com.kevinjones.fitmasala.snap

import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.presentation.log.LogMealState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class LogMealModelTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private fun at(hour: Int, minute: Int = 0, day: Int = 28) =
        LocalDateTime.of(2026, 9, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test fun aNewFormIsNowWithTheMealTypeFromTheClock() {
        val state = LogMealState.startingAt(at(13, 20), zone)
        assertEquals(at(13, 20), state.eatenAt)
        assertEquals(MealType.LUNCH, state.mealType)
    }

    @Test fun movingTheTimeMovesTheMealTypeUntilOneIsChosen() {
        val start = LogMealState.startingAt(at(16, 30), zone) // SNACK
        val moved = start.withEatenAt(at(13), now = at(16, 30), zone = zone)
        assertEquals(MealType.LUNCH, moved.mealType)

        val chosen = start.withMealType(MealType.DINNER).withEatenAt(at(8), now = at(16, 30), zone = zone)
        assertEquals("a chosen meal type stays", MealType.DINNER, chosen.mealType)
        assertEquals(at(8), chosen.eatenAt)
    }

    @Test fun aMealCannotBeLoggedInTheFuture() {
        val state = LogMealState.startingAt(at(9), zone).withEatenAt(at(21), now = at(9), zone = zone)
        assertEquals(at(9), state.eatenAt)
    }

    @Test fun yesterdaysDinnerLandsOnYesterday() {
        val state = LogMealState.startingAt(at(9), zone).withEatenAt(at(20, day = 27), now = at(9), zone = zone)
        assertEquals(at(20, day = 27), state.eatenAt)
        assertEquals(MealType.DINNER, state.mealType)
    }

    @Test fun nothingMovesWhileSaving() {
        val saving = LogMealState.startingAt(at(13), zone).copy(saving = true)
        assertEquals(saving, saving.withMealType(MealType.SNACK))
        assertEquals(saving, saving.withEatenAt(at(8), now = at(13), zone = zone))
    }
}
