package com.kevinjones.fitmasala.data.remote.api

import com.kevinjones.fitmasala.data.remote.dto.GeminiRequest
import com.kevinjones.fitmasala.data.remote.dto.GeminiResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * The Gemini API's `models.generateContent`, as its discovery document
 * (revision 20260927) describes it: `POST v1beta/{+model}:generateContent`.
 *
 * The key travels in the `x-goog-api-key` header, added by
 * [com.kevinjones.fitmasala.data.remote.GeminiAuthInterceptor] - the header the
 * official SDKs use. Never the `?key=` query parameter: a URL ends up in logs.
 */
interface GeminiApi {

    /** [model] without the `models/` prefix, e.g. `gemini-flash-latest`. */
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Body request: GeminiRequest,
    ): Response<GeminiResponse>

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/"
        const val KEY_HEADER = "x-goog-api-key"

        /**
         * The name Google's own SDK READMEs use today. It is an alias that moves
         * with new releases, so each logged Dish records the `modelVersion` the
         * API reports (#20) - a silent model change shows up in the log. Pin a
         * specific model in Settings to hold it still.
         */
        const val DEFAULT_MODEL = "gemini-flash-latest"

        /** Includes thinking tokens, which count against the output limit. */
        const val MAX_OUTPUT_TOKENS = 16_000
    }
}
