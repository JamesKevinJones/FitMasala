package com.kevinjones.fitmasala.remote

import com.kevinjones.fitmasala.data.remote.EstimateRequests
import com.kevinjones.fitmasala.data.remote.dto.RequestContentBlock
import com.kevinjones.fitmasala.data.remote.prompt.RecipeSchemas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A dish typed onto the review sheet joins a photographed meal, so it must be
 * asked for in the same shape and judged by the same rules - otherwise the two
 * disagree about what "1 katori" means inside one Meal.
 */
class EstimateRequestsTest {

    private val photo = EstimateRequests.forPhoto(base64Jpeg = "AAAA", mealType = "lunch")
    private val text = EstimateRequests.forText(description = "  1 tsp ghee ", mealType = "lunch")

    @Test
    fun aTypedDishIsAskedForWithThePhotoEstimatesSchema() {
        assertSame(RecipeSchemas.PHOTO_ESTIMATE, text.schema)
        assertSame(photo.schema, text.schema)
    }

    @Test
    fun aTypedDishIsTextOnly() {
        val block = text.message.content.single() as RequestContentBlock.Text
        assertEquals("user", text.message.role)
        assertTrue(block.text.startsWith("Estimate the nutrition of: 1 tsp ghee"))
        assertTrue(block.text.contains("part of my lunch"))
    }

    @Test
    fun aPhotoIsStillImageFirstThenTheQuestion() {
        assertTrue(photo.message.content[0] is RequestContentBlock.Image)
        assertTrue(photo.message.content[1] is RequestContentBlock.Text)
    }

    @Test
    fun bothAreJudgedByTheSamePortionRulesAndHonestyBar() {
        val portionRule = "katori for dal, sabzi, rice and curd"
        val honesty = "Do not\nround downward to be encouraging."
        assertTrue(photo.system.contains(portionRule))
        assertTrue(text.system.contains(portionRule))
        assertTrue(photo.system.contains(honesty))
        assertTrue(text.system.contains(honesty))
    }

    @Test
    fun theTypedPromptDoesNotTalkAboutAPhoto() {
        assertFalse(text.system.contains("photograph", ignoreCase = true))
        // Sections are joined, not interpolated: nothing is left indented.
        assertFalse(text.system.lines().any { it.startsWith("        ") })
        assertFalse(photo.system.lines().any { it.startsWith("        ") })
    }
}
