package com.kevinjones.fitmasala.remote

import com.kevinjones.fitmasala.data.remote.AnthropicBackend
import com.kevinjones.fitmasala.data.remote.CulinaryLlmClient
import com.kevinjones.fitmasala.data.remote.LlmBackend
import com.kevinjones.fitmasala.data.remote.LlmRequest
import com.kevinjones.fitmasala.data.remote.LlmResult
import com.kevinjones.fitmasala.data.remote.MissingApiKeyException
import com.kevinjones.fitmasala.data.remote.api.AnthropicApi
import com.kevinjones.fitmasala.data.remote.apiJson
import com.kevinjones.fitmasala.data.remote.dto.AnthropicRequest
import com.kevinjones.fitmasala.data.remote.dto.AnthropicResponse
import com.kevinjones.fitmasala.data.remote.prompt.RecipeSchemas
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * The Anthropic half of the transport split (#21). The request bytes are pinned
 * against goldens captured from the client BEFORE the split, so moving the code
 * provably changed nothing on the wire; every failure case maps as it did.
 *
 * The goldens embed the prompts. A deliberate prompt or schema change means
 * regenerating them - the diff of the golden is then the review of the change.
 */
class AnthropicBackendTest {

    private val json = apiJson()

    /** Records what would have been sent, then answers with [reply]. */
    private class FakeApi(val reply: () -> Response<AnthropicResponse>) : AnthropicApi {
        val sent = mutableListOf<AnthropicRequest>()
        override suspend fun messages(request: AnthropicRequest): Response<AnthropicResponse> {
            sent += request
            return reply()
        }
    }

    private fun backend(api: AnthropicApi, model: String? = "") = AnthropicBackend(api, json) { model }

    private fun golden(name: String) =
        requireNotNull(javaClass.getResource("/anthropic/$name.json")) { "missing golden $name" }.readText()

    private fun ok(body: String): Response<AnthropicResponse> =
        Response.success(json.decodeFromString<AnthropicResponse>(body))

    private fun httpError(code: Int, body: String = "", retryAfter: String? = null): Response<AnthropicResponse> {
        val raw = okhttp3.Response.Builder()
            .code(code)
            .message("error")
            .protocol(Protocol.HTTP_1_1)
            .request(okhttp3.Request.Builder().url("https://api.anthropic.com/v1/messages").build())
            .apply { if (retryAfter != null) header("retry-after", retryAfter) }
            .build()
        return Response.error(body.toResponseBody(), raw)
    }

    private val textRequest = LlmRequest(system = "sys", text = "hello", schema = RecipeSchemas.PHOTO_ESTIMATE)

    // --- The wire format is unchanged ------------------------------------------

    @Test
    fun everyCulinaryRequestIsSentByteForByteAsBeforeTheSplit() = runTest {
        val api = FakeApi { throw IOException("captured") }
        val client = CulinaryLlmClient(backend(api), json)

        client.generateRecipe("paneer, spinach, onion", "dinner", 2)
        client.estimateFromPhoto("AAAA", "lunch")
        client.estimateFromText("1 tsp ghee", "lunch")

        listOf("recipe", "photo", "text").zip(api.sent).forEach { (name, request) ->
            assertEquals(name, golden(name), json.encodeToString(AnthropicRequest.serializer(), request))
        }
    }

    @Test
    fun theModelFromSettingsWinsAndBlankOrUnreadableMeansTheDefault() = runTest {
        val api = FakeApi { throw IOException("captured") }
        backend(api, "claude-sonnet-5").complete(textRequest)
        backend(api, "  ").complete(textRequest)
        backend(api, null).complete(textRequest)
        AnthropicBackend(api, json) { error("DataStore unavailable") }.complete(textRequest)
        assertEquals(
            listOf("claude-sonnet-5", AnthropicApi.DEFAULT_MODEL, AnthropicApi.DEFAULT_MODEL, AnthropicApi.DEFAULT_MODEL),
            api.sent.map { it.model },
        )
    }

    // --- Every failure maps as before -------------------------------------------

    private suspend fun answer(reply: () -> Response<AnthropicResponse>) = backend(FakeApi(reply)).complete(textRequest)

    @Test
    fun aMissingKeyAndANetworkErrorAreTheirOwnFailures() = runTest {
        assertEquals(LlmResult.Failure.MissingApiKey, answer { throw MissingApiKeyException() })
        assertEquals(LlmResult.Failure.Network("timeout"), answer { throw IOException("timeout") })
    }

    @Test
    fun httpErrorsMapByStatusWithTheApisOwnMessage() = runTest {
        val envelope = """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""
        assertEquals(LlmResult.Failure.Unauthorized("invalid x-api-key"), answer { httpError(401, envelope) })
        assertEquals(
            LlmResult.Failure.Unauthorized("The API rejected that key. Check it in Settings."),
            answer { httpError(403) },
        )
        assertEquals(
            LlmResult.Failure.RateLimited(30, "Rate limited. Try again shortly."),
            answer { httpError(429, retryAfter = "30") },
        )
        assertEquals(
            LlmResult.Failure.RateLimited(null, "Rate limited. Try again shortly."),
            answer { httpError(529) },
        )
        assertEquals(LlmResult.Failure.Http(500, "The API returned 500."), answer { httpError(500, "not json") })
    }

    @Test
    fun aRefusalOrTruncationIsCaughtBeforeTheTextIsRead() = runTest {
        val refused = answer {
            ok(
                """{"id":"m","model":"claude-opus-5","content":[{"type":"text","text":"{}"}],
                "stop_reason":"refusal","stop_details":{"category":"cyber","explanation":"Declined."}}""",
            )
        }
        assertEquals(LlmResult.Failure.Refused("cyber", "Declined."), refused)

        val truncated = answer {
            ok("""{"id":"m","model":"claude-opus-5","content":[{"type":"text","text":"{\"a\":"}],"stop_reason":"max_tokens"}""")
        }
        assertEquals(LlmResult.Failure.Truncated, truncated)
    }

    @Test
    fun anEmptyBodyOrBlankTextIsUnparseable() = runTest {
        assertEquals(
            LlmResult.Failure.Unparseable("", "The API returned an empty body."),
            answer { Response.success(null) },
        )
        assertEquals(
            LlmResult.Failure.Unparseable("", "The reply contained no text."),
            answer { ok("""{"id":"m","model":"claude-opus-5","content":[{"type":"thinking","thinking":""}],"stop_reason":"end_turn"}""") },
        )
    }

    @Test
    fun aGoodReplyIsTheTextWithTheModelAndTokenCounts() = runTest {
        val result = answer {
            ok(
                """{"id":"m","model":"claude-opus-5-20260901","content":[{"type":"some_future_block"},
                {"type":"text","text":"{\"ok\":true}"}],"stop_reason":"end_turn",
                "usage":{"input_tokens":1200,"output_tokens":300}}""",
            )
        }
        assertEquals(
            LlmResult.Success(
                value = """{"ok":true}""",
                rawJson = """{"ok":true}""",
                model = "claude-opus-5-20260901",
                inputTokens = 1200,
                outputTokens = 300,
            ),
            result,
        )
    }

    // --- The client's half: parsing and advisories, whatever the backend --------

    private class CannedBackend(val reply: LlmResult<String>) : LlmBackend {
        override suspend fun complete(request: LlmRequest) = reply
    }

    private fun canned(text: String) = CannedBackend(LlmResult.Success(text, text, "any-model", 10, 20))

    @Test
    fun textThatDoesNotMatchTheSchemaIsUnparseableWithTheRawTextKept() = runTest {
        val result = CulinaryLlmClient(canned("""{"nope":1}"""), json).estimateFromPhoto("AAAA", "lunch")
        assertTrue(result is LlmResult.Failure.Unparseable)
        assertEquals("""{"nope":1}""", (result as LlmResult.Failure.Unparseable).raw)
    }

    @Test
    fun aParsedEstimateCarriesTheBackendsModelTokensAndTheClientsAdvisories() = runTest {
        val noFood = """{"containsFood":false,"items":[],
            "totalMacros":{"calories":0,"proteinG":0,"carbsG":0,"fatG":0,"fiberG":0},"overallConfidence":"low"}"""
        val result = CulinaryLlmClient(canned(noFood), json).estimateFromPhoto("AAAA", "lunch") as LlmResult.Success
        assertEquals("any-model", result.model)
        assertEquals(10 to 20, result.inputTokens to result.outputTokens)
        assertTrue(result.advisories.contains("No food was found in the photo."))
    }

    @Test
    fun aBackendFailurePassesStraightThrough() = runTest {
        val result = CulinaryLlmClient(CannedBackend(LlmResult.Failure.Truncated), json).generateRecipe("dal")
        assertEquals(LlmResult.Failure.Truncated, result)
    }
}
