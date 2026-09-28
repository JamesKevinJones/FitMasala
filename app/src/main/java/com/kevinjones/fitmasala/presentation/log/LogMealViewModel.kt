package com.kevinjones.fitmasala.presentation.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.domain.repository.MealRepository
import com.kevinjones.fitmasala.presentation.snap.ManualDishInput
import com.kevinjones.fitmasala.presentation.snap.loggedSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Moves [LogMealState] along and writes the dish; the rules live in the model. */
@HiltViewModel
class LogMealViewModel @Inject constructor(
    private val meals: MealRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LogMealState.startingAt(System.currentTimeMillis()))
    val state: StateFlow<LogMealState> = _state.asStateFlow()

    fun setMealType(mealType: MealType) {
        _state.value = _state.value.withMealType(mealType)
    }

    fun setEatenAt(millis: Long) {
        _state.value = _state.value.withEatenAt(millis, now = System.currentTimeMillis())
    }

    /**
     * Logs the typed dish as MANUAL - not an Estimate. The form only offers this
     * once [ManualDishInput.problems] is empty; checked again here regardless.
     */
    fun save(input: ManualDishInput) {
        val current = _state.value
        val macros = input.macros
        if (current.saving || current.logged != null || input.problems().isNotEmpty() || macros == null) return
        _state.value = current.copy(saving = true, saveError = null)
        viewModelScope.launch {
            try {
                meals.logManual(
                    name = input.name.trim(),
                    mealType = current.mealType,
                    portionQuantity = input.quantity,
                    portionUnit = input.unit,
                    calories = macros.calories,
                    proteinG = macros.proteinG,
                    carbsG = macros.carbsG,
                    fatG = macros.fatG,
                    eatenAt = current.eatenAt,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(saving = false, saveError = SAVE_FAILED)
                return@launch
            }
            _state.value = _state.value.copy(
                saving = false,
                logged = loggedSummary(current.mealType, 1, macros.calories),
            )
        }
    }

    private companion object {
        const val SAVE_FAILED = "This meal couldn't be saved. Try again."
    }
}
