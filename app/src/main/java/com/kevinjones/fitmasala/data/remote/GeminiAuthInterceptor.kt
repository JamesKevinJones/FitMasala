package com.kevinjones.fitmasala.data.remote

import com.kevinjones.fitmasala.data.prefs.SettingsStore
import com.kevinjones.fitmasala.data.remote.api.GeminiApi
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches the user's Gemini key to every Gemini request, and only to those: it
 * sits on the Gemini API's own OkHttp client, so the Anthropic key can never
 * reach Google, nor this key Anthropic. Read fresh per request, like
 * [AnthropicAuthInterceptor]; `runBlocking` is safe on OkHttp's thread.
 */
@Singleton
class GeminiAuthInterceptor internal constructor(
    private val key: () -> String?,
) : Interceptor {

    @Inject
    constructor(settingsStore: SettingsStore) : this({ runBlocking { settingsStore.current().geminiApiKey } })

    override fun intercept(chain: Interceptor.Chain): Response {
        val apiKey = key()?.takeIf { it.isNotBlank() }
            // No key is a Settings problem, not a rejected key.
            ?: throw MissingApiKeyException()

        val request = chain.request().newBuilder()
            .addHeader(GeminiApi.KEY_HEADER, apiKey)
            .addHeader("content-type", "application/json")
            .build()
        return chain.proceed(request)
    }
}
