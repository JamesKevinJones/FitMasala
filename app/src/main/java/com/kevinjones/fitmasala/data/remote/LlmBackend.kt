package com.kevinjones.fitmasala.data.remote

import kotlinx.serialization.json.JsonObject

/**
 * One call to a model provider: a system prompt, a user turn (text, optionally
 * with one photo) and the JSON schema the reply must match.
 *
 * Everything provider-neutral - what to ask, the schema, parsing the answer,
 * validation and advisories - stays in [CulinaryLlmClient]. A backend only
 * speaks its provider's wire format: request shape, headers, stop reasons and
 * HTTP errors, each mapped onto the same [LlmResult.Failure] cases so every
 * screen handles every provider the same way.
 */
interface LlmBackend {

    /**
     * The model's JSON text as [LlmResult.Success.value] (and
     * [LlmResult.Success.rawJson]), with the model name and token counts; or a
     * [LlmResult.Failure]. Never parses the JSON - that is the client's job.
     */
    suspend fun complete(request: LlmRequest): LlmResult<String>
}

/** What to ask, independent of any provider's wire format. */
data class LlmRequest(
    val system: String,
    /** The user's question. */
    val text: String,
    /** One photo sent with the question, base64 JPEG; null for a text-only request. */
    val imageJpegBase64: String? = null,
    /** The JSON schema the reply is held to. */
    val schema: JsonObject,
)
