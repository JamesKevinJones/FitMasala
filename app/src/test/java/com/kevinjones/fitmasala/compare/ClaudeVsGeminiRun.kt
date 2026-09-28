package com.kevinjones.fitmasala.compare

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.kevinjones.fitmasala.data.remote.AnthropicAuthInterceptor
import com.kevinjones.fitmasala.data.remote.AnthropicBackend
import com.kevinjones.fitmasala.data.remote.CulinaryLlmClient
import com.kevinjones.fitmasala.data.remote.GeminiAuthInterceptor
import com.kevinjones.fitmasala.data.remote.GeminiBackend
import com.kevinjones.fitmasala.data.remote.LlmBackend
import com.kevinjones.fitmasala.data.remote.LlmResult
import com.kevinjones.fitmasala.data.remote.api.AnthropicApi
import com.kevinjones.fitmasala.data.remote.api.GeminiApi
import com.kevinjones.fitmasala.data.remote.apiJson
import com.kevinjones.fitmasala.data.remote.dto.PhotoEstimateDto
import com.kevinjones.fitmasala.data.remote.toBase64
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Retrofit
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The Claude vs Gemini comparison on Kevin's own meals (#23). Skipped unless
 * asked for, so CI and ordinary test runs never call an API:
 *
 * ```
 * $env:FITMASALA_COMPARE = "1"
 * $env:ANTHROPIC_API_KEY = "sk-ant-..."
 * $env:GEMINI_API_KEY = "AIza..."
 * $env:FITMASALA_COMPARE_PHOTOS = "C:\meals\photos"
 * $env:FITMASALA_COMPARE_TRUTH = "C:\meals\known.csv"
 * $env:FITMASALA_GEMINI_PRICE = "0.5,3"         # $ per MTok in,out - optional
 * .\gradlew.bat :app:testDebugUnitTest --tests "*ClaudeVsGeminiRun*" --rerun
 * ```
 *
 * Optional: `FITMASALA_CLAUDE_PRICE` (default "5,25", claude-opus-5),
 * `FITMASALA_CLAUDE_MODEL` / `FITMASALA_GEMINI_MODEL` to override the app's
 * defaults, and `FITMASALA_COMPARE_OUT` for the report path (default
 * `app/build/compare/report.md`). The known-values format is on [KnownTable].
 *
 * One engine: every photo goes through [JvmPhotoPreprocessor] (the app's sizing),
 * then the app's own [CulinaryLlmClient] - prompts, schema, parsing and
 * advisories - over the app's own backends. Only the key source differs: an
 * environment variable instead of Settings. Keys are never written anywhere;
 * the run fails rather than save a report that contains one.
 */
class ClaudeVsGeminiRun {

    private val env = System.getenv()

    @Test
    fun compareOnKnownMeals() = runBlocking {
        assumeTrue("set FITMASALA_COMPARE=1 to run the comparison", env["FITMASALA_COMPARE"] == "1")
        val claudeKey = env["ANTHROPIC_API_KEY"].orEmpty()
        val geminiKey = env["GEMINI_API_KEY"].orEmpty()
        assumeTrue("both ANTHROPIC_API_KEY and GEMINI_API_KEY are needed", claudeKey.isNotBlank() && geminiKey.isNotBlank())
        System.setProperty("java.awt.headless", "true")

        val photos = File(required("FITMASALA_COMPARE_PHOTOS"))
        require(photos.isDirectory) { "FITMASALA_COMPARE_PHOTOS is not a folder: $photos" }
        val known = KnownTable.parse(File(required("FITMASALA_COMPARE_TRUTH")).readText())
        known.forEach { require(File(photos, it.photo).isFile) { "no photo named ${it.photo} in $photos" } }

        val providers = listOf(
            Provider("Claude", claude(claudeKey), Price.parse(env["FITMASALA_CLAUDE_PRICE"]) ?: CLAUDE_OPUS_5),
            Provider("Gemini", gemini(geminiKey), Price.parse(env["FITMASALA_GEMINI_PRICE"])),
        )

        val rows = known.flatMap { meal ->
            val image = JvmPhotoPreprocessor.prepare(File(photos, meal.photo)).toBase64()
            providers.map { provider ->
                val result = withOneRetry { provider.client.estimateFromPhoto(image, meal.mealType) }
                // A rejected key fails every photo the same way; stop at the first.
                check(result !is LlmResult.Failure.Unauthorized) { "${provider.name} rejected its key: ${result}" }
                Row.of(meal, provider.name, result, provider.price).also {
                    println("${meal.photo} / ${provider.name}: ${it.calorieErrorPct?.let { e -> "%+.0f%%".format(e) } ?: it.failure}")
                }
            }
        }

        val summaries = providers.map { p -> Summary.of(p.name, rows.filter { it.provider == p.name }) }
        val report = ReportWriter.markdown(rows, summaries, geminiMeetsTheRule(summaries[0], summaries[1]))
        check(!report.contains(claudeKey) && !report.contains(geminiKey)) { "refusing to write a report containing a key" }

        val out = File(env["FITMASALA_COMPARE_OUT"] ?: "build/compare/report.md")
        out.parentFile?.mkdirs()
        out.writeText(report)
        println("Report written to ${out.absolutePath}")
    }

    private class Provider(val name: String, val client: CulinaryLlmClient, val price: Price?)

    private fun required(name: String) = env[name]?.takeIf { it.isNotBlank() } ?: error("$name is not set")

    /** A rate limit waits as long as the API asked (or 30s) and tries once more. */
    private suspend fun withOneRetry(call: suspend () -> LlmResult<PhotoEstimateDto>): LlmResult<PhotoEstimateDto> {
        val first = call()
        if (first !is LlmResult.Failure.RateLimited) return first
        delay(TimeUnit.SECONDS.toMillis(first.retryAfterSeconds ?: 30))
        return call()
    }

    private val json = apiJson()

    private fun claude(key: String): CulinaryLlmClient {
        val api = retrofit(AnthropicApi.BASE_URL, AnthropicAuthInterceptor { key }).create(AnthropicApi::class.java)
        val model = env["FITMASALA_CLAUDE_MODEL"]
        return CulinaryLlmClient(AnthropicBackend(api, json) { model } as LlmBackend, json)
    }

    private fun gemini(key: String): CulinaryLlmClient {
        val api = retrofit(GeminiApi.BASE_URL, GeminiAuthInterceptor { key }).create(GeminiApi::class.java)
        val model = env["FITMASALA_GEMINI_MODEL"]
        return CulinaryLlmClient(GeminiBackend(api, json) { model } as LlmBackend, json)
    }

    /** One client per provider, as in the app, and no logging interceptor at all. */
    private fun retrofit(baseUrl: String, auth: Interceptor): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            OkHttpClient.Builder()
                .addInterceptor(auth)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build(),
        )
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private companion object {
        /** claude-opus-5 list price, $ per MTok (Anthropic, 2026). */
        val CLAUDE_OPUS_5 = Price(5.0, 25.0)
    }
}
