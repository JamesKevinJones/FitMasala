package com.kevinjones.fitmasala.snap

import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.data.local.entity.PortionUnit
import com.kevinjones.fitmasala.data.remote.LlmResult
import com.kevinjones.fitmasala.presentation.snap.FailureAction
import com.kevinjones.fitmasala.presentation.snap.ManualDishInput
import com.kevinjones.fitmasala.presentation.snap.failureFor
import com.kevinjones.fitmasala.presentation.snap.loggedSummary
import com.kevinjones.fitmasala.presentation.snap.photoUnreadable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the estimate can't be had, the meal still gets logged: each failure says
 * what went wrong and offers the way forward that fits it, and "Log by hand" is
 * always one of them.
 */
class SnapFailuresTest {

    private val eatenAt = 1_790_000_000_000L
    private fun failed(failure: LlmResult.Failure) = failureFor(failure, "/photos/meal.jpg", eatenAt, MealType.LUNCH)

    /** Every subtype, so a new one added to [LlmResult.Failure] shows up here. */
    private val everyFailure: List<LlmResult.Failure> = listOf(
        LlmResult.Failure.MissingApiKey,
        LlmResult.Failure.Unauthorized("401"),
        LlmResult.Failure.RateLimited(30, "429"),
        LlmResult.Failure.Refused("cyber", "refused"),
        LlmResult.Failure.Truncated,
        LlmResult.Failure.Unparseable("{", "bad json"),
        LlmResult.Failure.Network("timeout"),
        LlmResult.Failure.Http(500, "boom"),
    )

    @Test
    fun everyFailureCanBeLoggedByHandAndKeepsThePhotoAndTime() {
        everyFailure.forEach { failure ->
            val state = failed(failure)
            assertEquals(failure.toString(), FailureAction.LOG_BY_HAND, state.actions.last())
            assertEquals("/photos/meal.jpg", state.photoPath)
            assertEquals(eatenAt, state.eatenAt)
            assertEquals(MealType.LUNCH, state.mealType)
            assertTrue(failure.toString(), state.message.isNotBlank())
            assertFalse(failure.toString(), state.message.contains("something went wrong", ignoreCase = true))
        }
    }

    @Test
    fun aMissingOrRejectedKeyLeadsToSettingsFirst() {
        listOf(LlmResult.Failure.MissingApiKey, LlmResult.Failure.Unauthorized("401")).forEach {
            val actions = failed(it).actions
            assertEquals(FailureAction.OPEN_SETTINGS, actions.first())
            assertTrue(FailureAction.RETRY in actions)
        }
        assertTrue(failed(LlmResult.Failure.MissingApiKey).message.contains("API key"))
    }

    @Test
    fun passingFailuresOfferTheSamePhotoAgain() {
        listOf(
            LlmResult.Failure.RateLimited(null, "429"),
            LlmResult.Failure.Truncated,
            LlmResult.Failure.Unparseable("{", "bad"),
            LlmResult.Failure.Network("timeout"),
            LlmResult.Failure.Http(503, "down"),
        ).forEach {
            assertEquals(it.toString(), listOf(FailureAction.RETRY, FailureAction.LOG_BY_HAND), failed(it).actions)
        }
    }

    @Test
    fun aRefusalIsNotRetriedWithTheSamePhoto() {
        val actions = failed(LlmResult.Failure.Refused(null, "no")).actions
        assertEquals(listOf(FailureAction.RETAKE, FailureAction.LOG_BY_HAND), actions)
    }

    @Test
    fun aRateLimitSaysHowLongToWaitWhenTheServerDoes() {
        assertTrue(failed(LlmResult.Failure.RateLimited(30, "429")).message.contains("30 seconds"))
        assertFalse(failed(LlmResult.Failure.RateLimited(null, "429")).message.contains("null"))
        assertTrue(failed(LlmResult.Failure.Http(500, "boom")).message.contains("500"))
    }

    @Test
    fun anUnreadablePhotoAsksForAnotherOne() {
        val state = photoUnreadable(null, eatenAt, MealType.DINNER)
        assertEquals(listOf(FailureAction.RETAKE, FailureAction.LOG_BY_HAND), state.actions)
        assertNull(state.photoPath)
    }

    // --- Log by hand ---------------------------------------------------------

    private val dal = ManualDishInput(name = "Dal tadka", calories = "240")

    @Test
    fun caloriesAloneAreEnough() {
        assertEquals(emptyList<String>(), dal.problems())
        val macros = dal.macros!!
        assertEquals(240.0, macros.calories, 0.0)
        assertEquals(0.0, macros.proteinG + macros.carbsG + macros.fatG, 0.0)
        assertNull("nothing to cross-check without macros", dal.advisory())
    }

    @Test
    fun aCommaDecimalIsANumber() {
        val input = dal.copy(calories = "240,5", protein = " 12,5 ")
        assertEquals(emptyList<String>(), input.problems())
        assertEquals(12.5, input.macros!!.proteinG, 0.0)
    }

    @Test
    fun whatStopsADishBeingLoggedIsSaidInWords() {
        assertTrue(dal.copy(name = "  ").problems().single().contains("name"))
        assertTrue(dal.copy(calories = "").problems().single().contains("calories"))
        assertTrue(dal.copy(calories = "abc").problems().single().contains("calories"))
        assertTrue(dal.copy(calories = "0").problems().single().contains("more than zero"))
        assertTrue(dal.copy(fat = "-3").problems().single().startsWith("Fat"))
        assertTrue(dal.copy(protein = "lots").problems().single().startsWith("Protein"))
        assertEquals(2, ManualDishInput().problems().size)
    }

    @Test
    fun macrosThatDisagreeWithTheCaloriesAreFlaggedNotBlocked() {
        // 10p + 30c + 8f = 232 kcal: consistent with 240.
        val consistent = dal.copy(protein = "10", carbs = "30", fat = "8")
        assertNull(consistent.advisory())
        // A slipped digit: 80 g fat is 720 kcal on its own.
        val typo = dal.copy(protein = "10", carbs = "30", fat = "80")
        assertNotNull(typo.advisory())
        assertTrue(typo.advisory()!!.contains("240"))
        assertEquals(emptyList<String>(), typo.problems())
    }

    @Test
    fun portionsStepInTheUnitAndRestartWhenItChanges() {
        assertEquals(1.5, dal.steppedUp().quantity, 0.0)
        assertFalse(dal.copy(quantity = 0.5).canStepDown)
        assertEquals(0.5, dal.copy(quantity = 0.5).steppedDown().quantity, 0.0)

        val grams = dal.copy(quantity = 1.5).withUnit(PortionUnit.GRAMS)
        assertEquals(100.0, grams.quantity, 0.0)
        assertEquals(110.0, grams.steppedUp().quantity, 0.0)
        assertEquals(1.0, grams.withUnit(PortionUnit.ROTI).quantity, 0.0)
        assertEquals(dal.copy(quantity = 2.0), dal.copy(quantity = 2.0).withUnit(PortionUnit.KATORI))
    }

    @Test
    fun oneTypedDishIsConfirmedLikeAPhotoMeal() {
        assertEquals("Dinner logged · 1 dish · 241 kcal", loggedSummary(MealType.DINNER, 1, 240.6))
    }
}
