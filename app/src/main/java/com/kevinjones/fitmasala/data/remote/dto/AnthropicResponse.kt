package com.kevinjones.fitmasala.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnthropicResponse(
    val id: String,
    val model: String,
    val role: String = "assistant",
    val content: List<ResponseContentBlock> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
    /**
     * Populated ONLY when stopReason == "refusal"; null for end_turn,
     * max_tokens, tool_use and everything else. Always guard before reading.
     */
    @SerialName("stop_details") val stopDetails: StopDetails? = null,
    val usage: Usage? = null,
) {
    /** Concatenated text blocks. Thinking blocks are skipped. */
    val text: String
        get() = content.filterIsInstance<ResponseContentBlock.Text>()
            .joinToString("") { it.text }
            .trim()

    val wasRefused: Boolean get() = stopReason == "refusal"

    /**
     * A truncated response is not a failed one at the HTTP layer, so it has to
     * be checked explicitly — otherwise half a recipe gets parsed as if whole.
     */
    val wasTruncated: Boolean get() = stopReason == "max_tokens"
}

@Serializable
data class StopDetails(
    val type: String? = null,
    /** Open set — "cyber", "bio", … or null. Never match exhaustively. */
    val category: String? = null,
    val explanation: String? = null,
)

@Serializable
data class Usage(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
    @SerialName("cache_read_input_tokens") val cacheReadInputTokens: Int = 0,
    @SerialName("cache_creation_input_tokens") val cacheCreationInputTokens: Int = 0,
)

/**
 * Only `text` is actually consumed. The others exist so that a response
 * containing them deserializes instead of throwing — and [Unknown] catches block
 * types added to the API after this was written, which would otherwise crash the
 * app on a discriminator kotlinx has never heard of. See the
 * `polymorphicDefaultDeserializer` registration in NetworkModule.
 */
@Serializable
sealed class ResponseContentBlock {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : ResponseContentBlock()

    @Serializable
    @SerialName("thinking")
    data class Thinking(
        val thinking: String = "",
        val signature: String? = null,
    ) : ResponseContentBlock()

    @Serializable
    @SerialName("redacted_thinking")
    data class RedactedThinking(val data: String = "") : ResponseContentBlock()

    @Serializable
    data object Unknown : ResponseContentBlock()
}

/** Shape of a non-2xx body: {"type":"error","error":{"type":..,"message":..}} */
@Serializable
data class AnthropicErrorEnvelope(
    val type: String? = null,
    val error: AnthropicError? = null,
)

@Serializable
data class AnthropicError(
    val type: String? = null,
    val message: String? = null,
)
