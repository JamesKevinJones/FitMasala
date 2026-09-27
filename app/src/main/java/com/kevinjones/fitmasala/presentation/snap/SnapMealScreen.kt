package com.kevinjones.fitmasala.presentation.snap

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.RamenDining
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevinjones.fitmasala.core.ui.components.EstimateBadge
import com.kevinjones.fitmasala.core.ui.components.FmAdvisory
import com.kevinjones.fitmasala.core.ui.components.FmButton
import com.kevinjones.fitmasala.core.ui.components.FmButtonGhost
import com.kevinjones.fitmasala.core.ui.components.FmCard
import com.kevinjones.fitmasala.core.ui.components.FmEmptyState
import com.kevinjones.fitmasala.core.ui.components.FmErrorState
import com.kevinjones.fitmasala.core.ui.components.FmListItem
import com.kevinjones.fitmasala.core.ui.components.FmListValue
import com.kevinjones.fitmasala.core.ui.components.FmSegmentedButtons
import com.kevinjones.fitmasala.core.ui.components.FmSkeletonCard
import com.kevinjones.fitmasala.core.ui.components.FmStat
import com.kevinjones.fitmasala.core.ui.components.FmStatRow
import com.kevinjones.fitmasala.core.ui.components.SectionHeader
import com.kevinjones.fitmasala.core.ui.theme.Fm
import com.kevinjones.fitmasala.core.ui.theme.MaxContentWidth
import com.kevinjones.fitmasala.core.ui.theme.fm
import com.kevinjones.fitmasala.data.local.entity.Macros
import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.data.remote.dto.PhotoItemDto
import com.kevinjones.fitmasala.data.remote.dto.confidenceToScore
import kotlin.math.roundToInt

/**
 * "Snap a meal": opens the phone's own camera app, then shows the estimate for
 * review before anything is logged.
 *
 * The system camera rather than an in-app preview: its focus and exposure are
 * better than anything worth building here, and the photo lands straight in
 * app-private storage through [com.kevinjones.fitmasala.data.photo.MealPhotoStore].
 */
@Composable
fun SnapMealScreen(
    contentPadding: PaddingValues,
    onClose: () -> Unit,
    onLogged: (summary: String) -> Unit,
    viewModel: SnapMealViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        viewModel.onCaptureResult(taken)
    }
    // The app declares CAMERA, so Android refuses to hand the camera app over
    // until it is granted - even though the camera app takes the photo.
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePicture.launch(viewModel.prepareCapture()) else viewModel.onPermissionDenied()
    }
    val openCamera: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) takePicture.launch(viewModel.prepareCapture()) else requestCamera.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(Unit) {
        if (viewModel.shouldAutoLaunchCamera()) openCamera()
    }
    LaunchedEffect(state) {
        when (val current = state) {
            SnapMealState.Cancelled -> onClose()
            is SnapMealState.Logged -> onLogged(current.summary)
            else -> Unit
        }
    }

    val itemModifier = Modifier.fillMaxWidth().widthIn(max = MaxContentWidth)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Fm.gap),
    ) {
        when (val current = state) {
            SnapMealState.Capturing -> item {
                FmEmptyState(
                    icon = Icons.Outlined.CameraAlt,
                    title = "Photograph your plate",
                    body = "Shoot from above with the whole plate in frame. A katori or roti " +
                        "beside the food helps judge the portion.",
                    modifier = itemModifier,
                    action = { FmButton("Open camera", onClick = openCamera) },
                )
            }

            SnapMealState.PermissionDenied -> item {
                Column(itemModifier, verticalArrangement = Arrangement.spacedBy(Fm.snug)) {
                    FmErrorState(
                        title = "Camera access is off",
                        body = "The camera is only used to photograph meals, and photos stay on this " +
                            "phone. If Android no longer asks, turn it on in Settings › Apps › " +
                            "FitMasala › Permissions.",
                        onRetry = openCamera,
                        retryLabel = "Allow camera",
                    )
                    FmButtonGhost("Back", onClick = viewModel::discard)
                }
            }

            is SnapMealState.Estimating -> {
                item {
                    Column(itemModifier, verticalArrangement = Arrangement.spacedBy(Fm.hair)) {
                        Text("Estimating your meal", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Reading each dish and its portion. This takes a few seconds.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.fm.textSecondary,
                        )
                    }
                }
                items(3) { FmSkeletonCard(itemModifier) }
            }

            is SnapMealState.Review -> reviewItems(
                review = current,
                itemModifier = itemModifier,
                onMealType = viewModel::setMealType,
                onLog = viewModel::logMeal,
                onDiscard = viewModel::discard,
            )

            is SnapMealState.Failed -> item {
                FmErrorState(
                    title = "This meal couldn't be estimated",
                    body = current.message,
                    modifier = itemModifier,
                    onRetry = viewModel::discard,
                    retryLabel = "Back",
                )
            }

            SnapMealState.Cancelled, is SnapMealState.Logged -> Unit
        }
    }
}

