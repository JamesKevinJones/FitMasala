package com.kevinjones.fitmasala.data.remote

import com.kevinjones.fitmasala.data.prefs.SettingsStore
import com.kevinjones.fitmasala.data.remote.api.AnthropicApi
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches the user's own API key to every request.
 *
 * `runBlocking` is deliberate and safe here: OkHttp interceptors are synchronous
 * by contract and always run on OkHttp's dispatcher thread, never the main
 * thread. Reading one value from DataStore is the cheapest possible way to keep
 * the key in exactly one place instead of threading it through every call site.
 *
 * The key is read fresh per request rather than cached, so pasting a new key in
 * Settings takes effect immediately without restarting anything.
 */
@Singleton
class AnthropicAuthInterceptor internal constructor(
    private val key: () -> String?,
) : Interceptor {

    @Inject
    constructor(settingsStore: SettingsStore) : this({ runBlocking { settingsStore.current().apiKey } })

    override fun intercept(chain: Interceptor.Chain): Response {
        val apiKey = key()?.takeIf { it.isNotBlank() }
            // Fail here rather than sending an unauthenticated request and
            // surfacing a 401 that reads like a bad key rather than no key.
            ?: throw MissingApiKeyException()

        val request = chain.request().newBuilder()
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", AnthropicApi.VERSION_HEADER)
            .addHeader("anthropic-beta", AnthropicApi.FALLBACK_BETA)
            .addHeader("content-type", "application/json")
            .build()

        return chain.proceed(request)
    }
}

/** Distinct from an auth failure: there is no key at all, so Settings is the fix. */
class MissingApiKeyException : IOException("No API key set. Add one in Settings.")
