package com.kevinjones.fitmasala.data.remote

import com.kevinjones.fitmasala.data.local.entity.Macros
import com.kevinjones.fitmasala.data.remote.dto.MacrosDto
import com.kevinjones.fitmasala.data.remote.dto.PhotoEstimateDto
import com.kevinjones.fitmasala.data.remote.dto.RecipeDto
import com.kevinjones.fitmasala.data.remote.prompt.CulinaryPrompts
import com.kevinjones.fitmasala.data.remote.prompt.RecipeSchemas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The culinary half of every LLM call: prompts, schemas, parsing and advisories.
 * Which provider answers is [backend]'s business - in the app, [ProviderBackend],
 * the one chosen in Settings - so a recipe from Gemini goes through exactly the
 * same parsing and Atwater check as one from Claude.
 */
@Singleton
class CulinaryLlmClient internal constructor(
    private val backend: LlmBackend,
    private val json: Json,
) {

    @Inject
    constructor(backend: ProviderBackend, json: Json) : this(backend as LlmBackend, json)

    suspend fun generateRecipe(
        ingredients: String,
        mealType: String? = null,
        servings: Int = 1,
    ): LlmResult<RecipeDto> = call(
        request = LlmRequest(
            system = CulinaryPrompts.RECIPE_SYSTEM,
            text = CulinaryPrompts.pantryRequest(ingredients, mealType, servings),
            schema = RecipeSchemas.RECIPE,
        ),
        deserialize = { json.decodeFromString<RecipeDto>(it) },
        validate = { dto -> validateMacros(dto.macrosPerServing, "recipe") },
    )

    suspend fun estimateFromPhoto(
        base64Jpeg: String,
        mealType: String? = null,
    ): LlmResult<PhotoEstimateDto> = estimate(
        request = EstimateRequests.forPhoto(base64Jpeg, mealType),
        noFood = "No food was found in the photo.",
    )

    /**
     * A dish typed onto a photo's review sheet ("1 tsp ghee"). Same schema, DTO
     * and validation as [estimateFromPhoto], so a typed dish joins the sheet
     * exactly like a photographed one.
     */
    suspend fun estimateFromText(
        description: String,
        mealType: String? = null,
    ): LlmResult<PhotoEstimateDto> = estimate(
        request = EstimateRequests.forText(description, mealType),
        noFood = "\"${description.trim()}\" doesn't read as food.",
    )

    private suspend fun estimate(request: LlmRequest, noFood: String): LlmResult<PhotoEstimateDto> =
        call(
            request = request,
            deserialize = { json.decodeFromString<PhotoEstimateDto>(it) },
            validate = { dto -> validatePhoto(dto, noFood) },
        )

    private fun validatePhoto(dto: PhotoEstimateDto, noFood: String): List<String> = buildList {
        addAll(validateMacros(dto.totalMacros, "estimate total"))
        if (!dto.containsFood) add(noFood)
        // The schema can't bound numbers, so a zero or negative quantity (or an
        // unknown unit) can still arrive. Say so rather than quietly logging it
        // as "1 serving" the user can't step.
        dto.items.filter { it.structuredPortion() == null }.forEach { item ->
            add(
                "The portion for ${item.name} came back as " +
                    "'${item.portionQuantity} ${item.portionUnit}', so it will be logged as 1 serving.",
            )
        }
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

    /**
     * Asks through the wired-in [LlmBackend], then does the
     * provider-neutral half: parse the JSON into [T] and attach advisories.
     */
    private suspend fun <T> call(
        request: LlmRequest,
        deserialize: (String) -> T,
        validate: (T) -> List<String>,
    ): LlmResult<T> = withContext(Dispatchers.IO) {
        when (val reply = backend.complete(request)) {
            is LlmResult.Failure -> reply
            is LlmResult.Success -> {
                val value = try {
                    deserialize(reply.value)
                } catch (e: Exception) {
                    // Should be unreachable while the schema is enforced. If it
                    // ever fires, the schema and the DTO have drifted apart -
                    // which is worth surfacing loudly rather than swallowing.
                    return@withContext LlmResult.Failure.Unparseable(
                        raw = reply.value,
                        message = "The reply didn't match the expected format: ${e.message}",
                    )
                }
                LlmResult.Success(
                    value = value,
                    rawJson = reply.rawJson,
                    model = reply.model,
                    inputTokens = reply.inputTokens,
                    outputTokens = reply.outputTokens,
                    advisories = validate(value),
                )
            }
        }
    }
}

/**
 * How each kind of dish estimate is asked for, in one place so the photo and the
 * typed versions cannot drift apart: different instructions and input, the same
 * structured-output schema - and so the same DTO, validation and mapper.
 */
internal object EstimateRequests {

    fun forPhoto(base64Jpeg: String, mealType: String?) = LlmRequest(
        system = CulinaryPrompts.VISION_SYSTEM,
        text = CulinaryPrompts.photoRequest(mealType),
        imageJpegBase64 = base64Jpeg,
        schema = RecipeSchemas.PHOTO_ESTIMATE,
    )

    fun forText(description: String, mealType: String?) = LlmRequest(
        system = CulinaryPrompts.TEXT_ESTIMATE_SYSTEM,
        text = CulinaryPrompts.textRequest(description, mealType),
        schema = RecipeSchemas.PHOTO_ESTIMATE,
    )
}
