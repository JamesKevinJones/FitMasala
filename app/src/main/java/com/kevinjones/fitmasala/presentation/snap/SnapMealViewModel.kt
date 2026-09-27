package com.kevinjones.fitmasala.presentation.snap

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevinjones.fitmasala.core.util.exifTakenAt
import com.kevinjones.fitmasala.core.util.mealTypeAt
import com.kevinjones.fitmasala.core.util.resolveEatenAt
import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.data.photo.ImagePreprocessor
import com.kevinjones.fitmasala.data.photo.MealPhotoStore
import com.kevinjones.fitmasala.data.remote.CulinaryLlmClient
import com.kevinjones.fitmasala.data.remote.LlmResult
import com.kevinjones.fitmasala.data.remote.toBase64
import com.kevinjones.fitmasala.domain.repository.MealRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import javax.inject.Inject

/**
 * Drives "Snap a meal": camera app -> on-device downscale -> vision estimate ->
 * review -> log. The rules live in [SnapMealState]; this class only moves between
 * states and does the IO.
 *
 * The photo path is kept in [SavedStateHandle] because the camera app is a
 * separate process: Android may kill this one while the user is framing the
 * shot, and the result must still find its file when it comes back.
 */
@HiltViewModel
class SnapMealViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val photos: MealPhotoStore,
    private val preprocessor: ImagePreprocessor,
    private val culinaryClient: CulinaryLlmClient,
    private val meals: MealRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SnapMealState>(SnapMealState.Capturing)
    val state: StateFlow<SnapMealState> = _state.asStateFlow()

    private var pendingPath: String?
        get() = savedState[KEY_PENDING_PATH]
        set(value) { savedState[KEY_PENDING_PATH] = value }

    /** A fresh file for the camera app to write into, as the URI it is allowed to use. */
    fun prepareCapture(): Uri {
        photos.discard(pendingPath)
        val file = photos.newPhotoFile()
        pendingPath = file.absolutePath
        return photos.uriFor(file)
    }

    fun onPermissionDenied() {
        _state.value = SnapMealState.PermissionDenied
    }

    /** Back to "Take photo / Choose from gallery" - from the permission explanation, say. */
    fun backToChoice() {
        _state.value = SnapMealState.Capturing
    }

    /**
     * From the camera app. `false` means the user backed out without a photo:
     * back to the choice, not out of the flow - they may want the gallery instead.
     */
    fun onCaptureResult(taken: Boolean) {
        val path = pendingPath ?: return
        if (!taken) {
            photos.discard(path)
            pendingPath = null
            _state.value = SnapMealState.Capturing
            return
        }
        // Taken this moment, so eaten this moment.
        val eatenAt = System.currentTimeMillis()
        estimate(path, eatenAt, mealTypeAt(eatenAt))
    }

    /**
     * From the photo picker; null when nothing was picked. The photo is copied in
     * first - the meal keeps its audit photo even if the original is deleted -
     * and its EXIF time read before downscaling strips it. A lunch photo logged
     * at 11pm is logged as lunch, on the day it was eaten.
     */
    fun onGalleryPicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val path = try {
                withContext(Dispatchers.IO) { photos.importFrom(uri) }.absolutePath
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val now = System.currentTimeMillis()
                _state.value = photoUnreadable(null, now, mealTypeAt(now))
                return@launch
            }
            photos.discard(pendingPath)
            pendingPath = path

            val (dateTime, offset) = withContext(Dispatchers.IO) {
                runCatching { photos.exifDateTime(path) }.getOrDefault(null to null)
            }
            val eatenAt = resolveEatenAt(
                photoTakenAt = exifTakenAt(dateTime, offset, ZoneId.systemDefault()),
                now = System.currentTimeMillis(),
            )
            estimate(path, eatenAt, mealTypeAt(eatenAt))
        }
    }

    private fun estimate(path: String, eatenAt: Long, mealType: MealType) {
        _state.value = SnapMealState.Estimating(path)
        viewModelScope.launch {
            val prepared = try {
                preprocessor.prepare(File(path)).also { image ->
                    // Keep only what was sent: the audit trail at a tenth of the size.
                    withContext(Dispatchers.IO) { photos.replaceWith(path, image.jpegBytes) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = photoUnreadable(path, eatenAt, mealType)
                return@launch
            }

            _state.value = when (
                val result = culinaryClient.estimateFromPhoto(
                    base64Jpeg = prepared.toBase64(),
                    mealType = mealType.name.lowercase(),
                )
            ) {
                is LlmResult.Success -> SnapMealState.Review(
                    photoPath = path,
                    eatenAt = eatenAt,
                    mealType = mealType,
                    estimate = result.value,
                    advisories = result.advisories,
                )
                is LlmResult.Failure -> failureFor(result, path, eatenAt, mealType)
            }
            if (_state.value is SnapMealState.Review) checkAlreadyLogged(eatenAt)
        }
    }

    /**
     * The same photo again, at the same time. The file on disk may already be the
     * downscaled copy; preparing it again keeps it at that size.
     */
    fun retry() {
        val failed = _state.value as? SnapMealState.Failed ?: return
        val path = failed.photoPath ?: return retake()
        estimate(path, failed.eatenAt, failed.mealType)
    }

    /** This photo is no use: drop it and go back to "Take photo / Choose from gallery". */
    fun retake() {
        photos.discard(pendingPath)
        pendingPath = null
        _state.value = SnapMealState.Capturing
    }

    /** From a failure, or from an estimate that found no dish: type the dish instead. */
    fun logByHand() {
        _state.value = when (val current = _state.value) {
            is SnapMealState.Failed ->
                SnapMealState.ManualEntry(current.photoPath, current.eatenAt, current.mealType)
            is SnapMealState.Review ->
                SnapMealState.ManualEntry(current.photoPath, current.eatenAt, current.mealType)
            else -> return
        }
    }

    fun setMealType(mealType: MealType) {
        when (val current = _state.value) {
            is SnapMealState.Review -> _state.value = current.withMealType(mealType)
            is SnapMealState.ManualEntry -> _state.value = current.copy(mealType = mealType)
            else -> Unit
        }
    }

    /**
     * Logs the typed dish as MANUAL - not an Estimate - at the photo's time, with
     * the photo kept for the audit trail. The screen only offers this once
     * [ManualDishInput.problems] is empty; checked again here regardless.
     */
    fun saveManual(input: ManualDishInput) {
        val entry = _state.value as? SnapMealState.ManualEntry ?: return
        val macros = input.macros
        if (entry.saving || input.problems().isNotEmpty() || macros == null) return
        _state.value = entry.copy(saving = true, saveError = null)
        viewModelScope.launch {
            try {
                meals.logManual(
                    name = input.name.trim(),
                    mealType = entry.mealType,
                    portionQuantity = input.quantity,
                    portionUnit = input.unit,
                    calories = macros.calories,
                    proteinG = macros.proteinG,
                    carbsG = macros.carbsG,
                    fatG = macros.fatG,
                    eatenAt = entry.eatenAt,
                    photoPath = entry.photoPath,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val current = _state.value as? SnapMealState.ManualEntry ?: return@launch
                _state.value = current.copy(saving = false, saveError = SAVE_FAILED)
                return@launch
            }
            pendingPath = null
            _state.value = SnapMealState.Logged(loggedSummary(entry.mealType, 1, macros.calories))
        }
    }

    /** The user moved the meal in time on the sheet. */
    fun setEatenAt(millis: Long) {
        val review = _state.value as? SnapMealState.Review ?: return
        val moved = review.withEatenAt(millis, now = System.currentTimeMillis())
        _state.value = moved
        checkAlreadyLogged(moved.eatenAt)
    }

    /**
     * Warns, never blocks: the flag is set only if the sheet still shows the same
     * time when the answer comes back.
     */
    private fun checkAlreadyLogged(eatenAt: Long) {
        viewModelScope.launch {
            val duplicate = meals.hasPhotoMealAt(eatenAt)
            val current = _state.value as? SnapMealState.Review ?: return@launch
            if (current.eatenAt == eatenAt) _state.value = current.copy(alreadyLogged = duplicate)
        }
    }

    fun stepDish(index: Int, up: Boolean) =
        editDish(index) { if (up) it.steppedUp() else it.steppedDown() }

    fun renameDish(index: Int, name: String) = editDish(index) { it.renamed(name) }

    /** Removal is a toggle so a mistaken tap is one tap to undo. */
    fun setDishRemoved(index: Int, removed: Boolean) = editDish(index) { it.copy(removed = removed) }

    /**
     * Estimates a typed dish ("1 tsp ghee") and adds what comes back to the
     * sheet. The result is applied to the sheet as it is when it arrives, so
     * edits made while waiting are kept.
     */
    fun addDish(description: String) {
        val review = _state.value as? SnapMealState.Review ?: return
        if (description.isBlank() || review.addingDish || review.logging) return
        _state.value = review.copy(addingDish = true, addError = null)

        viewModelScope.launch {
            val result = culinaryClient.estimateFromText(
                description = description,
                mealType = review.mealType.name.lowercase(),
            )
            val current = _state.value as? SnapMealState.Review ?: return@launch
            _state.value = when (result) {
                is LlmResult.Success ->
                    if (result.value.items.isEmpty()) {
                        current.copy(addingDish = false, addError = noDishMessage(description, result.value.containsFood))
                    } else {
                        current.withAddedDishes(result.value.items, result.advisories)
                    }
                is LlmResult.Failure -> current.copy(addingDish = false, addError = result.message)
            }
        }
    }

    private fun editDish(index: Int, change: (ReviewDish) -> ReviewDish) {
        val review = _state.value as? SnapMealState.Review ?: return
        _state.value = review.editDish(index, change)
    }

    fun logMeal() {
        val review = _state.value as? SnapMealState.Review ?: return
        if (!review.canLog) return
        _state.value = review.copy(logging = true, logError = null)
        viewModelScope.launch {
            try {
                meals.logPhotoMeal(review.toLoggedDishes())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Back to the same sheet, edits intact - a failed save must not cost the review.
                val current = _state.value as? SnapMealState.Review ?: return@launch
                _state.value = current.copy(logging = false, logError = SAVE_FAILED)
                return@launch
            }
            // The rows now reference the photo; it is no longer ours to delete.
            pendingPath = null
            _state.value = SnapMealState.Logged(review.summary())
        }
    }

    /** Abandon the flow. The screen closes, and [onCleared] deletes the photo. */
    fun discard() {
        _state.value = SnapMealState.Cancelled
    }

    /**
     * However the flow ended without logging - cancel, back, or a failure - the
     * photo is referenced by no row, so the 90-day prune could never find it.
     * Deleted here rather than in a coroutine, because this scope is already
     * being cancelled. After a successful log [pendingPath] is null.
     */
    override fun onCleared() {
        photos.discard(pendingPath)
    }

    private companion object {
        const val KEY_PENDING_PATH = "pendingPhotoPath"
        const val SAVE_FAILED = "This meal couldn't be saved. Try again."
    }
}
