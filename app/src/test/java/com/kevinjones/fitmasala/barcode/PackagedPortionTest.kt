package com.kevinjones.fitmasala.barcode

import com.kevinjones.fitmasala.data.local.entity.PortionUnit
import com.kevinjones.fitmasala.data.remote.food.LabelMacros
import com.kevinjones.fitmasala.data.remote.food.PackagedFood
import com.kevinjones.fitmasala.presentation.barcode.PackagedPortion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackagedPortionTest {

    private val label = LabelMacros(calories = 440.0, proteinG = 7.5, carbsG = 77.0, fatG = 11.0, fiberG = 2.0)
    private val biscuits = PackagedFood("8901063093063", "Marie biscuits", "Example Foods", label, PortionUnit.GRAMS, 20.0, "3 biscuits (20 g)")
    private val loose = biscuits.copy(servingGrams = null, servingLabel = null, brand = null)
    private val drink = loose.copy(baseUnit = PortionUnit.MILLILITRES)

    @Test fun aLabelledServingStartsAsOneServing() {
        val portion = PackagedPortion.startingFor(biscuits)
        assertEquals(PortionUnit.SERVING, portion.unit)
        assertEquals(20.0, portion.amount, 0.0)
        assertEquals(88.0, portion.macros.calories, 1e-9)
        assertEquals("1 serving · 20 g", portion.label)
        assertEquals("Marie biscuits (Example Foods)", portion.dishName)
    }

    @Test fun noServingStartsAt100Grams() {
        val portion = PackagedPortion.startingFor(loose)
        assertEquals(PortionUnit.GRAMS, portion.unit)
        assertEquals(440.0, portion.macros.calories, 1e-9)
        assertEquals("100 g", portion.label)
        assertFalse(portion.canUseServings)
        assertEquals("Marie biscuits", portion.dishName)
        assertEquals("100 ml", PackagedPortion.startingFor(drink).label)
    }

    @Test fun stepsAreHalfServingsOrTenGrams() {
        val servings = PackagedPortion.startingFor(biscuits).steppedUp().steppedUp()
        assertEquals(2.0, servings.quantity, 0.0)
        assertEquals(176.0, servings.macros.calories, 1e-9)
        val grams = PackagedPortion.startingFor(loose).steppedDown()
        assertEquals(90.0, grams.quantity, 0.0)
    }

    @Test fun stepsNeverReachZero() {
        val half = PackagedPortion.startingFor(biscuits).steppedDown()
        assertEquals(0.5, half.quantity, 0.0)
        assertFalse(half.canStepDown)
        assertEquals(half, half.steppedDown())
    }

    @Test fun switchingUnitsKeepsTheAmountEaten() {
        val twoServings = PackagedPortion.startingFor(biscuits).steppedUp().steppedUp()
        val asGrams = twoServings.withUnit(PortionUnit.GRAMS)
        assertEquals(40.0, asGrams.quantity, 0.0)
        assertEquals(twoServings.macros, asGrams.macros)
        // 44 g is 2.2 servings: the nearest half is 2; 46 g (2.3) is 2.5.
        assertEquals(2.0, asGrams.copy(quantity = 44.0).withUnit(PortionUnit.SERVING).quantity, 0.0)
        assertEquals(2.5, asGrams.copy(quantity = 46.0).withUnit(PortionUnit.SERVING).quantity, 0.0)
        // A crumb still rounds up to half a serving, never zero.
        assertEquals(0.5, asGrams.copy(quantity = 1.0).withUnit(PortionUnit.SERVING).quantity, 0.0)
    }

    @Test fun servingsAreOnlyOfferedWhenTheLabelHasOne() {
        val grams = PackagedPortion.startingFor(loose)
        assertEquals(grams, grams.withUnit(PortionUnit.SERVING))
        assertTrue(PackagedPortion.startingFor(biscuits).canUseServings)
    }

    @Test fun missingFibreCountsAsZero() {
        val portion = PackagedPortion.startingFor(loose.copy(per100 = label.copy(fiberG = null)))
        assertEquals(0.0, portion.macros.fiberG, 0.0)
    }
}
