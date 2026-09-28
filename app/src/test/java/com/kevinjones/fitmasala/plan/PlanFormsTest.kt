package com.kevinjones.fitmasala.plan

import com.kevinjones.fitmasala.domain.plan.BodyFatSource
import com.kevinjones.fitmasala.domain.plan.Sex
import com.kevinjones.fitmasala.presentation.plan.CutSetup
import com.kevinjones.fitmasala.presentation.plan.CutSetupForm
import com.kevinjones.fitmasala.presentation.plan.FormResult
import com.kevinjones.fitmasala.presentation.plan.PlanField
import com.kevinjones.fitmasala.presentation.plan.WeighIn
import com.kevinjones.fitmasala.presentation.plan.WeighInForm
import com.kevinjones.fitmasala.presentation.plan.cutWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanFormsTest {

    private val filled = CutSetupForm(height = "178", age = "27", weight = "85", bodyFat = "22")

    private fun FormResult<CutSetup>.valid() = (this as FormResult.Valid).value
    private fun <T> FormResult<T>.errors() = (this as FormResult.Invalid).errors

    @Test fun aTypedEstimateStartsTheCut() {
        val setup = filled.validate().valid()
        assertEquals(22.0, setup.bodyFatPercent, 0.0)
        assertEquals(BodyFatSource.MANUAL_ENTRY, setup.bodyFatSource)
        assertEquals(12.0, setup.goalBodyFatPercent, 0.0)
    }

    @Test fun commaDecimalsAreAccepted() {
        assertEquals(82.5, filled.copy(weight = "82,5").validate().valid().weightKg, 0.0)
    }

    @Test fun theTapeStandsInForATypedEstimate() {
        val setup = filled.copy(bodyFat = "", neck = "38", waist = "90").validate().valid()
        assertEquals(BodyFatSource.NAVY_TAPE, setup.bodyFatSource)
        assertTrue(setup.bodyFatPercent in 15.0..25.0)
        assertNotNull(setup.tape)
    }

    @Test fun aTypedEstimateWinsOverTheTape() {
        val setup = filled.copy(neck = "38", waist = "90").validate().valid()
        assertEquals(22.0, setup.bodyFatPercent, 0.0)
        assertEquals(BodyFatSource.MANUAL_ENTRY, setup.bodyFatSource)
    }

    @Test fun noBodyFatAndNoTapeIsAnError() {
        val errors = filled.copy(bodyFat = "", neck = "38").validate().errors()
        assertEquals(setOf(PlanField.BODY_FAT), errors.keys)
    }

    @Test fun theFemaleTapeNeedsAHip() {
        val form = filled.copy(sex = Sex.FEMALE, bodyFat = "", neck = "33", waist = "75")
        assertNull(form.tapeBodyFat)
        assertTrue(PlanField.BODY_FAT in form.validate().errors())
        assertNotNull(form.copy(hip = "98").tapeBodyFat)
    }

    @Test fun impossibleTapeIsFlaggedOnTheWaist() {
        // Waist below neck: the logarithm is undefined - a typo, not a body.
        val errors = filled.copy(bodyFat = "", neck = "40", waist = "35").validate().errors()
        assertEquals(setOf(PlanField.WAIST), errors.keys)
    }

    @Test fun theGoalMustBeBelowTheStart() {
        assertEquals(setOf(PlanField.GOAL), filled.copy(goal = "22").validate().errors().keys)
        assertEquals(setOf(PlanField.GOAL), filled.copy(goal = "abc").validate().errors().keys)
    }

    @Test fun everyBadFieldGetsItsOwnMessage() {
        val errors = CutSetupForm(height = "5", age = "200", weight = "", bodyFat = "90").validate().errors()
        assertEquals(
            setOf(PlanField.HEIGHT, PlanField.AGE, PlanField.WEIGHT, PlanField.BODY_FAT),
            errors.keys,
        )
    }

    @Test fun aBareWeighInNeedsOnlyWeight() {
        val result = WeighInForm(weight = "84.2").validate(Sex.MALE) as FormResult.Valid<WeighIn>
        assertEquals(WeighIn(84.2, null, null, null), result.value)
    }

    @Test fun aPartialTapeIsRejectedNotDropped() {
        val errors = WeighInForm(weight = "84", waist = "88").validate(Sex.MALE).errors()
        assertEquals(setOf(PlanField.NECK), errors.keys)
    }

    @Test fun aCompleteMaleTapeIgnoresHip() {
        val result = WeighInForm(weight = "84", waist = "88", neck = "38", hip = "99")
            .validate(Sex.MALE) as FormResult.Valid<WeighIn>
        assertEquals(WeighIn(84.0, 88.0, 38.0, null), result.value)
    }

    @Test fun theFemaleWeighInTapeIncludesHip() {
        assertEquals(
            setOf(PlanField.HIP),
            WeighInForm(weight = "64", waist = "72", neck = "32").validate(Sex.FEMALE).errors().keys,
        )
        val result = WeighInForm(weight = "64", waist = "72", neck = "32", hip = "96")
            .validate(Sex.FEMALE) as FormResult.Valid<WeighIn>
        assertEquals(96.0, result.value.hipCm!!, 0.0)
    }

    @Test fun anImplausibleWeightIsRejected() {
        assertEquals(setOf(PlanField.WEIGHT), WeighInForm(weight = "8").validate(Sex.MALE).errors().keys)
    }

    @Test fun cutWeeksCountFromOne() {
        assertNull(cutWeek(null, 100))
        assertEquals(1, cutWeek(100, 100))
        assertEquals(1, cutWeek(100, 106))
        assertEquals(2, cutWeek(100, 107))
        assertEquals(6, cutWeek(100, 137))
    }
}
