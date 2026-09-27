package com.kevinjones.fitmasala.presentation.snap

import com.kevinjones.fitmasala.core.util.mealTypeAt
import com.kevinjones.fitmasala.data.local.entity.LoggedMealEntity
import com.kevinjones.fitmasala.data.local.entity.Macros
import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.data.local.entity.PortionUnit
import com.kevinjones.fitmasala.data.remote.LlmResult
import com.kevinjones.fitmasala.data.remote.dto.PhotoEstimateDto
import com.kevinjones.fitmasala.data.remote.dto.PhotoItemDto
import com.kevinjones.fitmasala.data.remote.structuredPortion
import com.kevinjones.fitmasala.data.remote.toLoggedMeals
import com.kevinjones.fitmasala.data.remote.toMacros
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
        /** A typed dish is being estimated. */
        val addingDish: Boolean = false,
        /** Why the last typed dish could not be added, in words; null when there is nothing to say. */
        val addError: String? = null,
        /** The user picked the meal type; from then on changing the time leaves it alone. */
        val mealTypeChosen: Boolean = false,
        /** A photo meal is already logged at exactly [eatenAt] - the same photo, most likely. */
        val alreadyLogged: Boolean = false,
        /** The last "Log meal" failed to save; the sheet and its edits are kept. */
        val logError: String? = null,
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

        /** Not while a typed dish is still being estimated - it would be silently left out. */
        val canLog: Boolean get() = kept.isNotEmpty() && !logging && !addingDish

        /**
         * Typed dishes join the end of the sheet, so every existing dish keeps its
         * index. Their advisories join the sheet's, where they are seen before
         * logging like any other.
         */
        fun withAddedDishes(items: List<PhotoItemDto>, newAdvisories: List<String>): Review = copy(
            dishes = dishes + items.map { ReviewDish(it) },
            advisories = advisories + newAdvisories,
            addingDish = false,
            addError = null,
        )

        /**
         * One row per kept Dish, through the same mapper every photo log uses - so
         * a corrected dish is still an Estimate with the model's confidence and its
         * original wording in the portion note.
         */
        fun toLoggedDishes(): List<LoggedMealEntity> =
            estimate.copy(items = kept.map { it.toItem() })
                .toLoggedMeals(mealType = mealType, photoPath = photoPath, eatenAt = eatenAt)

        fun withMealType(chosen: MealType): Review =
            if (logging) this else copy(mealType = chosen, mealTypeChosen = true)

        /**
         * Moves the meal in time. Never later than [now]; the meal type follows the
         * new time unless the user already chose one; the duplicate check starts
         * over, because it was about the old time.
         */
        fun withEatenAt(millis: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): Review {
            if (logging) return this
            val at = minOf(millis, now)
            return copy(
                eatenAt = at,
                mealType = if (mealTypeChosen) mealType else mealTypeAt(at, zone),
                alreadyLogged = false,
            )
        }

        /** Applies [change] to one dish; ignored while logging, so the rows can't shift under it. */
        fun editDish(index: Int, change: (ReviewDish) -> ReviewDish): Review =
            if (logging || index !in dishes.indices) this
            else copy(dishes = dishes.mapIndexed { i, dish -> if (i == index) change(dish) else dish })

        /** "Lunch logged · 3 dishes · 480 kcal" - the confirmation after logging. */
        fun summary(): String = loggedSummary(mealType, kept.size, total.calories)
    }

    /**
     * The estimate could not be made. Carries the reason in words, the ways
     * forward that make sense for *this* reason, and everything needed to try
     * again without retaking the photo. Nothing is ever logged from here.
     */
    data class Failed(
        val photoPath: String?,
        val message: String,
        val eatenAt: Long,
        val mealType: MealType,
        val actions: List<FailureAction>,
    ) : SnapMealState

    /**
     * "Log by hand": one dish typed with its numbers - from a label, a recipe, or
     * the user's own judgement - when the estimate can't be had. Keeps the photo
     * path so the audit trail survives.
     */
    data class ManualEntry(
        val photoPath: String?,
        val eatenAt: Long,
        val mealType: MealType,
        val saving: Boolean = false,
        val saveError: String? = null,
    ) : SnapMealState

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

private fun PortionUnit.startingQuantity(): Double = when (this) {
    PortionUnit.GRAMS -> 100.0
    PortionUnit.MILLILITRES -> 200.0
    else -> 1.0
}

private fun PortionUnit.step(): Double = when (this) {
    PortionUnit.GRAMS -> 10.0
    PortionUnit.MILLILITRES -> 50.0
    else -> 0.5
}

