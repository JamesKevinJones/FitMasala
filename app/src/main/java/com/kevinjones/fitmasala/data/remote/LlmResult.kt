package com.kevinjones.fitmasala.data.remote

/**
 * Every way a call can end, as data rather than exceptions.
 *
 * The failure modes are enumerated because they need genuinely different UI: a
 * missing key opens Settings, a rate limit offers retry, a refusal is not
 * retryable at all, and an inconsistent macro set is a *successful* call whose
 * numbers should not be trusted. Collapsing these into one "network error"
 * string is how an app ends up telling someone to check their connection when
 * their key is wrong.
 */
sealed interface LlmResult<out T> {

    data class Success<T>(
        val value: T,
        val rawJson: String,
        val model: String,
        val inputTokens: Int,
        val outputTokens: Int,
        /** Non-empty when the response parsed but something about it is suspect. */
        val advisories: List<String> = emptyList(),
    ) : LlmResult<T>

    sealed interface Failure : LlmResult<Nothing> {
        val message: String

        /** No key in Settings. Actionable, not an error state. */
        data object MissingApiKey : Failure {
            override val message = "No API key set. Add one in Settings."
        }

        /** 401/403 — a key exists but the API rejected it. */
        data class Unauthorized(override val message: String) : Failure

        /** 429 or 529. Retryable. */
        data class RateLimited(val retryAfterSeconds: Long?, override val message: String) : Failure

        /** stop_reason == "refusal". Retrying the same prompt will not help. */
        data class Refused(val category: String?, override val message: String) : Failure

        /** stop_reason == "max_tokens" — a partial answer is worse than none. */
        data object Truncated : Failure {
            override val message = "The reply was cut off before it finished."
        }

        /** Valid HTTP, unusable body. Carries the raw text so it can be inspected. */
        data class Unparseable(val raw: String, override val message: String) : Failure

        data class Network(override val message: String) : Failure

        data class Http(val code: Int, override val message: String) : Failure
    }
}
