package com.kevinjones.fitmasala.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Parsed shape of the model's structured output. Mirrors `RecipeSchemas`
 * one-for-one — if you change a schema, change the matching class here.
 *
 * Nullable fields are nullable because the SCHEMA declares them nullable, not
 * because they might be missing: `additionalProperties: false` plus a full
 * `required` list means every key is present, and a value the model could not
 * determine arrives as an explicit null.
 */
@Serializable
data class MacrosDto(
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
)

@Serializable
data class RecipeDto(
    val title: String,
    val titleLocal: String? = null,
    val region: String,
    val provenanceNote: String,
    val cookingMethod: String,
    val servings: Int,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val ingredients: List<IngredientDto>,
    val instructions: List<String>,
    val techniqueNotes: List<String>,
    val macrosPerServing: MacrosDto,
    val portionDescription: String,
)

@Serializable
data class IngredientDto(
    val name: String,
    val nameLocal: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val preparationNote: String? = null,
    val gramsDry: Double? = null,
    val gramsCooked: Double? = null,
    val isOptional: Boolean = false,
)

@Serializable
data class PhotoEstimateDto(
    val containsFood: Boolean,
    val items: List<PhotoItemDto> = emptyList(),
    val totalMacros: MacrosDto,
    val overallConfidence: String,
)

@Serializable
data class PhotoItemDto(
    val name: String,
    val nameLocal: String? = null,
    val region: String,
    val portionEstimate: String,
    val portionBasis: String,
    val macros: MacrosDto,
    val confidence: String,
    val uncertaintyNote: String,
)

/** Maps the model's confidence word onto the 0..1 the plan engine stores. */
fun confidenceToScore(raw: String): Double = when (raw.trim().lowercase()) {
    "high" -> 0.85
    "medium" -> 0.6
    "low" -> 0.35
    else -> 0.35 // unrecognised means unknown, and unknown is not confident
}
