package com.kevinjones.fitmasala.core.util

import com.kevinjones.fitmasala.data.local.entity.MealType
import java.time.Instant
import java.time.ZoneId

/**
 * The meal an hour of the day most likely belongs to. A default, never a verdict -
 * every screen that uses it lets the user change the answer.
 *
 * The small hours are a snack, not breakfast: food at 2am is the tail of the day
 * before, and calling it breakfast would split one day's eating into two Meals.
 */
fun mealTypeForHour(hour: Int): MealType = when (hour) {
    in 4..10 -> MealType.BREAKFAST
    in 11..15 -> MealType.LUNCH
    in 16..18 -> MealType.SNACK
    in 19..23 -> MealType.DINNER
    else -> MealType.SNACK
}

/** [mealTypeForHour] for a moment in time, in the user's zone. */
fun mealTypeAt(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): MealType =
    mealTypeForHour(Instant.ofEpochMilli(epochMillis).atZone(zone).hour)
