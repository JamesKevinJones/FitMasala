package com.kevinjones.fitmasala.presentation.chef

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevinjones.fitmasala.data.local.dao.RecipeDao
import com.kevinjones.fitmasala.data.local.entity.CookingMethod
import com.kevinjones.fitmasala.data.local.entity.Macros
import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.data.local.entity.RecipeEntity
import com.kevinjones.fitmasala.data.local.entity.RecipeIngredientEntity
import com.kevinjones.fitmasala.data.local.entity.Region
import com.kevinjones.fitmasala.data.local.relation.FrequentMeal
import com.kevinjones.fitmasala.data.remote.CulinaryLlmClient
import com.kevinjones.fitmasala.data.remote.LlmResult
import com.kevinjones.fitmasala.data.remote.dto.RecipeDto
import com.kevinjones.fitmasala.domain.repository.MealRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

sealed class ChefMessage {
    data class Text(val text: String, val isUser: Boolean) : ChefMessage()
    data class Recipe(val recipe: RecipeDto, val rawJson: String) : ChefMessage()
    data class Error(val message: String) : ChefMessage()
}

data class ChefUiState(
    val loading: Boolean = true,
    val frequent: List<FrequentMeal> = emptyList(),
    /** Meal name to pending portion count, before the user commits. */
    val pending: Map<String, Int> = emptyMap(),
    val justLogged: Int = 0,
    val chatMessages: List<ChefMessage> = emptyList(),
    val isAiLoading: Boolean = false
) {
    val pendingCount: Int get() = pending.values.sum()
    val hasPending: Boolean get() = pendingCount > 0
    val isEmpty: Boolean get() = !loading && frequent.isEmpty()
}

@HiltViewModel
class ChefViewModel @Inject constructor(
    private val meals: MealRepository,
    private val recipes: RecipeDao,
    private val culinaryClient: CulinaryLlmClient
) : ViewModel() {

    private val pending = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val justLogged = MutableStateFlow(0)
    private val chatMessages = MutableStateFlow<List<ChefMessage>>(emptyList())
    private val isAiLoading = MutableStateFlow(false)

    val state: StateFlow<ChefUiState> = combine(
        meals.observeFrequentMeals(),
        pending,
        justLogged,
        chatMessages,
        isAiLoading
    ) { frequent, pendingMap, logged, messages, aiLoading ->
        ChefUiState(
            loading = false,
            frequent = frequent,
            pending = pendingMap,
            justLogged = logged,
            chatMessages = messages,
            isAiLoading = aiLoading
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChefUiState())

    fun askChef(prompt: String) {
        if (prompt.isBlank()) return
        
        viewModelScope.launch {
            chatMessages.update { it + ChefMessage.Text(prompt, true) }
            isAiLoading.value = true
            
            val result = culinaryClient.generateRecipe(prompt)
            
            when (result) {
                is LlmResult.Success -> {
                    chatMessages.update { it + ChefMessage.Recipe(result.value, result.rawJson) }
                }
                is LlmResult.Failure -> {
                    chatMessages.update { it + ChefMessage.Error(result.message) }
                }
                else -> {}
            }
            isAiLoading.value = false
        }
    }

    fun cookAndEat(recipe: RecipeDto, rawJson: String) = viewModelScope.launch {
        // 1. Save to recipes table
        val macros = Macros(
            calories = recipe.macrosPerServing.calories,
            proteinG = recipe.macrosPerServing.proteinG,
            carbsG = recipe.macrosPerServing.carbsG,
            fatG = recipe.macrosPerServing.fatG,
            fiberG = recipe.macrosPerServing.fiberG
        )

        val entity = RecipeEntity(
            title = recipe.title,
            titleLocal = recipe.titleLocal,
            region = enumOrOther(recipe.region),
            provenanceNote = recipe.provenanceNote,
            servings = recipe.servings,
            prepMinutes = recipe.prepMinutes,
            cookMinutes = recipe.cookMinutes,
            cookingMethod = enumOrOtherMethod(recipe.cookingMethod),
            instructions = recipe.instructions,
            techniqueNotes = recipe.techniqueNotes,
            macrosPerServing = macros,
            rawResponse = rawJson
        )

        val ingredients = recipe.ingredients.mapIndexed { index, i ->
            RecipeIngredientEntity(
                recipeId = 0, // Set by DAO
                orderIndex = index,
                name = i.name,
                nameLocal = i.nameLocal,
                quantity = i.quantity,
                unit = i.unit,
                preparationNote = i.preparationNote,
                gramsDry = i.gramsDry,
                gramsCooked = i.gramsCooked
            )
        }

        val recipeId = recipes.insertRecipeWithIngredients(entity, ingredients)
        
        // 2. Log as meal
        meals.logRecipe(
            name = recipe.title,
            region = recipe.region,
            macros = macros,
            portions = 1.0,
            mealType = mealTypeForNow(),
            sourceRecipeId = recipeId
        )
        
        // 3. Update UI
        chatMessages.update { it + ChefMessage.Text("Logged ${recipe.title} to your diary. Enjoy!", false) }
    }

    fun setPortions(name: String, count: Int) {
        pending.update { current ->
            if (count <= 0) current - name else current + (name to count)
        }
    }

    fun logPending() = viewModelScope.launch {
        val snapshot = pending.value
        val byName = state.value.frequent.associateBy { it.name }
        val mealType = mealTypeForNow()

        snapshot.forEach { (name, portions) ->
            byName[name]?.let { meal ->
                meals.logAgain(meal, portions.toDouble(), mealType)
            }
        }
        justLogged.value = snapshot.values.sum()
        pending.value = emptyMap()
    }

    fun clearJustLogged() { justLogged.value = 0 }

    private fun mealTypeForNow(): MealType = when (LocalTime.now().hour) {
        in 4..10 -> MealType.BREAKFAST
        in 11..15 -> MealType.LUNCH
        in 16..18 -> MealType.SNACK
        in 19..23 -> MealType.DINNER
        else -> MealType.SNACK
    }

    private fun enumOrOther(raw: String): Region =
        Region.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: Region.OTHER

    private fun enumOrOtherMethod(raw: String): CookingMethod =
        CookingMethod.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CookingMethod.OTHER
}
