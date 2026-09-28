package com.kevinjones.fitmasala.presentation.log

import com.kevinjones.fitmasala.core.util.mealTypeAt
import com.kevinjones.fitmasala.data.local.entity.MealType
import java.time.ZoneId

/**
 * "Log a meal": one dish typed with its numbers - from a label, a home recipe, a
 * restaurant menu - with no photo and no model involved. The dish itself is
 * `ManualDishInput`, shared with Snap a meal's "Log by hand"; this holds the rest.
 *
 * Plain Kotlin so the rules run as JVM tests. Logged as MANUAL, never an
 * Estimate: the numbers are the user's own, and the UI must not dress them up
 * as the camera's guess (or the camera's guess as them).
 */
data class LogMealState(
    val eatenAt: Long,
    val mealType: MealType,
    /** Once the user picks a meal type, moving the time no longer overrides it. */
    val mealTypeChosen: Boolean = false,
    val saving: Boolean = false,
    val saveError: String? = null,
    /** Set once written; the screen hands it to the snackbar and closes. */
    val logged: String? = null,
) {
    fun withMealType(chosen: MealType): LogMealState =
        if (saving) this else copy(mealType = chosen, mealTypeChosen = true)

    /**
     * Moves the meal in time - for the lunch logged at teatime. Never later than
     * [now]; the meal type follows the new time unless the user already chose one.
     * The same rule as the photo review sheet.
     */
    fun withEatenAt(millis: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): LogMealState {
        if (saving) return this
        val at = minOf(millis, now)
        return copy(eatenAt = at, mealType = if (mealTypeChosen) mealType else mealTypeAt(at, zone))
    }

    companion object {
        /** A new form: eaten now, meal type from the clock. */
        fun startingAt(now: Long, zone: ZoneId = ZoneId.systemDefault()) =
            LogMealState(eatenAt = now, mealType = mealTypeAt(now, zone))
    }
}
