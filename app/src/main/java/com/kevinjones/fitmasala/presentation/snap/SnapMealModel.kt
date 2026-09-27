package com.kevinjones.fitmasala.presentation.snap

import com.kevinjones.fitmasala.data.local.entity.LoggedMealEntity
import com.kevinjones.fitmasala.data.local.entity.Macros
import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.data.local.entity.PortionUnit
import com.kevinjones.fitmasala.data.remote.dto.PhotoEstimateDto
import com.kevinjones.fitmasala.data.remote.dto.PhotoItemDto
import com.kevinjones.fitmasala.data.remote.structuredPortion
import com.kevinjones.fitmasala.data.remote.toLoggedMeals
import com.kevinjones.fitmasala.data.remote.toMacros
import kotlin.math.roundToLong

/**
 * Where "Snap a meal" is, as plain data. No Android here on purpose: this is the
 * part of the flow whose rules can be proven on the JVM - what the total is, what
 * gets logged, when logging is allowed - so the screen and ViewModel only move
 * between these states and draw them.
 */
sealed interface SnapMealState {

    /** Waiting on the camera app, or for the user to open it. */
    data object Capturing : SnapMealState

    /** Camera permission refused. Explained, never a blank screen. */
    data object PermissionDenied : SnapMealState

    data class Estimating(val photoPath: String) : SnapMealState

    /**
     * The estimate, shown before anything is written. Every photo goes through
     * here: a result carrying advisories must be seen, never logged silently, and
     * a clean one costs a single tap.
     */
    data class Review(
        val photoPath: String,
        val eatenAt: Long,
        val mealType: MealType,
        val estimate: PhotoEstimateDto,
        val advisories: List<String>,
        val logging: Boolean = false,
    ) : SnapMealState {

        val dishes: List<PhotoItemDto> get() = estimate.items

        /**
         * The Meal's total is the sum of its Dishes. The model's own stated total
         * only feeds the mismatch advisory; it is never shown as the answer.
         */
        val total: Macros
            get() = dishes.fold(Macros()) { sum, dish -> sum + dish.macros.toMacros() }

        val canLog: Boolean get() = dishes.isNotEmpty() && !logging

        /** One row per Dish, through the same mapper every photo log uses. */
        fun toLoggedDishes(): List<LoggedMealEntity> =
            estimate.toLoggedMeals(mealType = mealType, photoPath = photoPath, eatenAt = eatenAt)

        /** "Lunch logged · 3 dishes · 480 kcal" - the confirmation after logging. */
        fun summary(): String {
            val count = dishes.size
            return "${mealType.label()} logged · $count ${if (count == 1) "dish" else "dishes"} · " +
                "${total.calories.roundToLong()} kcal"
        }
    }

    /** The estimate could not be made. Carries the reason in words the user can act on. */
    data class Failed(val photoPath: String?, val message: String) : SnapMealState

    /** Written. The screen hands [summary] to the snackbar and closes. */
    data class Logged(val summary: String) : SnapMealState

    /** Camera dismissed or the flow abandoned; nothing was written. */
    data object Cancelled : SnapMealState
}

/** Sentence case, for chips and confirmations. */
fun MealType.label(): String = when (this) {
    MealType.BREAKFAST -> "Breakfast"
    MealType.LUNCH -> "Lunch"
    MealType.DINNER -> "Dinner"
    MealType.SNACK -> "Snack"
}

/**
 * The portion as the user would say it: "2 roti", "1.5 katori", "180 g". A dish
 * whose portion could not be trusted reads "1 serving" - the same fallback the
 * mapper logs, so the sheet never promises a unit the row will not carry.
 */
fun PhotoItemDto.portionLabel(): String {
    val (quantity, unit) = structuredPortion() ?: (1.0 to PortionUnit.SERVING)
    val amount = quantity.trimmed()
    val one = quantity == 1.0
    val word = when (unit) {
        // Hindi nouns stay as they are said: "2 roti", "3 katori".
        PortionUnit.KATORI -> "katori"
        PortionUnit.ROTI -> "roti"
        PortionUnit.PIECE -> if (one) "piece" else "pieces"
        PortionUnit.PLATE -> if (one) "plate" else "plates"
        PortionUnit.GLASS -> if (one) "glass" else "glasses"
        PortionUnit.TABLESPOON -> "tbsp"
        PortionUnit.GRAMS -> "g"
        PortionUnit.MILLILITRES -> "ml"
        PortionUnit.SERVING -> if (one) "serving" else "servings"
    }
    return "$amount $word"
}

/** 2.0 -> "2", 1.5 -> "1.5", 1.25 -> "1.3". Portions are never finer than a tenth. */
internal fun Double.trimmed(): String {
    val tenths = (this * 10).roundToLong()
    return if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}.${kotlin.math.abs(tenths % 10)}"
}
