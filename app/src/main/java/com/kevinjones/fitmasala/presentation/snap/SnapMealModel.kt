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
        /** What the user has made of the model's dishes. Starts as the model's answer. */
        val dishes: List<ReviewDish> = estimate.items.map { ReviewDish(it) },
    ) : SnapMealState {

        /** The dishes that will be logged: everything not removed. */
        val kept: List<ReviewDish> get() = dishes.filterNot { it.removed }

        /**
         * The Meal's total is the sum of its Dishes as they stand now. The model's
         * own stated total only feeds the mismatch advisory; it is never shown as
         * the answer.
         */
        val total: Macros
            get() = kept.fold(Macros()) { sum, dish -> sum + dish.macros }

        val canLog: Boolean get() = kept.isNotEmpty() && !logging

        /**
         * One row per kept Dish, through the same mapper every photo log uses - so
         * a corrected dish is still an Estimate with the model's confidence and its
         * original wording in the portion note.
         */
        fun toLoggedDishes(): List<LoggedMealEntity> =
            estimate.copy(items = kept.map { it.toItem() })
                .toLoggedMeals(mealType = mealType, photoPath = photoPath, eatenAt = eatenAt)

        /** Applies [change] to one dish; ignored while logging, so the rows can't shift under it. */
        fun editDish(index: Int, change: (ReviewDish) -> ReviewDish): Review =
            if (logging || index !in dishes.indices) this
            else copy(dishes = dishes.mapIndexed { i, dish -> if (i == index) change(dish) else dish })

        /** "Lunch logged · 3 dishes · 480 kcal" - the confirmation after logging. */
        fun summary(): String {
            val count = kept.size
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

/**
 * One dish on the review sheet, as the user has corrected it.
 *
 * Holds the model's [original] and derives everything else from it, so stepping
 * 3 roti down to 2 and back up returns exactly the model's numbers - macros are
 * always scaled from the original portion, never from the previous step.
 *
 * The unit is fixed: katori stays katori. A dish in the wrong unit is removed and
 * added again, not converted, because a conversion would have to guess grams.
 */
data class ReviewDish(
    val original: PhotoItemDto,
    val name: String = original.name,
    val quantity: Double = original.structuredPortion()?.first ?: 1.0,
    val removed: Boolean = false,
) {
    /** The model's unit, or SERVING when it could not be trusted (the mapper's fallback). */
    val unit: PortionUnit get() = original.structuredPortion()?.second ?: PortionUnit.SERVING

    private val originalQuantity: Double get() = original.structuredPortion()?.first ?: 1.0

    /** The model's macros, scaled by how far the portion has been stepped. */
    val macros: Macros get() = original.macros.toMacros() * (quantity / originalQuantity)

    val portionLabel: String get() = portionLabel(quantity, unit)

    /** How far one tap moves the portion: half a katori or roti, ten grams, fifty ml. */
    val step: Double get() = unit.step()

    /** The portion never steps to zero - taking a dish away is [removed], on purpose. */
    val canStepDown: Boolean get() = quantity - step >= step - 1e-9

    fun steppedUp(): ReviewDish = copy(quantity = quantity + step)

    fun steppedDown(): ReviewDish = if (canStepDown) copy(quantity = quantity - step) else this

    /**
     * What the stepper's buttons say to TalkBack: "Add half a roti to Phulka",
     * "Remove 10 g from Paneer". "Add one" would be wrong for every unit here.
     */
    fun stepDescription(up: Boolean): String {
        val amount = when (unit) {
            PortionUnit.GRAMS, PortionUnit.MILLILITRES -> portionLabel(step, unit)
            else -> "half a ${portionLabel(1.0, unit).substringAfter(' ')}"
        }
        return if (up) "Add $amount to $name" else "Remove $amount from $name"
    }

    /** A blank name keeps the old one - a dish logged with no name is unreadable later. */
    fun renamed(newName: String): ReviewDish = newName.trim().let { if (it.isEmpty()) this else copy(name = it) }

    /** Back to the model's answer shape, for the shared mapper. */
    fun toItem(): PhotoItemDto {
        val scaled = macros
        return original.copy(
            name = name,
            portionQuantity = quantity,
            portionUnit = unit.name,
            macros = original.macros.copy(
                calories = scaled.calories,
                proteinG = scaled.proteinG,
                carbsG = scaled.carbsG,
                fatG = scaled.fatG,
                fiberG = scaled.fiberG,
            ),
        )
    }
}

private fun PortionUnit.step(): Double = when (this) {
    PortionUnit.GRAMS -> 10.0
    PortionUnit.MILLILITRES -> 50.0
    else -> 0.5
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
    return portionLabel(quantity, unit)
}

/** "2 roti", "1.5 katori", "180 g", "3 pieces". */
fun portionLabel(quantity: Double, unit: PortionUnit): String {
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
