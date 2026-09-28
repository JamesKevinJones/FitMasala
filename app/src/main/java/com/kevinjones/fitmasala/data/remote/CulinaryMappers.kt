package com.kevinjones.fitmasala.data.remote

import com.kevinjones.fitmasala.core.util.DateKeys
import com.kevinjones.fitmasala.data.local.entity.CookingMethod
import com.kevinjones.fitmasala.data.local.entity.LoggedMealEntity
import com.kevinjones.fitmasala.data.local.entity.Macros
import com.kevinjones.fitmasala.data.local.entity.MealSource
import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.data.local.entity.PortionUnit
import com.kevinjones.fitmasala.data.local.entity.RecipeEntity
import com.kevinjones.fitmasala.data.local.entity.RecipeIngredientEntity
import com.kevinjones.fitmasala.data.local.entity.Region
import com.kevinjones.fitmasala.data.remote.dto.MacrosDto
import com.kevinjones.fitmasala.data.remote.dto.PhotoEstimateDto
import com.kevinjones.fitmasala.data.remote.dto.PhotoItemDto
import com.kevinjones.fitmasala.data.remote.dto.RecipeDto
import com.kevinjones.fitmasala.data.remote.dto.confidenceToScore
import com.kevinjones.fitmasala.data.photo.PreparedImage
import java.util.Base64

/**
 * DTO to entity. The boundary where an untrusted string from a model becomes a
 * typed row.
 *
 * Enum coercion is total: the schema tells the model to send an enum NAME, but
 * a model is not a compiler. An unrecognised region becomes OTHER rather than
 * throwing, because losing a good recipe over a regional label the app has no
 * constant for would be absurd.
 */

fun MacrosDto.toMacros() = Macros(
    calories = calories,
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
    fiberG = fiberG,
)

fun RecipeDto.toEntity(rawJson: String?, modelId: String?) = RecipeEntity(
    title = title,
    titleLocal = titleLocal,
    region = regionOf(region),
    provenanceNote = provenanceNote,
    servings = servings.coerceAtLeast(1),
    prepMinutes = prepMinutes,
    cookMinutes = cookMinutes,
    cookingMethod = cookingMethodOf(cookingMethod),
    instructions = instructions,
    techniqueNotes = techniqueNotes,
    macrosPerServing = macrosPerServing.toMacros(),
    rawResponse = rawJson,
    modelId = modelId,
)

fun RecipeDto.toIngredientEntities(recipeId: Long = 0): List<RecipeIngredientEntity> =
    ingredients.mapIndexed { index, dto ->
        RecipeIngredientEntity(
            recipeId = recipeId,
            orderIndex = index,
            name = dto.name,
            nameLocal = dto.nameLocal,
            quantity = dto.quantity,
            unit = dto.unit,
            preparationNote = dto.preparationNote,
            gramsDry = dto.gramsDry,
            gramsCooked = dto.gramsCooked,
            isOptional = dto.isOptional,
        )
    }

/**
 * "Cook & Eat". Macros are multiplied by servings eaten and COPIED onto the meal
 * row rather than referenced — see docs/DECISIONS.md on why history must not
 * move when a recipe is edited.
 */
fun RecipeDto.toLoggedMeal(
    mealType: MealType,
    servingsEaten: Double,
    sourceRecipeId: Long?,
    eatenAt: Long = System.currentTimeMillis(),
    estimateModel: String? = null,
) = LoggedMealEntity(
    name = title,
    nameLocal = titleLocal,
    region = regionOf(region),
    mealType = mealType,
    cookingMethod = cookingMethodOf(cookingMethod),
    eatenAt = eatenAt,
    dayEpoch = DateKeys.dayEpochOf(eatenAt),
    portionQuantity = servingsEaten,
    portionUnit = PortionUnit.SERVING,
    portionNote = portionDescription,
    macros = macrosPerServing.toMacros() * servingsEaten,
    isAiEstimate = true,
    estimateModel = estimateModel,
    sourceRecipeId = sourceRecipeId,
    source = MealSource.AI_CHAT,
)

/**
 * One photo becomes one meal row per dish, not one combined row.
 *
 * Per-dish rows are what make an estimate auditable later: when the weight trend
 * disagrees with the calorie log, "the dal was probably underestimated" is a
 * usable conclusion and "lunch was wrong" is not.
 */
fun PhotoEstimateDto.toLoggedMeals(
    mealType: MealType,
    photoPath: String?,
    eatenAt: Long = System.currentTimeMillis(),
    estimateModel: String? = null,
): List<LoggedMealEntity> = items.map { item ->
    val (quantity, unit) = item.structuredPortion() ?: (1.0 to PortionUnit.SERVING)
    LoggedMealEntity(
        name = item.name,
        nameLocal = item.nameLocal,
        region = regionOf(item.region),
        mealType = mealType,
        eatenAt = eatenAt,
        dayEpoch = DateKeys.dayEpochOf(eatenAt),
        portionQuantity = quantity,
        portionUnit = unit,
        // The model's own wording stays visible beside any later correction.
        portionNote = item.portionEstimate,
        macros = item.macros.toMacros(),
        isAiEstimate = true,
        estimateConfidence = confidenceToScore(item.confidence),
        estimateModel = estimateModel,
        source = MealSource.PHOTO,
        photoPath = photoPath,
        notes = item.uncertaintyNote,
    )
}

/**
 * The dish's portion as a quantity the stepper can scale, or null when it can't
 * be trusted to scale: an unrecognised unit, or a quantity that isn't a positive
 * number (the schema can't bound numbers, so zero or negative can still arrive).
 * Callers fall back to one SERVING - the macros still describe what was eaten,
 * they just can't be stepped.
 */
internal fun PhotoItemDto.structuredPortion(): Pair<Double, PortionUnit>? {
    val unit = PortionUnit.entries.firstOrNull { it.name.equals(portionUnit.trim(), ignoreCase = true) }
    return if (unit != null && portionQuantity.isFinite() && portionQuantity > 0) portionQuantity to unit else null
}

/** Base64 for the image content block. NO_WRAP equivalent - a wrapped payload is rejected. */
fun PreparedImage.toBase64(): String = Base64.getEncoder().encodeToString(jpegBytes)

private fun regionOf(raw: String): Region =
    Region.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: Region.OTHER

private fun cookingMethodOf(raw: String): CookingMethod =
    CookingMethod.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
        ?: CookingMethod.OTHER
