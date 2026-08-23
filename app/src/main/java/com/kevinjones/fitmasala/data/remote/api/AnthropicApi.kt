package com.kevinjones.fitmasala.data.remote.api

import com.kevinjones.fitmasala.data.remote.dto.AnthropicRequest
import com.kevinjones.fitmasala.data.remote.dto.AnthropicResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AnthropicApi {

    /**
     * Returns the raw `Response` rather than the body so the client can read the
     * status code and the error envelope. A 401 from a mistyped key and a 529
     * from an overloaded API need completely different messages, and Retrofit's
     * default `HttpException` throws away the body that distinguishes them.
     */
    @POST("v1/messages")
    suspend fun messages(@Body request: AnthropicRequest): Response<AnthropicResponse>

    companion object {
        const val BASE_URL = "https://api.anthropic.com/"
        const val VERSION_HEADER = "2023-06-01"

        /** Enables the `fallbacks: "default"` scalar form. */
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

        /**
         * Opus 5, per the app's default. The user can override it in Settings —
         * this is only what ships if they never touch it.
         */
        const val DEFAULT_MODEL = "claude-opus-5"

        /**
         * Non-streaming, so this stays under the client HTTP timeout. A recipe
         * with instructions and a full ingredient list fits comfortably.
         */
        const val MAX_TOKENS = 16_000
    }
}
