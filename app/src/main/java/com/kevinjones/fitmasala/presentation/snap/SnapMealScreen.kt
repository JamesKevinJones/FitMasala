package com.kevinjones.fitmasala.presentation.snap

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RamenDining
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevinjones.fitmasala.core.ui.components.EstimateBadge
import com.kevinjones.fitmasala.core.ui.components.FmAdvisory
import com.kevinjones.fitmasala.core.ui.components.FmButton
import com.kevinjones.fitmasala.core.ui.components.FmButtonGhost
import com.kevinjones.fitmasala.core.ui.components.FmButtonTonal
import com.kevinjones.fitmasala.core.ui.components.FmCard
import com.kevinjones.fitmasala.core.ui.components.FmEmptyState
import com.kevinjones.fitmasala.core.ui.components.FmErrorState
import com.kevinjones.fitmasala.core.ui.components.FmListItem
import com.kevinjones.fitmasala.core.ui.components.FmListValue
import com.kevinjones.fitmasala.core.ui.components.FmSegmentedButtons
import com.kevinjones.fitmasala.core.ui.components.FmSkeletonCard
import com.kevinjones.fitmasala.core.ui.components.FmStat
import com.kevinjones.fitmasala.core.ui.components.FmStatRow
import com.kevinjones.fitmasala.core.ui.components.FmStepper
import com.kevinjones.fitmasala.core.ui.components.FmTextField
import com.kevinjones.fitmasala.core.ui.components.SectionHeader
import com.kevinjones.fitmasala.core.ui.theme.Fm
import com.kevinjones.fitmasala.core.ui.theme.MaxContentWidth
import com.kevinjones.fitmasala.core.ui.theme.fm
import com.kevinjones.fitmasala.core.util.combineDateAndTime
import com.kevinjones.fitmasala.core.util.pickerDateOf
import com.kevinjones.fitmasala.data.local.entity.Macros
import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.data.remote.dto.confidenceToScore
import java.time.Instant
import java.time.ZoneId
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
    /** Index of the dish being renamed; null when no dialog is open. */
    var renaming by rememberSaveable { mutableStateOf<Int?>(null) }
    /** Changing when the meal was eaten: the date dialog, then the time dialog for [pickedDate]. */
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickedDate by rememberSaveable { mutableStateOf<Long?>(null) }

    // The system photo picker: no storage permission, and only the chosen photo is shared.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onGalleryPicked(uri)
    }
    val openGallery: () -> Unit = {
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

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
                        "beside the food helps judge the portion. A photo from earlier is " +
                        "logged at the time it was taken.",
                    modifier = itemModifier,
                    action = {
                        Column(verticalArrangement = Arrangement.spacedBy(Fm.snug)) {
                            FmButton("Take photo", onClick = openCamera, modifier = Modifier.fillMaxWidth())
                            FmButtonTonal("Choose from gallery", onClick = openGallery, modifier = Modifier.fillMaxWidth())
                        }
                    },
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
                    // The gallery needs no camera, so back is to the choice, not out.
                    FmButtonGhost("Back", onClick = viewModel::backToChoice)
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
                onStep = viewModel::stepDish,
                onRename = { index -> renaming = index },
                onRemove = viewModel::setDishRemoved,
                onAddDish = viewModel::addDish,
                onChangeTime = { pickingDate = true },
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

    val review = state as? SnapMealState.Review
    if (review != null && pickingDate) {
        EatenAtDateDialog(
            eatenAt = review.eatenAt,
            onPicked = { date ->
                pickingDate = false
                pickedDate = date
            },
            onDismiss = { pickingDate = false },
        )
    }
    val date = pickedDate
    if (review != null && date != null) {
        EatenAtTimeDialog(
            eatenAt = review.eatenAt,
            onPicked = { hour, minute ->
                viewModel.setEatenAt(combineDateAndTime(date, hour, minute, ZoneId.systemDefault()))
                pickedDate = null
            },
            onDismiss = { pickedDate = null },
        )
    }

    val renamingDish = renaming?.let { review?.dishes?.getOrNull(it) }
    if (renamingDish != null) {
        RenameDishDialog(
            current = renamingDish.name,
            onRename = { name ->
                renaming?.let { viewModel.renameDish(it, name) }
                renaming = null
            },
            onDismiss = { renaming = null },
        )
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
    onStep: (index: Int, up: Boolean) -> Unit,
    onRename: (index: Int) -> Unit,
    onRemove: (index: Int, removed: Boolean) -> Unit,
    onAddDish: (description: String) -> Unit,
    onChangeTime: () -> Unit,
    onLog: () -> Unit,
    onDiscard: () -> Unit,
) {
    if (review.alreadyLogged) {
        item { FmAdvisory(alreadyLoggedMessage(review.eatenAt), itemModifier) }
    }
    items(review.advisories) { advisory -> FmAdvisory(advisory, itemModifier) }

    item {
        Column(itemModifier, verticalArrangement = Arrangement.spacedBy(Fm.tight)) {
            // The day the meal lands on follows this time - a late log still counts
            // on the day it was eaten.
            FmListItem(
                overline = "Eaten",
                headline = eatenAtLabel(review.eatenAt),
                trailing = {
                    TextButton(onClick = onChangeTime, enabled = !review.logging) { Text("Change") }
                },
            )
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
                body = "Nothing in this photo could be estimated. Type what you ate below, or discard it.",
                modifier = itemModifier,
            )
        }
    } else {
        item {
            val count = review.kept.size
            SectionHeader(if (count == 1) "1 dish" else "$count dishes", modifier = itemModifier)
        }
        itemsIndexed(review.dishes) { index, dish ->
            if (dish.removed) {
                RemovedDishRow(dish, itemModifier, onUndo = { onRemove(index, false) })
            } else {
                DishCard(
                    dish = dish,
                    modifier = itemModifier,
                    editable = !review.logging,
                    onStep = { up -> onStep(index, up) },
                    onRename = { onRename(index) },
                    onRemove = { onRemove(index, true) },
                )
            }
        }
    }

    item {
        AddDishRow(
            dishCount = review.dishes.size,
            adding = review.addingDish,
            error = review.addError,
            enabled = !review.logging,
            onAdd = onAddDish,
            modifier = itemModifier,
        )
    }

    if (review.dishes.isNotEmpty()) {
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

/**
 * One dish, correctable in place: step the portion (macros follow), rename it, or
 * remove it. The unit is fixed - a katori stays a katori.
 */
@Composable
private fun DishCard(
    dish: ReviewDish,
    modifier: Modifier,
    editable: Boolean,
    onStep: (up: Boolean) -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    FmCard(modifier = modifier, contentPadding = PaddingValues(vertical = Fm.hair)) {
        FmListItem(
            headline = dish.name,
            supporting = dish.portionLabel,
            trailing = { FmListValue("${dish.macros.calories.roundToInt()}", "kcal") },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Fm.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FmStepper(
                value = dish.quantity.trimmed(),
                onIncrement = { if (editable) onStep(true) },
                onDecrement = { if (editable) onStep(false) },
                incrementDescription = dish.stepDescription(up = true),
                decrementDescription = dish.stepDescription(up = false),
                decrementEnabled = editable && dish.canStepDown,
            )
            Text(
                text = dish.portionLabel.substringAfter(' '),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.fm.textSecondary,
                modifier = Modifier.padding(start = Fm.tight).weight(1f),
            )
            IconButton(onClick = onRename, enabled = editable) {
                Icon(Icons.Outlined.Edit, contentDescription = "Rename ${dish.name}")
            }
            IconButton(onClick = onRemove, enabled = editable) {
                Icon(Icons.Outlined.Delete, contentDescription = "Remove ${dish.name}")
            }
        }
        // An estimate never looks like a weighed value - stepped or not.
        EstimateBadge(
            confidence = confidenceToScore(dish.original.confidence),
            modifier = Modifier.padding(start = Fm.gutter),
        )
        Text(
            text = dish.original.uncertaintyNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.fm.textSecondary,
            modifier = Modifier.padding(start = Fm.gutter, end = Fm.gutter, bottom = Fm.snug),
        )
    }
}

/**
 * What the camera missed - the ghee on the roti, the pickle, a second helping -
 * typed and estimated with the same rules as the photo, then added to the sheet.
 */
@Composable
private fun AddDishRow(
    dishCount: Int,
    adding: Boolean,
    error: String?,
    enabled: Boolean,
    onAdd: (String) -> Unit,
    modifier: Modifier,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    // A successful add grows the sheet: that is when the draft has done its job.
    // Tracked rather than keyed on first composition, so rotating keeps the draft.
    var seenCount by rememberSaveable { mutableIntStateOf(dishCount) }
    LaunchedEffect(dishCount) {
        if (dishCount > seenCount) draft = ""
        seenCount = dishCount
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Fm.tight)) {
        SectionHeader("Missed something?")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Fm.tight),
        ) {
            FmTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = "1 tsp ghee, a spoon of pickle",
                enabled = enabled && !adding,
            )
            FmButtonTonal(
                text = if (adding) "Adding…" else "Add",
                onClick = { onAdd(draft) },
                enabled = enabled && !adding && draft.isNotBlank(),
            )
        }
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Step one of changing when the meal was eaten. Future days can't be picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EatenAtDateDialog(eatenAt: Long, onPicked: (dateUtcMillis: Long) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val today = pickerDateOf(System.currentTimeMillis(), zone)
    val state = rememberDatePickerState(
        initialSelectedDateMillis = pickerDateOf(eatenAt, zone),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= today
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let(onPicked) },
                enabled = state.selectedDateMillis != null,
            ) { Text("Next") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

/** Step two: the time on that day. A time later than now is clamped to now by the model. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EatenAtTimeDialog(eatenAt: Long, onPicked: (hour: Int, minute: Int) -> Unit, onDismiss: () -> Unit) {
    val local = Instant.ofEpochMilli(eatenAt).atZone(ZoneId.systemDefault())
    val state = rememberTimePickerState(
        initialHour = local.hour,
        initialMinute = local.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What time did you eat?") },
        text = { TimeInput(state = state) },
        confirmButton = { TextButton(onClick = { onPicked(state.hour, state.minute) }) { Text("Set time") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A removed dish stays in place as one line, so a mistaken tap is one tap to undo. */
@Composable
private fun RemovedDishRow(dish: ReviewDish, modifier: Modifier, onUndo: () -> Unit) {
    FmCard(modifier = modifier, contentPadding = PaddingValues(vertical = Fm.hair)) {
        FmListItem(
            headline = dish.name,
            supporting = "Removed - not logged",
            trailing = { TextButton(onClick = onUndo) { Text("Undo") } },
        )
    }
}

@Composable
private fun RenameDishDialog(current: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename dish") },
        text = { FmTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            TextButton(onClick = { onRename(text) }, enabled = text.isNotBlank()) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
