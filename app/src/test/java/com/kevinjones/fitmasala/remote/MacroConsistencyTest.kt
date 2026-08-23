package com.kevinjones.fitmasala.remote

import com.kevinjones.fitmasala.data.local.entity.Macros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Atwater cross-check is the only defence against a response that is
 * schema-valid and nutritionally nonsense. The schema guarantees the shape; this
 * is what guards the contents.
 */
class MacroConsistencyTest {

    @Test
    fun realisticDishReconciles() {
        // Dal tadka, one katori: 240 kcal, 14P / 30C / 8F -> 4*14 + 4*30 + 9*8 = 248
        val macros = Macros(calories = 240.0, proteinG = 14.0, carbsG = 30.0, fatG = 8.0)
        assertTrue(macros.isInternallyConsistent())
    }

    @Test
    fun caloriesShavedToFlatterTheDishAreCaught() {
        // The classic bad estimate: grams say ~700 kcal, the model reports 450.
        val macros = Macros(calories = 450.0, proteinG = 20.0, carbsG = 60.0, fatG = 40.0)
        assertEquals(680.0, macros.derivedCalories, 0.001)
        assertFalse(macros.isInternallyConsistent())
    }

    /** Deep-fried food is exactly where a model tends to forget absorbed oil. */
    @Test
    fun forgottenFryingOilShowsUpAsInconsistency() {
        // Four pooris with the absorbed oil left out entirely.
        val withoutOil = Macros(calories = 560.0, proteinG = 12.0, carbsG = 72.0, fatG = 6.0)
        assertFalse(withoutOil.isInternallyConsistent())

        // Same dish with ~10% of weight absorbed as oil counted in.
        val withOil = Macros(calories = 560.0, proteinG = 12.0, carbsG = 72.0, fatG = 25.0)
        assertTrue(withOil.isInternallyConsistent())
    }

    @Test
    fun zeroCalorieMealIsNeverConsideredValid() {
        assertFalse(Macros.ZERO.isInternallyConsistent())
        assertFalse(Macros(calories = 0.0, proteinG = 10.0).isInternallyConsistent())
    }

    @Test
    fun toleranceAllowsHonestRoundingButNotDrift() {
        // 4% out - rounding and fibre accounting. Acceptable.
        assertTrue(Macros(calories = 400.0, proteinG = 20.0, carbsG = 40.0, fatG = 16.0).isInternallyConsistent())
        // 25% out - something is actually wrong.
        assertFalse(Macros(calories = 400.0, proteinG = 20.0, carbsG = 60.0, fatG = 20.0).isInternallyConsistent())
    }

    @Test
    fun scalingAPortionScalesEveryMacro() {
        val perServing = Macros(calories = 300.0, proteinG = 15.0, carbsG = 30.0, fatG = 12.0, fiberG = 5.0)
        val twoServings = perServing * 2.0

        assertEquals(600.0, twoServings.calories, 0.001)
        assertEquals(30.0, twoServings.proteinG, 0.001)
        assertEquals(10.0, twoServings.fiberG, 0.001)
        assertTrue(twoServings.isInternallyConsistent())
    }
}