/**
 * The review: every Dish with its portion, confidence and what the model could
 * not see; any advisories first, where they cannot be scrolled past; the total
 * as the sum of the dishes; one primary action.
 */
private fun LazyListScope.reviewItems(
    review: SnapMealState.Review,
    itemModifier: Modifier,
    onMealType: (MealType) -> Unit,
    onLog: () -> Unit,
    onDiscard: () -> Unit,
) {
    items(review.advisories) { advisory -> FmAdvisory(advisory, itemModifier) }

    item {
        Column(itemModifier, verticalArrangement = Arrangement.spacedBy(Fm.tight)) {
            SectionHeader("Which meal was this?")
            FmSegmentedButtons(
                options = MealType.entries,
                selected = review.mealType,
                onSelect = onMealType,
                label = { it.label() },
            )
        }
    }

    if (review.dishes.isEmpty()) {
        item {
            FmEmptyState(
                icon = Icons.Outlined.RamenDining,
                title = "No dishes found",
                body = "Nothing in this photo could be estimated, so there is nothing to log.",
                modifier = itemModifier,
            )
        }
    } else {
        item {
            SectionHeader(
                if (review.dishes.size == 1) "1 dish" else "${review.dishes.size} dishes",
                modifier = itemModifier,
            )
        }
        items(review.dishes) { dish -> DishCard(dish, itemModifier) }
        item { TotalCard(review.total, itemModifier) }
    }

    item {
        Column(itemModifier, verticalArrangement = Arrangement.spacedBy(Fm.snug)) {
            FmButton(
                text = if (review.logging) "Logging…" else "Log meal",
                onClick = onLog,
                enabled = review.canLog,
                modifier = Modifier.fillMaxWidth(),
            )
            FmButtonGhost("Discard", onClick = onDiscard, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DishCard(dish: PhotoItemDto, modifier: Modifier) {
    FmCard(modifier = modifier, contentPadding = PaddingValues(vertical = Fm.hair)) {
        FmListItem(
            headline = dish.name,
            supporting = dish.portionLabel(),
            trailing = { FmListValue("${dish.macros.calories.roundToInt()}", "kcal") },
        )
        // An estimate never looks like a weighed value.
        EstimateBadge(
            confidence = confidenceToScore(dish.confidence),
            modifier = Modifier.padding(start = Fm.gutter),
        )
        Text(
            text = dish.uncertaintyNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.fm.textSecondary,
            modifier = Modifier.padding(start = Fm.gutter, end = Fm.gutter, bottom = Fm.snug),
        )
    }
}

/** The sum of the dishes. Warm macro colours: this is food, not progress. */
@Composable
private fun TotalCard(total: Macros, modifier: Modifier) {
    FmCard(modifier = modifier) {
        Text("Meal total", style = MaterialTheme.typography.titleSmall)
        FmStatRow {
            FmStat("${total.calories.roundToInt()}", "kcal")
            FmStat("${total.proteinG.roundToInt()} g", "protein", tint = MaterialTheme.fm.macroProtein)
            FmStat("${total.carbsG.roundToInt()} g", "carbs", tint = MaterialTheme.fm.macroCarbs)
            FmStat("${total.fatG.roundToInt()} g", "fat", tint = MaterialTheme.fm.macroFat)
        }
    }
}
