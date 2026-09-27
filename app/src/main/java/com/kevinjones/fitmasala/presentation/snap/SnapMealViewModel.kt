package com.kevinjones.fitmasala.presentation.snap

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevinjones.fitmasala.core.util.mealTypeAt
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

    /** The camera opens by itself once per visit, never again on recomposition or rotation. */
    private var autoLaunched: Boolean
        get() = savedState[KEY_AUTO_LAUNCHED] ?: false
        set(value) { savedState[KEY_AUTO_LAUNCHED] = value }

    fun shouldAutoLaunchCamera(): Boolean = !autoLaunched && _state.value == SnapMealState.Capturing

    /** A fresh file for the camera app to write into, as the URI it is allowed to use. */
    fun prepareCapture(): Uri {
        autoLaunched = true
        photos.discard(pendingPath)
        val file = photos.newPhotoFile()
        pendingPath = file.absolutePath
        return photos.uriFor(file)
    }

    fun onPermissionDenied() {
        autoLaunched = true
        _state.value = SnapMealState.PermissionDenied
    }

    /** From the camera app. `false` means the user backed out without a photo. */
    fun onCaptureResult(taken: Boolean) {
        val path = pendingPath ?: return
        if (!taken) {
            discard()
            return
        }
        val eatenAt = System.currentTimeMillis()
        estimate(path, eatenAt, mealTypeAt(eatenAt))
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
                _state.value = SnapMealState.Failed(path, "This photo couldn't be read. Take it again.")
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
                is LlmResult.Failure -> SnapMealState.Failed(path, result.message)
            }
        }
    }

    fun setMealType(mealType: MealType) {
        val review = _state.value as? SnapMealState.Review ?: return
        if (!review.logging) _state.value = review.copy(mealType = mealType)
    }

    fun logMeal() {
        val review = _state.value as? SnapMealState.Review ?: return
        if (!review.canLog) return
        _state.value = review.copy(logging = true)
        viewModelScope.launch {
            try {
                meals.logPhotoMeal(review.toLoggedDishes())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = SnapMealState.Failed(review.photoPath, "This meal couldn't be saved. Try again.")
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
        const val KEY_AUTO_LAUNCHED = "cameraAutoLaunched"
    }
}
