package com.kevinjones.fitmasala.remote

import com.kevinjones.fitmasala.data.prefs.LlmProvider
import com.kevinjones.fitmasala.data.remote.CulinaryLlmClient
import com.kevinjones.fitmasala.data.remote.ProviderBackend
import com.kevinjones.fitmasala.data.remote.LlmBackend
import com.kevinjones.fitmasala.data.remote.LlmRequest
import com.kevinjones.fitmasala.data.remote.LlmResult
import com.kevinjones.fitmasala.data.remote.apiJson
import com.kevinjones.fitmasala.data.remote.prompt.RecipeSchemas
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every call - meal estimates (#22) and Chef recipes (#24) - goes to the
 * provider chosen in Settings and nowhere else. No fallback: a failure is shown,
 * not retried on the other model, which would mix two estimation biases in one log.
 */
class ProviderRoutingTest {

    private class Recording(val name: String, val reply: LlmResult<String>) : LlmBackend {
        val calls = mutableListOf<LlmRequest>()
        override suspend fun complete(request: LlmRequest): LlmResult<String> { calls += request; return reply }
    }

    private val estimate = """{"containsFood":true,"items":[],
        "totalMacros":{"calories":300,"proteinG":10,"carbsG":40,"fatG":11,"fiberG":3},"overallConfidence":"medium"}"""

    private fun ok(model: String) = LlmResult.Success(estimate, estimate, model, 1, 1)

    @Test
    fun eachEstimateGoesToTheChosenProvider() = runTest {
        val anthropic = Recording("anthropic", ok("claude-opus-5"))
        val gemini = Recording("gemini", ok("gemini-3.1-flash"))
        var chosen = LlmProvider.GEMINI
        val backend = ProviderBackend(anthropic, gemini) { chosen }

        val first = CulinaryLlmClient(backend, apiJson()).estimateFromPhoto("AAAA", "lunch") as LlmResult.Success
        assertEquals("gemini-3.1-flash", first.model)

        chosen = LlmProvider.ANTHROPIC
        val second = CulinaryLlmClient(backend, apiJson()).estimateFromText("1 tsp ghee", "lunch") as LlmResult.Success
        assertEquals("claude-opus-5", second.model)
        assertEquals(1 to 1, anthropic.calls.size to gemini.calls.size)
    }

    @Test
    fun aFailingProviderIsNeverBackedUpByTheOther() = runTest {
        val anthropic = Recording("anthropic", ok("claude-opus-5"))
        val gemini = Recording("gemini", LlmResult.Failure.Network("timeout"))
        val result = ProviderBackend(anthropic, gemini) { LlmProvider.GEMINI }
            .complete(LlmRequest("s", "t", schema = RecipeSchemas.PHOTO_ESTIMATE))
        assertEquals(LlmResult.Failure.Network("timeout"), result)
        assertEquals(0, anthropic.calls.size)
    }

    @Test
    fun anUnreadableSettingMeansTheDefaultProvider() = runTest {
        val anthropic = Recording("anthropic", ok("claude-opus-5"))
        val gemini = Recording("gemini", ok("gemini-3.1-flash"))
        ProviderBackend(anthropic, gemini) { error("DataStore unavailable") }
            .complete(LlmRequest("s", "t", schema = RecipeSchemas.PHOTO_ESTIMATE))
        assertEquals(1 to 0, anthropic.calls.size to gemini.calls.size)
    }

    @Test
    fun theChefFollowsTheChosenProviderToo() = runTest {
        val recipe = """{"title":"Dal","region":"PUNJABI","provenanceNote":"n","cookingMethod":"TADKA","servings":2,
            "ingredients":[],"instructions":[],"techniqueNotes":[],
            "macrosPerServing":{"calories":300,"proteinG":15,"carbsG":40,"fatG":9,"fiberG":8},"portionDescription":"1 katori"}"""
        val anthropic = Recording("anthropic", LlmResult.Success(recipe, recipe, "claude-opus-5", 1, 1))
        val gemini = Recording("gemini", LlmResult.Success(recipe, recipe, "gemini-3.1-flash", 1, 1))
        var chosen = LlmProvider.GEMINI
        val client = CulinaryLlmClient(ProviderBackend(anthropic, gemini) { chosen }, apiJson())

        val fromGemini = client.generateRecipe("dal") as LlmResult.Success
        assertEquals("gemini-3.1-flash", fromGemini.model)
        assertEquals(0 to 1, anthropic.calls.size to gemini.calls.size)
        assertEquals(RecipeSchemas.RECIPE, gemini.calls.single().schema)

        chosen = LlmProvider.ANTHROPIC
        val fromClaude = client.generateRecipe("dal") as LlmResult.Success
        assertEquals("claude-opus-5", fromClaude.model)
        assertEquals(1 to 1, anthropic.calls.size to gemini.calls.size)
    }
}
