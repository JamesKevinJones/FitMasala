package com.kevinjones.fitmasala.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A meal actually eaten. Written when "Cook & Eat" is tapped on an AI recipe, or
 * when a meal is logged by hand.
 *
 * This table is intentionally DENORMALISED from [RecipeEntity]: the dish name,
 * region and macros are copied in rather than joined through `sourceRecipeId`.
 * If a recipe is later edited or deleted, what you ate three weeks ago must not
 * change. `sourceRecipeId` is a nullable back-reference for "show me the recipe",
 * with no foreign key precisely so deleting a recipe cannot cascade into history.
 */
@Entity(
    tableName = "logged_meals",
    indices = [
        Index("dayEpoch"),
        Index("eatenAt"),
        Index("sourceRecipeId"),
    ],
)
data class LoggedMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val name: String,
    /** Devanagari or regional-script name when the AI supplies one. */
    val nameLocal: String? = null,
    val region: Region = Region.OTHER,
    val mealType: MealType,
    val cookingMethod: CookingMethod = CookingMethod.OTHER,

    /** Epoch millis. */
    val eatenAt: Long,
    /**
     * Epoch DAY, derived from [eatenAt] in the user's zone at write time.
     *
     * Denormalised on purpose. The dashboard's hottest query is "today's totals",
     * and grouping by a stored, indexed integer beats doing timezone arithmetic
     * inside SQLite on every read. It also means a meal stays on the day it was
     * eaten if the phone later changes timezone.
     */
    val dayEpoch: Long,

    /** e.g. 2.0 katori, 3.0 roti. */
    val portionQuantity: Double = 1.0,
    val portionUnit: PortionUnit = PortionUnit.SERVING,
    /** Human phrasing the AI used, kept verbatim: "2 medium katori (~180g each)". */
    val portionNote: String? = null,

    /** Totals for the portion actually eaten, not per-serving. */
    @Embedded val macros: Macros,

    /**
     * True when the numbers came from the LLM rather than a label or a scale.
     * The UI must show this — a hazard-striped estimate and a weighed value
     * cannot look the same, or the log stops meaning anything.
     */
    val isAiEstimate: Boolean = true,
    /** The model's own stated confidence, when it gives one. 0.0–1.0. */
    val estimateConfidence: Double? = null,

    val sourceRecipeId: Long? = null,
    val source: MealSource = MealSource.MANUAL,

    /**
     * App-private path to the photo this was estimated from, if any.
     *
     * Kept because a photo estimate is the one kind of log entry you can audit
     * later: when the weight trend disagrees with the calorie log, the photos are
     * the evidence for which one is lying.
     */
    val photoPath: String? = null,

    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