/** "13:04 · Sun 27 Sep": when the meal is being logged against. */
fun eatenAtLabel(millis: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
    Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm · EEE d MMM", locale))

/** The duplicate warning. A question, not a block: the same dal really can be eaten twice. */
fun alreadyLoggedMessage(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    "A photo meal is already logged at ${
        Instant.ofEpochMilli(millis).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
    } - the same photo? Log again only if you ate it twice."

/** Why a typed dish added nothing - said so the next attempt can be better. */
fun noDishMessage(description: String, containsFood: Boolean): String {
    val quoted = "\"${description.trim()}\""
    return if (containsFood) "No dish could be picked out of $quoted. Try one food at a time."
    else "$quoted doesn't read as food. Name the dish and how much, like \"1 tsp ghee\"."
}

/** "Lunch logged · 3 dishes · 480 kcal". */
fun loggedSummary(mealType: MealType, dishes: Int, calories: Double): String =
    "${mealType.label()} logged · $dishes ${if (dishes == 1) "dish" else "dishes"} · ${calories.roundToLong()} kcal"

/** A way forward from a failed estimate. Which ones apply depends on why it failed. */
enum class FailureAction(val label: String) {
    /** Same photo, same time - for failures that may not happen twice. */
    RETRY("Try again"),
    /** A different photo - when this one can't be read or won't be estimated. */
    RETAKE("Retake photo"),
    /** No key, or a rejected one. */
    OPEN_SETTINGS("Open Settings"),
    /** Always available: the meal still gets logged, just without an estimate. */
    LOG_BY_HAND("Log by hand"),
}

/**
 * Every way an estimate call can fail, as a sentence and the actions that make
 * sense for it. Never "something went wrong": the app knows which thing did, and
 * a wrong key needs Settings where a dropped connection needs a retry.
 */
fun failureFor(
    failure: LlmResult.Failure,
    photoPath: String?,
    eatenAt: Long,
    mealType: MealType,
): SnapMealState.Failed {
    val (message, actions) = when (failure) {
        LlmResult.Failure.MissingApiKey ->
            "Estimating a photo needs your API key. Add it in Settings, then try again." to
                listOf(FailureAction.OPEN_SETTINGS, FailureAction.RETRY)
        is LlmResult.Failure.Unauthorized ->
            "Your API key was turned down. Check it in Settings, then try again." to
                listOf(FailureAction.OPEN_SETTINGS, FailureAction.RETRY)
        is LlmResult.Failure.RateLimited -> {
            val wait = failure.retryAfterSeconds?.let { " Try again in about $it seconds." } ?: " Try again in a moment."
            "Too many requests right now.$wait" to listOf(FailureAction.RETRY)
        }
        // Retrying the same photo will get the same answer; a different one might not.
        is LlmResult.Failure.Refused ->
            "The model declined to estimate this photo. A different photo may work." to
                listOf(FailureAction.RETAKE)
        LlmResult.Failure.Truncated ->
            "The estimate was cut off before it finished." to listOf(FailureAction.RETRY)
        is LlmResult.Failure.Unparseable ->
            "The estimate came back in a form the app couldn't read." to listOf(FailureAction.RETRY)
        is LlmResult.Failure.Network ->
            "Couldn't reach the estimate service. Check your connection." to listOf(FailureAction.RETRY)
        is LlmResult.Failure.Http ->
            "The estimate service returned an error (${failure.code})." to listOf(FailureAction.RETRY)
    }
    return SnapMealState.Failed(photoPath, message, eatenAt, mealType, actions + FailureAction.LOG_BY_HAND)
}

/** The photo itself couldn't be opened or decoded - a different one is the fix. */
fun photoUnreadable(photoPath: String?, eatenAt: Long, mealType: MealType): SnapMealState.Failed =
    SnapMealState.Failed(
        photoPath = photoPath,
        message = "This photo couldn't be read. Take or pick another one.",
        eatenAt = eatenAt,
        mealType = mealType,
        actions = listOf(FailureAction.RETAKE, FailureAction.LOG_BY_HAND),
    )

/**
 * The "Log by hand" form as typed: numbers still as text, so the rules for what
 * counts as a number live here, where they can be tested, and not in the UI.
 * Blank macros mean 0 - a label with only calories is still worth logging.
 */
data class ManualDishInput(
    val name: String = "",
    val quantity: Double = 1.0,
    val unit: PortionUnit = PortionUnit.KATORI,
    val calories: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
) {
    /** Same steps as the review sheet: half a katori, 10 g, 50 ml. */
    val step: Double get() = unit.step()
    val canStepDown: Boolean get() = quantity - step >= step - 1e-9
    fun steppedUp(): ManualDishInput = copy(quantity = quantity + step)
    fun steppedDown(): ManualDishInput = if (canStepDown) copy(quantity = quantity - step) else this

    /**
     * A new unit starts from a quantity that means something in it: "1.5 katori"
     * switched to grams is not 1.5 g.
     */
    fun withUnit(newUnit: PortionUnit): ManualDishInput =
        if (newUnit == unit) this else copy(unit = newUnit, quantity = newUnit.startingQuantity())

    val portionLabel: String get() = portionLabel(quantity, unit)

    private fun parse(text: String): Double? =
        text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    private fun macroOrZero(text: String): Double? = if (text.isBlank()) 0.0 else parse(text)

    val macros: Macros?
        get() {
            val kcal = parse(calories) ?: return null
            return Macros(
                calories = kcal,
                proteinG = macroOrZero(protein) ?: return null,
                carbsG = macroOrZero(carbs) ?: return null,
                fatG = macroOrZero(fat) ?: return null,
            )
        }

    /** What stops the dish being logged, in words. Empty means it can be logged. */
    fun problems(): List<String> = buildList {
        if (name.isBlank()) add("Give the dish a name.")
        if (quantity <= 0) add("The portion has to be more than zero.")
        val kcal = parse(calories)
        when {
            kcal == null -> add("Enter the calories as a number.")
            kcal <= 0 -> add("Calories have to be more than zero.")
        }
        listOf("Protein" to protein, "Carbs" to carbs, "Fat" to fat).forEach { (label, text) ->
            val value = macroOrZero(text)
            if (value == null || value < 0) add("$label has to be a number of grams, or left blank.")
        }
    }

    /**
     * The same Atwater cross-check the model's numbers get: a warning, not a
     * block - a mistyped digit is caught, a genuine label is not refused. Only
     * when macros were entered; calories alone have nothing to disagree with.
     */
    fun advisory(): String? {
        val m = macros ?: return null
        if (m.proteinG + m.carbsG + m.fatG <= 0 || m.isInternallyConsistent()) return null
        return "These macros add up to ${m.derivedCalories.roundToLong()} kcal, not ${m.calories.roundToLong()}. " +
            "Check the numbers - or log it anyway if the label says so."
    }
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
