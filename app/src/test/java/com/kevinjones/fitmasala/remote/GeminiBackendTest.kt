package com.kevinjones.fitmasala.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.kevinjones.fitmasala.data.remote.GeminiAuthInterceptor
import com.kevinjones.fitmasala.data.remote.GeminiBackend
import com.kevinjones.fitmasala.data.remote.LlmRequest
import com.kevinjones.fitmasala.data.remote.LlmResult
import com.kevinjones.fitmasala.data.remote.api.GeminiApi
import com.kevinjones.fitmasala.data.remote.apiJson
import com.kevinjones.fitmasala.data.remote.prompt.GeminiSchema
import com.kevinjones.fitmasala.data.remote.prompt.RecipeSchemas
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Retrofit
import java.io.IOException

/**
 * Gemini estimates (#22), through the real Retrofit service and JSON converter:
 * only the network is replaced, by an interceptor that records the outgoing
 * request and answers with a canned response. So the URL, header and body are
 * what the app would really send, and each response is parsed as it would be.
 */
class GeminiBackendTest {

    private val json = apiJson()

    /** What reached the "network", and what it answered. */
    private class Wire(val code: Int, val body: String, val retryAfter: String? = null) {
        var sent: Request? = null
        var failWith: IOException? = null
    }

    private fun backend(wire: Wire, key: String? = "AIza-test-key", model: String? = ""): GeminiBackend {
        val client = OkHttpClient.Builder()
            .addInterceptor(GeminiAuthInterceptor { key })
            .addInterceptor(Interceptor { chain ->
                wire.sent = chain.request()
                wire.failWith?.let { throw it }
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(wire.code)
                    .message("canned")
                    .apply { wire.retryAfter?.let { header("retry-after", it) } }
                    .body(wire.body.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
        val api = Retrofit.Builder()
            .baseUrl(GeminiApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeminiApi::class.java)
        return GeminiBackend(api, json) { model }
    }

    private fun resource(path: String) =
        requireNotNull(javaClass.getResource("/gemini/$path")) { "missing fixture $path" }.readText()

    private val photo = LlmRequest(
        system = "You estimate Indian meals.",
        text = "Estimate the nutrition of this meal. It is my lunch.",
        imageJpegBase64 = "AAAA",
        schema = RecipeSchemas.PHOTO_ESTIMATE,
    )

    private fun ok(body: String) = Wire(200, body)

    private val answer = """{"containsFood":true}"""

    private fun success(
        text: String = answer,
        finishReason: String = "STOP",
        extraParts: String = "",
    ) = """{"candidates":[{"content":{"role":"model","parts":[$extraParts{"text":${json.encodeToString(String.serializer(), text)}}]},
        "finishReason":"$finishReason","index":0}],
        "usageMetadata":{"promptTokenCount":1500,"candidatesTokenCount":400,"thoughtsTokenCount":600,"totalTokenCount":2500},
        "modelVersion":"gemini-3.1-flash","responseId":"r1"}"""

    // --- What is sent ---------------------------------------------------------

    @Test
    fun theKeyTravelsInTheHeaderAndNeverInTheUrl() = runTest {
        val wire = ok(success())
        backend(wire, key = "AIza-secret").complete(photo)
        val sent = wire.sent!!
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent",
            sent.url.toString(),
        )
        assertEquals("AIza-secret", sent.header("x-goog-api-key"))
        assertNull(sent.url.queryParameter("key"))
        assertFalse(sent.url.toString().contains("AIza"))
        assertNull("no Anthropic header on a Google request", sent.header("x-api-key"))
    }

    @Test
    fun theBodyCarriesTheSystemPromptTheImageThenTheQuestionAndTheConvertedSchema() = runTest {
        val wire = ok(success())
        backend(wire).complete(photo)
        val body = json.parseToJsonElement(Buffer().also { wire.sent!!.body!!.writeTo(it) }.readUtf8()).jsonObject

        assertEquals(
            "You estimate Indian meals.",
            body["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content,
        )
        assertNull("the system instruction has no role", body["systemInstruction"]!!.jsonObject["role"])
        val content = body["contents"]!!.jsonArray.single().jsonObject
        assertEquals("user", content["role"]!!.jsonPrimitive.content)
        val (image, question) = content["parts"]!!.jsonArray.map { it.jsonObject }
        assertEquals("image/jpeg", image["inlineData"]!!.jsonObject["mimeType"]!!.jsonPrimitive.content)
        assertEquals("AAAA", image["inlineData"]!!.jsonObject["data"]!!.jsonPrimitive.content)
        assertEquals(photo.text, question["text"]!!.jsonPrimitive.content)

        val config = body["generationConfig"]!!.jsonObject
        assertEquals("application/json", config["responseMimeType"]!!.jsonPrimitive.content)
        assertEquals(GeminiSchema.from(RecipeSchemas.PHOTO_ESTIMATE), config["responseSchema"])
        assertEquals(GeminiApi.MAX_OUTPUT_TOKENS, config["maxOutputTokens"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun aTypedDishSendsNoImage() = runTest {
        val wire = ok(success())
        backend(wire).complete(photo.copy(imageJpegBase64 = null))
        val body = json.parseToJsonElement(Buffer().also { wire.sent!!.body!!.writeTo(it) }.readUtf8()).jsonObject
        val parts = body["contents"]!!.jsonArray.single().jsonObject["parts"]!!.jsonArray
        assertEquals(1, parts.size)
        assertEquals(setOf("text"), parts.single().jsonObject.keys)
    }

    @Test
    fun theModelFromSettingsWinsWithOrWithoutItsModelsPrefix() = runTest {
        val wire = ok(success())
        backend(wire, model = "gemini-2.5-flash").complete(photo)
        assertEquals("/v1beta/models/gemini-2.5-flash:generateContent", wire.sent!!.url.encodedPath)
        backend(wire, model = " models/gemini-2.5-pro ").complete(photo)
        assertEquals("/v1beta/models/gemini-2.5-pro:generateContent", wire.sent!!.url.encodedPath)
    }

    // --- What comes back --------------------------------------------------------

    @Test
    fun aGoodReplyIsTheTextWithoutThinkingTheReportedModelAndTokenCounts() = runTest {
        val result = backend(ok(success(extraParts = """{"text":"Let me look at the katori...","thought":true},"""))).complete(photo)
        assertEquals(
            LlmResult.Success(
                value = answer,
                rawJson = answer,
                model = "gemini-3.1-flash",
                inputTokens = 1500,
                outputTokens = 1000,
            ),
            result,
        )
    }

    @Test
    fun aMissingKeyNeverLeavesThePhone() = runTest {
        val wire = ok(success())
        assertEquals(LlmResult.Failure.MissingApiKey, backend(wire, key = " ").complete(photo))
        assertNull("nothing was sent", wire.sent)
    }

    @Test
    fun aNetworkErrorIsItsOwnFailure() = runTest {
        val wire = ok(success()).apply { failWith = IOException("timeout") }
        assertEquals(LlmResult.Failure.Network("timeout"), backend(wire).complete(photo))
    }

    @Test
    fun aRejectedKeyIsUnauthorizedEvenThoughGoogleSays400() = runTest {
        // Recorded from the live API with a malformed key in x-goog-api-key.
        val result = backend(Wire(400, resource("error-api-key-invalid.json"))).complete(photo)
        assertEquals(LlmResult.Failure.Unauthorized("API key not valid. Please pass a valid API key."), result)
    }

    @Test
    fun a403IsUnauthorized() = runTest {
        // Recorded from the live API with no key at all.
        val result = backend(Wire(403, resource("error-no-key.json"))).complete(photo)
        assertEquals(LlmResult.Failure.Unauthorized::class, result::class)
    }

    @Test
    fun aRateLimitCarriesGooglesRetryDelay() = runTest {
        val body = """{"error":{"code":429,"message":"Resource has been exhausted (e.g. check quota).","status":"RESOURCE_EXHAUSTED",
            "details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"37.2s"}]}}"""
        assertEquals(
            LlmResult.Failure.RateLimited(38, "Resource has been exhausted (e.g. check quota)."),
            backend(Wire(429, body)).complete(photo),
        )
        assertEquals(
            LlmResult.Failure.RateLimited(12, "Rate limited. Try again shortly."),
            backend(Wire(429, "", retryAfter = "12")).complete(photo),
        )
    }

    @Test
    fun otherHttpErrorsKeepTheirCodeAndMessage() = runTest {
        val body = """{"error":{"code":503,"message":"The model is overloaded. Please try again later.","status":"UNAVAILABLE"}}"""
        assertEquals(
            LlmResult.Failure.Http(503, "The model is overloaded. Please try again later."),
            backend(Wire(503, body)).complete(photo),
        )
        assertEquals(LlmResult.Failure.Http(500, "The API returned 500."), backend(Wire(500, "<html>")).complete(photo))
    }

    @Test
    fun aSafetyStopOrABlockedPromptIsARefusal() = runTest {
        assertEquals(
            LlmResult.Failure.Refused("SAFETY", "The model declined this request."),
            backend(ok(success(finishReason = "SAFETY"))).complete(photo),
        )
        val blocked = """{"promptFeedback":{"blockReason":"PROHIBITED_CONTENT"},"modelVersion":"gemini-3.1-flash"}"""
        assertEquals(
            LlmResult.Failure.Refused("PROHIBITED_CONTENT", "The model declined this request."),
            backend(ok(blocked)).complete(photo),
        )
    }

    @Test
    fun aMaxTokensStopIsTruncatedAndAnyOtherEarlyStopIsUnparseable() = runTest {
        assertEquals(LlmResult.Failure.Truncated, backend(ok(success(text = """{"contai""", finishReason = "MAX_TOKENS"))).complete(photo))
        val other = backend(ok(success(finishReason = "MALFORMED_RESPONSE"))).complete(photo)
        assertEquals(LlmResult.Failure.Unparseable(answer, "The model stopped early (MALFORMED_RESPONSE)."), other)
    }

    @Test
    fun noCandidateOrNoTextIsUnparseable() = runTest {
        assertEquals(
            LlmResult.Failure.Unparseable("", "The reply contained no answer."),
            backend(ok("""{"modelVersion":"gemini-3.1-flash"}""")).complete(photo),
        )
        assertEquals(
            LlmResult.Failure.Unparseable("", "The reply contained no text."),
            backend(ok(success(text = "  "))).complete(photo),
        )
    }

}
