package com.kevinjones.fitmasala.data.remote

import com.kevinjones.fitmasala.data.local.entity.Macros
import com.kevinjones.fitmasala.data.prefs.SettingsStore
import com.kevinjones.fitmasala.data.remote.api.AnthropicApi
import com.kevinjones.fitmasala.data.remote.dto.AnthropicErrorEnvelope
import com.kevinjones.fitmasala.data.remote.dto.AnthropicMessage
import com.kevinjones.fitmasala.data.remote.dto.AnthropicRequest
import com.kevinjones.fitmasala.data.remote.dto.AnthropicResponse
import com.kevinjones.fitmasala.data.remote.dto.MacrosDto
import com.kevinjones.fitmasala.data.remote.dto.OutputConfig
import com.kevinjones.fitmasala.data.remote.dto.OutputFormat
import com.kevinjones.fitmasala.data.remote.dto.PhotoEstimateDto
import com.kevinjones.fitmasala.data.remote.dto.RecipeDto
import com.kevinjones.fitmasala.data.remote.dto.ThinkingConfig
import com.kevinjones.fitmasala.data.remote.prompt.CulinaryPrompts
import com.kevinjones.fitmasala.data.remote.prompt.RecipeSchemas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CulinaryLlmClient @Inject constructor(
    private val api: AnthropicApi,
    private val settingsStore: SettingsStore,
    private val json: Json,
) {

    suspend fun generateRecipe(
        ingredients: String,
        mealType: String? = null,
        servings: Int = 1,
    ): LlmResult<RecipeDto> = call(
        system = CulinaryPrompts.RECIPE_SYSTEM,
        message = AnthropicMessage.userText(
            CulinaryPrompts.pantryRequest(ingredients, mealType, servings),
        ),
        schema = RecipeSchemas.RECIPE,
        deserialize = { json.decodeFromString<RecipeDto>(it) },
        validate = { dto -> validateMacros(dto.macrosPerServing, "recipe") },
    )

    suspend fun estimateFromPhoto(
        base64Jpeg: String,
        mealType: String? = null,
    ): LlmResult<PhotoEstimateDto> = call(
        system = CulinaryPrompts.VISION_SYSTEM,
        message = AnthropicMessage.userImageAndText(
            base64Jpeg = base64Jpeg,
            text = CulinaryPrompts.photoRequest(mealType),
        ),
        schema = RecipeSchemas.PHOTO_ESTIMATE,
        deserialize = { json.decodeFromString<PhotoEstimateDto>(it) },
        validate = { dto -> validatePhoto(dto) },
    )

    private fun validatePhoto(dto: PhotoEstimateDto): List<String> = buildList {
        addAll(validateMacros(dto.totalMacros, "photo total"))
        if (!dto.containsFood) add("No food was found in the photo.")
        // The model returns per-item macros AND a total. If they disagree, one of
        // them is wrong, and silently trusting the total would hide it.
        val summed = dto.items.sumOf { it.macros.calories }
        val stated = dto.totalMacros.calories
        if (dto.items.isNotEmpty() && stated > 0 &&
            kotlin.math.abs(summed - stated) / stated > 0.10
        ) {
            add(
                "Per-dish calories sum to ${summed.toInt()} " +
                    "but the stated total is ${stated.toInt()}.",
            )
        }
    }

    /**
     * The Atwater cross-check: protein and carbs at 4 kcal/g, fat at 9, should
     * roughly reconcile with the stated calorie figure.
     *
     * This catches the one failure the schema cannot — a response that is
     * perfectly well-formed JSON and internally nonsense. It is an advisory
     * rather than a rejection, because the honest response to "these numbers
     * don't add up" is to show the user, not to throw the recipe away.
     */
    private fun validateMacros(dto: MacrosDto, label: String): List<String> {
        val macros = Macros(dto.calories, dto.proteinG, dto.carbsG, dto.fatG, dto.fiberG)
        return buildList {
            if (dto.calories <= 0) {
                add("The $label came back with no calories.")
            } else if (!macros.isInternallyConsistent()) {
                add(
                    "The $label macros don't reconcile: " +
                        "${macros.derivedCalories.toInt()} kcal from the grams vs " +
                        "${dto.calories.toInt()} kcal stated.",
                )
            }
            if (dto.proteinG < 0 || dto.carbsG < 0 || dto.fatG < 0) {
                add("The $label contains a negative macro value.")
            }
        }
    }

    private suspend fun <T> call(
        system: String,
        message: AnthropicMessage,
        schema: JsonObject,
        deserialize: (String) -> T,
        validate: (T) -> List<String>,
    ): LlmResult<T> = withContext(Dispatchers.IO) {
        val settings = runCatching { settingsStore.current() }.getOrNull()
        val model = settings?.modelId?.takeIf { it.isNotBlank() } ?: AnthropicApi.DEFAULT_MODEL

        val request = AnthropicRequest(
            model = model,
            maxTokens = AnthropicApi.MAX_TOKENS,
            system = system,
            messages = listOf(message),
            // Adaptive is the only on-mode here; budget_tokens is a 400.
            thinking = ThinkingConfig(type = "adaptive"),
            outputConfig = OutputConfig(
                effort = "high",
                format = OutputFormat(schema = schema),
            ),
            // Rescues a policy decline inside the same call instead of stopping.
            fallbacks = "default",
        )

        val response = try {
            api.messages(request)
        } catch (e: MissingApiKeyException) {
            return@withContext LlmResult.Failure.MissingApiKey
        } catch (e: IOException) {
            return@withContext LlmResult.Failure.Network(
                e.message ?: "Couldn't reach the API.",
            )
        }

        interpret(response, deserialize, validate)
    }

    private fun <T> interpret(
        response: Response<AnthropicResponse>,
        deserialize: (String) -> T,
        validate: (T) -> List<String>,
    ): LlmResult<T> {
        if (!response.isSuccessful) return httpFailure(response)

        val body = response.body()
            ?: return LlmResult.Failure.Unparseable("", "The API returned an empty body.")

        // Checked before the content: a refused or truncated response still
        // carries text that would parse into something misleading.
        if (body.wasRefused) {
            return LlmResult.Failure.Refused(
                category = body.stopDetails?.category,
                message = body.stopDetails?.explanation ?: "The model declined this request.",
            )
        }
        if (body.wasTruncated) return LlmResult.Failure.Truncated

        val text = body.text
        if (text.isBlank()) {
            return LlmResult.Failure.Unparseable("", "The reply contained no text.")
        }

        val value = try {
            deserialize(text)
        } catch (e: Exception) {
            // Should be unreachable while output_config.format is set. If it
            // ever fires, the schema and the DTO have drifted apart - which is
            // worth surfacing loudly rather than swallowing.
            return LlmResult.Failure.Unparseable(
                raw = text,
                message = "The reply didn't match the expected format: ${e.message}",
            )
        }

        return LlmResult.Success(
            value = value,
            rawJson = text,
            model = body.model,
            inputTokens = body.usage?.inputTokens ?: 0,
            outputTokens = body.usage?.outputTokens ?: 0,
            advisories = validate(value),
        )
    }

    private fun httpFailure(response: Response<AnthropicResponse>): LlmResult.Failure {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()
        val apiMessage = runCatching {
            json.decodeFromString<AnthropicErrorEnvelope>(raw).error?.message
        }.getOrNull()

        return when (response.code()) {
            401, 403 -> LlmResult.Failure.Unauthorized(
                apiMessage ?: "The API rejected that key. Check it in Settings.",
            )
            429, 529 -> LlmResult.Failure.RateLimited(
                retryAfterSeconds = response.headers()["retry-after"]?.toLongOrNull(),
                message = apiMessage ?: "Rate limited. Try again shortly.",
            )
            else -> LlmResult.Failure.Http(
                code = response.code(),
                message = apiMessage ?: "The API returned ${response.code()}.",
            )
        }
    }
}
