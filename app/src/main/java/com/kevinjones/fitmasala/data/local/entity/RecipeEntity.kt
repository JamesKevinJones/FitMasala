package com.kevinjones.fitmasala.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A recipe the AI chef generated and the user chose to keep. */
@Entity(
    tableName = "recipes",
    indices = [Index("createdAt"), Index("region")],
)
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    val title: String,
    val titleLocal: String? = null,
    val region: Region = Region.OTHER,
    /** Why this is the authentic version — the AI's own note. */
    val provenanceNote: String? = null,

    val servings: Int = 1,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val cookingMethod: CookingMethod = CookingMethod.OTHER,

    /** Ordered steps, stored as a JSON array via Converters. */
    val instructions: List<String> = emptyList(),
    /** Technique notes that change the macros: oil absorbed, ghee in the tadka. */
    val techniqueNotes: List<String> = emptyList(),

    /** Per SERVING, not per recipe. Multiply at log time. */
    @Embedded val macrosPerServing: Macros,

    /**
     * The raw JSON the model returned, kept verbatim.
     *
     * Storage is free and prompts change. When the parser is improved in a later
     * phase, old recipes can be re-parsed from this instead of being re-asked —
     * which would cost tokens and return a different recipe.
     */
    val rawResponse: String? = null,
    val modelId: String? = null,

    val isFavourite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "recipe_ingredients",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipeId")],
)
data class RecipeIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val orderIndex: Int,

    val name: String,
    /** "besan", "methi" — the name actually used in an Indian kitchen. */
    val nameLocal: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    /** "finely chopped", "soaked overnight" — affects cooked weight. */
    val preparationNote: String? = null,

    /**
     * DRY weight where the ingredient absorbs water (dal, rice, rajma).
     * Storing dry rather than cooked is the only way the macros stay meaningful:
     * 100g dry toor dal and 100g cooked toor dal differ by roughly a factor of
     * three in calories, and the LLM will happily conflate them if not pinned.
     */
    val gramsDry: Double? = null,
    val gramsCooked: Double? = null,
    val isOptional: Boolean = false,
)
