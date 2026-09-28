package com.kevinjones.fitmasala.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/*
 * Gemini API wire types - only the fields the app sends or reads, named as in
 * the API's discovery document (revision 20260927). Unknown response fields are
 * ignored by the app's Json configuration.
 */

@Serializable
data class GeminiRequest(
    val systemInstruction: GeminiContent? = null,
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig,
)

@Serializable
data class GeminiContent(
    /** "user" or "model"; left out of the system instruction. */
    val role: String? = null,
    val parts: List<GeminiPart> = emptyList(),
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiBlob? = null,
    /** True on a thinking part, which is never the answer. */
    val thought: Boolean? = null,
)

@Serializable
data class GeminiBlob(val mimeType: String, val data: String)

@Serializable
data class GeminiGenerationConfig(
    val responseMimeType: String,
    /** The typed OpenAPI-subset `Schema`, converted from the app's JSON Schema. */
    val responseSchema: JsonObject,
    val maxOutputTokens: Int,
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val promptFeedback: GeminiPromptFeedback? = null,
    val usageMetadata: GeminiUsage? = null,
    /** The concrete model that answered, even when an alias was asked for. */
    val modelVersion: String? = null,
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
    val finishMessage: String? = null,
)

@Serializable
data class GeminiPromptFeedback(val blockReason: String? = null)

@Serializable
data class GeminiUsage(
    val promptTokenCount: Int = 0,
    val candidatesTokenCount: Int = 0,
    val thoughtsTokenCount: Int = 0,
)

/** Google's standard error body: {"error":{"code":..,"message":..,"status":..,"details":[..]}}. */
@Serializable
data class GeminiErrorEnvelope(val error: GeminiError? = null)

@Serializable
data class GeminiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null,
    /** google.rpc detail messages, each tagged by "@type". */
    val details: List<JsonObject> = emptyList(),
)
