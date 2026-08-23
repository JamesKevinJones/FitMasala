package com.kevinjones.fitmasala.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Wire types for POST https://api.anthropic.com/v1/messages.
 *
 * Hand-rolled rather than pulled from the official SDK because the app talks to
 * the API directly over Retrofit (no backend), and the Java SDK would drag a
 * large dependency into an APK for one endpoint.
 */
@Serializable
data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<AnthropicMessage>,
    val system: String? = null,
    val thinking: ThinkingConfig? = null,
    @SerialName("output_config") val outputConfig: OutputConfig? = null,
    val stream: Boolean = false,
    /**
     * Server-side refusal fallback. On a policy decline the API re-runs the same
     * request on another model inside the same call, instead of the request
     * simply stopping. "default" routes by refusal category so there is no model
     * list to maintain — it pairs with the `server-side-fallback-2026-07-01`
     * beta header, and mixing that header with the array form is a 400.
     */
    val fallbacks: String? = null,
)

/**
 * Adaptive thinking. `budget_tokens` is REMOVED on Opus 5 and returns a 400 —
 * depth is controlled through [OutputConfig.effort] instead.
 */
@Serializable
data class ThinkingConfig(
    val type: String = "adaptive",
    /**
     * Default is "omitted", which returns thinking blocks with empty text. The
     * app never shows reasoning, so the default is correct here — thinking still
     * happens and is still billed either way.
     */
    val display: String? = null,
)

@Serializable
data class OutputConfig(
    /** low | medium | high | xhigh | max. Default high. */
    val effort: String? = null,
    val format: OutputFormat? = null,
)

/**
 * Structured output. This is what guarantees the macro JSON parses.
 *
 * The alternative — prefilling an assistant turn with `{` to force JSON — is
 * REJECTED WITH A 400 on Opus 5 and the whole 4.6+ family. Anything remembered
 * from older Claude integrations that reaches for prefill is stale.
 */
@Serializable
data class OutputFormat(
    val type: String = "json_schema",
    val schema: JsonObject,
)

@Serializable
data class AnthropicMessage(
    val role: String,
    val content: List<RequestContentBlock>,
) {
    companion object {
        fun userText(text: String) =
            AnthropicMessage("user", listOf(RequestContentBlock.Text(text)))

        fun assistantText(text: String) =
            AnthropicMessage("assistant", listOf(RequestContentBlock.Text(text)))

        /** Image first, then the question — the documented ordering for vision. */
        fun userImageAndText(base64Jpeg: String, text: String) = AnthropicMessage(
            role = "user",
            content = listOf(
                RequestContentBlock.Image(
                    source = ImageSource(
                        type = "base64",
                        mediaType = "image/jpeg",
                        data = base64Jpeg,
                    ),
                ),
                RequestContentBlock.Text(text),
            ),
        )
    }
}

@Serializable
sealed class RequestContentBlock {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : RequestContentBlock()

    @Serializable
    @SerialName("image")
    data class Image(val source: ImageSource) : RequestContentBlock()
}

@Serializable
data class ImageSource(
    val type: String = "base64",
    @SerialName("media_type") val mediaType: String = "image/jpeg",
    /** Base64 with NO newlines — a wrapped payload is rejected. */
    val data: String,
)
