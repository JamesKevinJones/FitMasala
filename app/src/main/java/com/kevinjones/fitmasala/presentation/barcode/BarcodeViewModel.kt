package com.kevinjones.fitmasala.presentation.barcode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevinjones.fitmasala.data.local.entity.MealSource
import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.data.local.entity.PortionUnit
import com.kevinjones.fitmasala.data.remote.food.FoodLookupClient
import com.kevinjones.fitmasala.data.remote.food.isPlausibleBarcode
import com.kevinjones.fitmasala.domain.repository.MealRepository
import com.kevinjones.fitmasala.presentation.snap.loggedSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Moves [BarcodeState] along: lookup, portion, log. The rules live in the models. */
@HiltViewModel
class BarcodeViewModel @Inject constructor(
    private val foods: FoodLookupClient,
    private val meals: MealRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<BarcodeState>(BarcodeState.Ready())
    val state: StateFlow<BarcodeState> = _state.asStateFlow()

    fun setTyped(text: String) {
        val ready = _state.value as? BarcodeState.Ready ?: return
        _state.value = ready.copy(typed = text.filter(Char::isDigit).take(14), error = null)
    }

    /** From the scanner, or the typed digits. */
    fun lookup(barcode: String) {
        val code = barcode.trim()
        if (!isPlausibleBarcode(code)) {
            _state.value = BarcodeState.Ready(typed = code, error = "A barcode is 8 to 14 digits.")
            return
        }
        _state.value = BarcodeState.LookingUp(code)
        viewModelScope.launch {
            val result = foods.lookup(code)
            _state.value = BarcodeState.after(code, result, now = System.currentTimeMillis())
        }
    }

    /** The scanner couldn't start (Play services missing or busy): typing still works. */
    fun scannerUnavailable() {
        val current = _state.value as? BarcodeState.Ready ?: BarcodeState.Ready()
        _state.value = current.copy(error = "The scanner isn't available on this phone. Type the digits under the barcode instead.")
    }

    fun startOver() {
        _state.value = BarcodeState.Ready()
    }

    fun stepUp() = editFound { it.withPortion(PackagedPortion::steppedUp) }
    fun stepDown() = editFound { it.withPortion(PackagedPortion::steppedDown) }
    fun setUnit(unit: PortionUnit) = editFound { it.withPortion { p -> p.withUnit(unit) } }
    fun setMealType(mealType: MealType) = editFound { it.withMealType(mealType) }

    /** Logs the label values as BARCODE - not an Estimate - eaten now. */
    fun log() {
        val found = _state.value as? BarcodeState.Found ?: return
        if (found.saving) return
        _state.value = found.copy(saving = true, saveError = null)
        val portion = found.portion
        val macros = portion.macros
        viewModelScope.launch {
            try {
                meals.logManual(
                    name = portion.dishName,
                    mealType = found.mealType,
                    portionQuantity = portion.quantity,
                    portionUnit = portion.unit,
                    calories = macros.calories,
                    proteinG = macros.proteinG,
                    carbsG = macros.carbsG,
                    fatG = macros.fatG,
                    fiberG = macros.fiberG,
                    eatenAt = found.eatenAt,
                    source = MealSource.BARCODE,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val current = _state.value as? BarcodeState.Found ?: return@launch
                _state.value = current.copy(saving = false, saveError = SAVE_FAILED)
                return@launch
            }
            _state.value = BarcodeState.Logged(loggedSummary(found.mealType, 1, macros.calories))
        }
    }

    private fun editFound(change: (BarcodeState.Found) -> BarcodeState.Found) {
        val found = _state.value as? BarcodeState.Found ?: return
        _state.value = change(found)
    }

    private companion object {
        const val SAVE_FAILED = "This meal couldn't be saved. Try again."
    }
}
