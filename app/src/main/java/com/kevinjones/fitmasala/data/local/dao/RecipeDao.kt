package com.kevinjones.fitmasala.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kevinjones.fitmasala.data.local.entity.RecipeEntity
import com.kevinjones.fitmasala.data.local.entity.RecipeIngredientEntity
import com.kevinjones.fitmasala.data.local.entity.Region
import com.kevinjones.fitmasala.data.local.relation.RecipeWithIngredients
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIngredients(ingredients: List<RecipeIngredientEntity>)

    /**
     * A recipe without its ingredients is not a recipe. @Transaction makes the
     * two inserts atomic — a crash between them would otherwise leave a titled
     * recipe with an empty ingredient list, which looks like data loss.
     */
    @Transaction
    suspend fun insertRecipeWithIngredients(
        recipe: RecipeEntity,
        ingredients: List<RecipeIngredientEntity>,
    ): Long {
        val recipeId = insertRecipe(recipe)
        insertIngredients(ingredients.map { it.copy(recipeId = recipeId) })
        return recipeId
    }

    @Update
    suspend fun update(recipe: RecipeEntity)

    @Delete
    suspend fun delete(recipe: RecipeEntity)

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun byIdWithIngredients(id: Long): RecipeWithIngredients?

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY createdAt DESC LIMIT :limit")
    fun observeAll(limit: Int = 100): Flow<List<RecipeWithIngredients>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE isFavourite = 1 ORDER BY createdAt DESC")
    fun observeFavourites(): Flow<List<RecipeWithIngredients>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE region = :region ORDER BY createdAt DESC")
    fun observeByRegion(region: Region): Flow<List<RecipeWithIngredients>>

    @Query("UPDATE recipes SET isFavourite = :favourite WHERE id = :id")
    suspend fun setFavourite(id: Long, favourite: Boolean)

    @Transaction
    @Query(
        """
        SELECT * FROM recipes
        WHERE title LIKE '%' || :query || '%' OR titleLocal LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
        LIMIT :limit
        """,
    )
    suspend fun search(query: String, limit: Int = 30): List<RecipeWithIngredients>
}
