package com.kevinjones.fitmasala.presentation.barcode

import com.kevinjones.fitmasala.data.local.entity.Macros
import com.kevinjones.fitmasala.data.local.entity.PortionUnit
import com.kevinjones.fitmasala.data.remote.food.PackagedFood
import com.kevinjones.fitmasala.presentation.snap.portionLabel
import kotlin.math.roundToInt

/**
 * How much of a packaged food was eaten. Servings when the label gives one -
 * "2 biscuits" is how a packet is eaten - else grams (or ml) against the
 * per-100 values. Macros always come from the label's per-100 figures, so
 * stepping never drifts.
 *
 * Plain Kotlin so the rules run as JVM tests.
 */
data class PackagedPortion(
    val food: PackagedFood,
    /** SERVING, or the food's base unit (GRAMS / MILLILITRES). */
    val unit: PortionUnit,
    val quantity: Double,
) {
    val canUseServings: Boolean get() = food.servingGrams != null

    /** Grams (or ml) eaten, whatever the unit shown. */
    val amount: Double
        get() = if (unit == PortionUnit.SERVING) quantity * (food.servingGrams ?: 0.0) else quantity

    val step: Double get() = if (unit == PortionUnit.SERVING) 0.5 else 10.0
    val canStepDown: Boolean get() = quantity - step >= step - 1e-9

    fun steppedUp(): PackagedPortion = copy(quantity = quantity + step)
    fun steppedDown(): PackagedPortion = if (canStepDown) copy(quantity = quantity - step) else this

    /**
     * Switches between servings and grams keeping the amount eaten - 2 servings of
     * 20 g become 40 g, and 45 g becomes 2 servings (nearest half, at least half).
     */
    fun withUnit(newUnit: PortionUnit): PackagedPortion {
        if (newUnit == unit) return this
        val serving = food.servingGrams
        return when {
            newUnit == PortionUnit.SERVING && serving != null ->
                copy(unit = newUnit, quantity = ((amount / serving) * 2).roundToInt().coerceAtLeast(1) / 2.0)
            newUnit == food.baseUnit -> copy(unit = newUnit, quantity = amount.roundToInt().toDouble().coerceAtLeast(1.0))
            else -> this
        }
    }

    val macros: Macros
        get() {
            val factor = amount / 100.0
            val label = food.per100
            return Macros(
                calories = label.calories * factor,
                proteinG = label.proteinG * factor,
                carbsG = label.carbsG * factor,
                fatG = label.fatG * factor,
                fiberG = (label.fiberG ?: 0.0) * factor,
            )
        }

    /** "2 servings · 40 g", or "150 g". */
    val label: String
        get() {
            val base = portionLabel(amount.roundToInt().toDouble(), food.baseUnit)
            return if (unit == PortionUnit.SERVING) "${portionLabel(quantity, unit)} · $base" else base
        }

    /** What the log row is called: the product, with its brand when there is one. */
    val dishName: String
        get() = food.brand?.let { "${food.name} ($it)" } ?: food.name

    companion object {
        /** One serving if the label has one, else 100 g (or ml). */
        fun startingFor(food: PackagedFood): PackagedPortion =
            if (food.servingGrams != null) PackagedPortion(food, PortionUnit.SERVING, 1.0)
            else PackagedPortion(food, food.baseUnit, 100.0)
    }
}
