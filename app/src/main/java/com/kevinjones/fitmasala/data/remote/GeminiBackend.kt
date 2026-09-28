package com.kevinjones.fitmasala.data.remote

import com.kevinjones.fitmasala.data.prefs.SettingsStore
import com.kevinjones.fitmasala.data.remote.api.GeminiApi
import com.kevinjones.fitmasala.data.remote.dto.GeminiBlob
import com.kevinjones.fitmasala.data.remote.dto.GeminiContent
import com.kevinjones.fitmasala.data.remote.dto.GeminiErrorEnvelope
import com.kevinjones.fitmasala.data.remote.dto.GeminiGenerationConfig
import com.kevinjones.fitmasala.data.remote.dto.GeminiPart
import com.kevinjones.fitmasala.data.remote.dto.GeminiRequest
import com.kevinjones.fitmasala.data.remote.dto.GeminiResponse
import com.kevinjones.fitmasala.data.remote.prompt.GeminiSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * The Gemini API behind [LlmBackend]: the same system prompt, photo and schema
 * as every other backend, with JSON enforced by `generationConfig.responseSchema`
 * (never prompt instructions, never prefill). Each Gemini outcome maps onto the
 * same [LlmResult.Failure] cases Anthropic's do, so every screen already knows
 * what to show.
 *
 * Shapes, field names and reasons come from the API's discovery document
 * (revision 20260927); the two key errors from real responses of the live API.
 */
@Singleton
class GeminiBackend internal constructor(
    private val api: GeminiApi,
    private val json: Json,
    private val modelOverride: suspend () -> String?,
) : LlmBackend {

    @Inject
    constructor(api: GeminiApi, settingsStore: SettingsStore, json: Json) :
        this(api, json, { settingsStore.current().geminiModelId })

    override suspend fun complete(request: LlmRequest): LlmResult<String> = withContext(Dispatchers.IO) {
        val model = runCatching { modelOverride() }.getOrNull()
            ?.trim()?.removePrefix("models/")?.takeIf { it.isNotBlank() }
            ?: GeminiApi.DEFAULT_MODEL

        val body = GeminiRequest(
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = request.system))),
            contents = listOf(
                GeminiContent(
                    role = "user",
                    // Image first, then the question - the same order as Anthropic.
                    parts = listOfNotNull(
                        request.imageJpegBase64?.let { GeminiPart(inlineData = GeminiBlob("image/jpeg", it)) },
                        GeminiPart(text = request.text),
                    ),
                ),
            ),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                responseSchema = GeminiSchema.from(request.schema),
                maxOutputTokens = GeminiApi.MAX_OUTPUT_TOKENS,
            ),
        )

        val response = try {
            api.generateContent(model, body)
        } catch (e: MissingApiKeyException) {
            return@withContext LlmResult.Failure.MissingApiKey
        } catch (e: IOException) {
            return@withContext LlmResult.Failure.Network(e.message ?: "Couldn't reach the API.")
        }

        interpret(response, model)
    }

    private fun interpret(response: Response<GeminiResponse>, requestedModel: String): LlmResult<String> {
        if (!response.isSuccessful) return httpFailure(response)

        val body = response.body()
            ?: return LlmResult.Failure.Unparseable("", "The API returned an empty body.")

        // The prompt itself was blocked: there is no candidate at all.
        val candidate = body.candidates.firstOrNull()
            ?: return body.promptFeedback?.blockReason
                ?.takeUnless { it == "BLOCK_REASON_UNSPECIFIED" }
                ?.let { LlmResult.Failure.Refused(category = it, message = DECLINED) }
                ?: LlmResult.Failure.Unparseable("", "The reply contained no answer.")

        // Checked before the text, which may be a misleading fragment.
        when (val reason = candidate.finishReason) {
            null, "STOP", "FINISH_REASON_UNSPECIFIED" -> Unit
            "MAX_TOKENS" -> return LlmResult.Failure.Truncated
            in REFUSALS -> return LlmResult.Failure.Refused(category = reason, message = candidate.finishMessage ?: DECLINED)
            else -> return LlmResult.Failure.Unparseable(
                raw = candidate.text(),
                message = "The model stopped early ($reason).",
            )
        }

        val text = candidate.text()
        if (text.isBlank()) return LlmResult.Failure.Unparseable("", "The reply contained no text.")

        val usage = body.usageMetadata
        return LlmResult.Success(
            value = text,
            rawJson = text,
            model = body.modelVersion ?: requestedModel,
            inputTokens = usage?.promptTokenCount ?: 0,
            // Thinking is billed as output, as it is on Anthropic.
            outputTokens = (usage?.candidatesTokenCount ?: 0) + (usage?.thoughtsTokenCount ?: 0),
        )
    }

    /** The answer's text parts, without thinking. */
    private fun com.kevinjones.fitmasala.data.remote.dto.GeminiCandidate.text(): String =
        content?.parts.orEmpty().filter { it.thought != true }.mapNotNull { it.text }.joinToString("").trim()

    private fun httpFailure(response: Response<GeminiResponse>): LlmResult.Failure {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
        val error = runCatching { json.decodeFromString<GeminiErrorEnvelope>(raw).error }.getOrNull()
        val message = error?.message
        val reasons = error?.details.orEmpty().mapNotNull { it["reason"]?.jsonPrimitive?.contentOrNull }

        return when {
            // A bad key is a 400, not a 401: INVALID_ARGUMENT with reason API_KEY_INVALID.
            "API_KEY_INVALID" in reasons || response.code() == 401 || response.code() == 403 ->
                LlmResult.Failure.Unauthorized(message ?: "Google rejected that key. Check it in Settings.")
            response.code() == 429 -> LlmResult.Failure.RateLimited(
                retryAfterSeconds = retryDelaySeconds(error) ?: response.headers()["retry-after"]?.toLongOrNull(),
                message = message ?: "Rate limited. Try again shortly.",
            )
            else -> LlmResult.Failure.Http(
                code = response.code(),
                message = message ?: "The API returned ${response.code()}.",
            )
        }
    }

    /** google.rpc.RetryInfo's `retryDelay`, a Duration such as "38s" or "1.5s". */
    private fun retryDelaySeconds(error: com.kevinjones.fitmasala.data.remote.dto.GeminiError?): Long? =
        error?.details.orEmpty()
            .firstOrNull { it["@type"]?.jsonPrimitive?.contentOrNull?.endsWith("google.rpc.RetryInfo") == true }
            ?.get("retryDelay")?.jsonPrimitive?.contentOrNull
            ?.removeSuffix("s")?.toDoubleOrNull()
            ?.let { ceil(it).toLong() }

    private companion object {
        const val DECLINED = "The model declined this request."

        /** Finish reasons that mean "won't", not "couldn't" - the photo, not the call, is the problem. */
        val REFUSALS = setOf(
            "SAFETY", "RECITATION", "LANGUAGE", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII",
            "IMAGE_SAFETY", "IMAGE_PROHIBITED_CONTENT", "IMAGE_RECITATION",
        )
    }
}
