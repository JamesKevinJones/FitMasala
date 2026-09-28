package com.kevinjones.fitmasala.data.remote

import com.kevinjones.fitmasala.data.prefs.SettingsStore
import com.kevinjones.fitmasala.data.remote.api.AnthropicApi
import com.kevinjones.fitmasala.data.remote.dto.AnthropicErrorEnvelope
import com.kevinjones.fitmasala.data.remote.dto.AnthropicMessage
import com.kevinjones.fitmasala.data.remote.dto.AnthropicRequest
import com.kevinjones.fitmasala.data.remote.dto.AnthropicResponse
import com.kevinjones.fitmasala.data.remote.dto.OutputConfig
import com.kevinjones.fitmasala.data.remote.dto.OutputFormat
import com.kevinjones.fitmasala.data.remote.dto.ThinkingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Anthropic Messages API behind [LlmBackend]: structured output through
 * `output_config.format` (never prefill, which is a 400 on Opus 5), adaptive
 * thinking at high effort, and server-side fallbacks.
 *
 * [modelOverride] is the model id from Settings; blank or unreadable means
 * [AnthropicApi.DEFAULT_MODEL]. It is a function rather than the settings store
 * so the request can be pinned in a plain JVM test.
 */
@Singleton
class AnthropicBackend internal constructor(
    private val api: AnthropicApi,
    private val json: Json,
    private val modelOverride: suspend () -> String?,
) : LlmBackend {

    @Inject
    constructor(api: AnthropicApi, settingsStore: SettingsStore, json: Json) :
        this(api, json, { settingsStore.current().modelId })

    override suspend fun complete(request: LlmRequest): LlmResult<String> = withContext(Dispatchers.IO) {
        val model = runCatching { modelOverride() }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: AnthropicApi.DEFAULT_MODEL

        val body = AnthropicRequest(
            model = model,
            maxTokens = AnthropicApi.MAX_TOKENS,
            system = request.system,
            messages = listOf(request.toMessage()),
            // Adaptive is the only on-mode here; budget_tokens is a 400.
            thinking = ThinkingConfig(type = "adaptive"),
            outputConfig = OutputConfig(
                effort = "high",
                format = OutputFormat(schema = request.schema),
            ),
            // Rescues a policy decline inside the same call instead of stopping.
            fallbacks = "default",
        )

        val response = try {
            api.messages(body)
        } catch (e: MissingApiKeyException) {
            return@withContext LlmResult.Failure.MissingApiKey
        } catch (e: IOException) {
            return@withContext LlmResult.Failure.Network(
                e.message ?: "Couldn't reach the API.",
            )
        }

        interpret(response)
    }

    /** Image first, then the question - the documented ordering for vision. */
    private fun LlmRequest.toMessage(): AnthropicMessage =
        imageJpegBase64?.let { AnthropicMessage.userImageAndText(base64Jpeg = it, text = text) }
            ?: AnthropicMessage.userText(text)

    private fun interpret(response: Response<AnthropicResponse>): LlmResult<String> {
        if (!response.isSuccessful) return httpFailure(response)

        val body = response.body()
            ?: return LlmResult.Failure.Unparseable("", "The API returned an empty body.")

        // Checked before the content: a refused or truncated response still
        // carries text that would parse into something misleading.
        if (body.wasRefused) {
            return LlmResult.Failure.Refused(
                category = body.stopDetails?.category,
                message = body.stopDetails?.explanation ?: "The model declined this request.",
            )
        }
        if (body.wasTruncated) return LlmResult.Failure.Truncated

        val text = body.text
        if (text.isBlank()) {
            return LlmResult.Failure.Unparseable("", "The reply contained no text.")
        }

        return LlmResult.Success(
            value = text,
            rawJson = text,
            model = body.model,
            inputTokens = body.usage?.inputTokens ?: 0,
            outputTokens = body.usage?.outputTokens ?: 0,
        )
    }

    private fun httpFailure(response: Response<AnthropicResponse>): LlmResult.Failure {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
        val apiMessage = runCatching {
            json.decodeFromString<AnthropicErrorEnvelope>(raw).error?.message
        }.getOrNull()

        return when (response.code()) {
            401, 403 -> LlmResult.Failure.Unauthorized(
                apiMessage ?: "The API rejected that key. Check it in Settings.",
            )
            429, 529 -> LlmResult.Failure.RateLimited(
                retryAfterSeconds = response.headers()["retry-after"]?.toLongOrNull(),
                message = apiMessage ?: "Rate limited. Try again shortly.",
            )
            else -> LlmResult.Failure.Http(
                code = response.code(),
                message = apiMessage ?: "The API returned ${response.code()}.",
            )
        }
    }
}
