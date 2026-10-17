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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevinjones.fitmasala.core.ui.components.DefaultIndianUnits
import com.kevinjones.fitmasala.core.ui.components.EstimateBadge
import com.kevinjones.fitmasala.core.ui.components.FmAdvisory
import com.kevinjones.fitmasala.core.ui.components.FmButton
import com.kevinjones.fitmasala.core.ui.components.FmButtonGhost
import com.kevinjones.fitmasala.core.ui.components.FmButtonTonal
import com.kevinjones.fitmasala.core.ui.components.FmCard
import com.kevinjones.fitmasala.core.ui.components.FmChip
import com.kevinjones.fitmasala.core.ui.components.FmEmptyState
import com.kevinjones.fitmasala.core.ui.components.FmErrorState
import com.kevinjones.fitmasala.core.ui.components.FmKatoriLegend
import com.kevinjones.fitmasala.core.ui.components.FmListItem
import com.kevinjones.fitmasala.core.ui.components.FmListValue
import com.kevinjones.fitmasala.core.ui.components.FmSegmentedButtons
import com.kevinjones.fitmasala.core.ui.components.FmSkeletonCard
import com.kevinjones.fitmasala.core.ui.components.FmStat
import com.kevinjones.fitmasala.core.ui.components.FmStatRow
import com.kevinjones.fitmasala.core.ui.components.FmStepper
import com.kevinjones.fitmasala.core.ui.components.FmTextField
import com.kevinjones.fitmasala.core.ui.components.FmThali
import com.kevinjones.fitmasala.core.ui.components.Katori
import com.kevinjones.fitmasala.core.ui.components.SectionHeader
import com.kevinjones.fitmasala.core.ui.components.displayName
import com.kevinjones.fitmasala.core.ui.theme.Fm
import com.kevinjones.fitmasala.core.ui.theme.MaxContentWidth
import com.kevinjones.fitmasala.core.ui.theme.fm
import com.kevinjones.fitmasala.core.ui.theme.numeric
import com.kevinjones.fitmasala.core.util.DateKeys
import com.kevinjones.fitmasala.core.util.combineDateAndTime
import com.kevinjones.fitmasala.core.util.mealTypeAt
import com.kevinjones.fitmasala.core.util.pickerDateOf
import com.kevinjones.fitmasala.data.local.entity.Macros
import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.data.local.entity.PortionUnit
import com.kevinjones.fitmasala.data.remote.dto.confidenceToScore
import com.kevinjones.fitmasala.presentation.dashboard.TodayVoice
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
    onOpenSettings: () -> Unit,
    viewModel: SnapMealViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val plate by viewModel.plate.collectAsStateWithLifecycle()
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
                    // Named for the meal the clock suggests - the same guess the review starts from.
                    title = "Photograph your ${mealTypeAt(System.currentTimeMillis()).label().lowercase()}",
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
                plate = plate,
                itemModifier = itemModifier,
                onMealType = viewModel::setMealType,
                onStep = viewModel::stepDish,
                onRename = { index -> renaming = index },
                onRemove = viewModel::setDishRemoved,
                onAddDish = viewModel::addDish,
                onChangeTime = { pickingDate = true },
                onLog = viewModel::logMeal,
                onDiscard = viewModel::discard,
                onRetake = viewModel::retake,
                onLogByHand = viewModel::logByHand,
            )

            is SnapMealState.Failed -> item {
                FailedCard(
                    failed = current,
                    modifier = itemModifier,
                    onAction = { action ->
                        when (action) {
                            FailureAction.RETRY -> viewModel.retry()
                            FailureAction.RETAKE -> viewModel.retake()
                            FailureAction.OPEN_SETTINGS -> onOpenSettings()
                            FailureAction.LOG_BY_HAND -> viewModel.logByHand()
                        }
                    },
                    onDiscard = viewModel::discard,
                )
            }

            is SnapMealState.ManualEntry -> item {
                ManualDishForm(
                    eatenAt = current.eatenAt,
                    mealType = current.mealType,
                    saving = current.saving,
                    saveError = current.saveError,
                    intro = "From a label, a recipe or your own judgement. Logged at the time below" +
                        if (current.photoPath != null) ", with the photo kept." else ".",
                    modifier = itemModifier,
                    onMealType = viewModel::setMealType,
                    onSave = viewModel::saveManual,
                    onDiscard = viewModel::discard,
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
 * poured onto the day's thali, so the decision is made looking at the day; one
 * primary action.
 */
private fun LazyListScope.reviewItems(
    review: SnapMealState.Review,
    plate: DayPlate?,
    itemModifier: Modifier,
    onMealType: (MealType) -> Unit,
    onStep: (index: Int, up: Boolean) -> Unit,
    onRename: (index: Int) -> Unit,
    onRemove: (index: Int, removed: Boolean) -> Unit,
    onAddDish: (description: String) -> Unit,
    onChangeTime: () -> Unit,
    onLog: () -> Unit,
    onDiscard: () -> Unit,
    onRetake: () -> Unit,
    onLogByHand: () -> Unit,
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
                body = "Nothing in this photo could be estimated. Try another photo, type what " +
                    "you ate below, or enter the dish and its numbers yourself.",
                modifier = itemModifier,
                action = {
                    Column(verticalArrangement = Arrangement.spacedBy(Fm.snug)) {
                        FmButtonTonal("Retake photo", onClick = onRetake, modifier = Modifier.fillMaxWidth())
                        FmButtonTonal("Log by hand", onClick = onLogByHand, modifier = Modifier.fillMaxWidth())
                    }
                },
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
        item { MealOnPlateCard(review.total, plate, itemModifier) }
    }

    item {
        Column(itemModifier, verticalArrangement = Arrangement.spacedBy(Fm.snug)) {
            if (review.logError != null) {
                Text(review.logError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
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

/**
 * Why the estimate failed, and the ways forward that fit that reason - the first
 * is the likely fix, so it alone gets the primary button. The photo is kept
 * until the flow ends, so "Try again" and "Log by hand" don't need a new one.
 */
@Composable
private fun FailedCard(
    failed: SnapMealState.Failed,
    modifier: Modifier,
    onAction: (FailureAction) -> Unit,
    onDiscard: () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Fm.snug)) {
        FmErrorState(title = "This meal couldn't be estimated", body = failed.message)
        failed.actions.forEachIndexed { index, action ->
            if (index == 0) {
                FmButton(action.label, onClick = { onAction(action) }, modifier = Modifier.fillMaxWidth())
            } else {
                FmButtonTonal(action.label, onClick = { onAction(action) }, modifier = Modifier.fillMaxWidth())
            }
        }
        FmButtonGhost("Discard", onClick = onDiscard, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * One dish and its numbers, typed. Portion in Indian units first, grams last;
 * macros optional. Logged as a manual entry, so it carries no estimate badge -
 * and the Atwater check still warns about a mistyped digit before it is saved.
 *
 * Shared by Snap a meal's "Log by hand" and the standalone "Log a meal" screen,
 * which also passes [onChangeTime] so the time can be moved.
 */
@Composable
internal fun ManualDishForm(
    eatenAt: Long,
    mealType: MealType,
    saving: Boolean,
    saveError: String?,
    intro: String,
    modifier: Modifier,
    onMealType: (MealType) -> Unit,
    onSave: (ManualDishInput) -> Unit,
    onDiscard: () -> Unit,
    onChangeTime: (() -> Unit)? = null,
    title: String = "Log by hand",
) {
    var name by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf(1.0) }
    var unit by rememberSaveable { mutableStateOf(PortionUnit.KATORI) }
    var calories by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    // Problems are shown once a save has been tried, not while the form is still empty.
    var attempted by rememberSaveable { mutableStateOf(false) }

    val input = ManualDishInput(name, quantity, unit, calories, protein, carbs, fat)
    val problems = input.problems()
    val advisory = input.advisory()
    val enabled = !saving
    val numberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Fm.gap)) {
        Column(verticalArrangement = Arrangement.spacedBy(Fm.hair)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.fm.textSecondary,
            )
        }

        FmListItem(
            overline = "Eaten",
            headline = eatenAtLabel(eatenAt),
            trailing = if (onChangeTime != null) {
                { TextButton(onClick = onChangeTime, enabled = enabled) { Text("Change") } }
            } else {
                null
            },
        )

        Column(verticalArrangement = Arrangement.spacedBy(Fm.tight)) {
            SectionHeader("Which meal was this?")
            FmSegmentedButtons(
                options = MealType.entries,
                selected = mealType,
                onSelect = onMealType,
                label = { it.label() },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Fm.tight)) {
            SectionHeader("Dish")
            FmTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Rajma chawal",
                enabled = enabled,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Fm.tight)) {
            SectionHeader("Portion")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Fm.tight)) {
                items(DefaultIndianUnits, key = { it.name }) { candidate ->
                    FmChip(
                        text = candidate.displayName(),
                        selected = candidate == unit,
                        onClick = {
                            if (enabled) {
                                val switched = input.withUnit(candidate)
                                unit = switched.unit
                                quantity = switched.quantity
                            }
                        },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FmStepper(
                    value = quantity.trimmed(),
                    onIncrement = { if (enabled) quantity = input.steppedUp().quantity },
                    onDecrement = { if (enabled) quantity = input.steppedDown().quantity },
                    incrementDescription = "Increase portion",
                    decrementDescription = "Decrease portion",
                    decrementEnabled = enabled && input.canStepDown,
                )
                Text(
                    text = input.portionLabel.substringAfter(' '),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.fm.textSecondary,
                    modifier = Modifier.padding(start = Fm.tight),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Fm.tight)) {
            SectionHeader("Calories for this portion")
            FmTextField(
                value = calories,
                onValueChange = { calories = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "kcal",
                enabled = enabled,
                keyboardOptions = numberKeyboard,
            )
            SectionHeader("Macros, if you know them")
            Row(horizontalArrangement = Arrangement.spacedBy(Fm.tight)) {
                FmTextField(protein, { protein = it }, Modifier.weight(1f), placeholder = "Protein g", enabled = enabled, keyboardOptions = numberKeyboard)
                FmTextField(carbs, { carbs = it }, Modifier.weight(1f), placeholder = "Carbs g", enabled = enabled, keyboardOptions = numberKeyboard)
                FmTextField(
                    fat, { fat = it }, Modifier.weight(1f), placeholder = "Fat g", enabled = enabled,
                    keyboardOptions = numberKeyboard.copy(imeAction = ImeAction.Done),
                )
            }
        }

        if (advisory != null) FmAdvisory(advisory)
        if (attempted) {
            problems.forEach { problem ->
                Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        if (saveError != null) {
            Text(saveError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Column(verticalArrangement = Arrangement.spacedBy(Fm.snug)) {
            FmButton(
                text = if (saving) "Logging…" else "Log dish",
                onClick = {
                    attempted = true
                    if (problems.isEmpty()) onSave(input)
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            FmButtonGhost("Discard", onClick = onDiscard, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Step one of changing when the meal was eaten. Future days can't be picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EatenAtDateDialog(eatenAt: Long, onPicked: (dateUtcMillis: Long) -> Unit, onDismiss: () -> Unit) {
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
internal fun EatenAtTimeDialog(eatenAt: Long, onPicked: (hour: Int, minute: Int) -> Unit, onDismiss: () -> Unit) {
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

/**
 * This meal on the plate of the day it lands on: what is logged drawn solid, this
 * meal poured in pale on top - Today's card, one meal ahead. Going over shows in
 * red here, before "Log meal", rather than on Today after it.
 */
@Composable
private fun MealOnPlateCard(total: Macros, plate: DayPlate?, modifier: Modifier) {
    // Room answers in milliseconds; until then, the plain sum.
    if (plate == null) return TotalCard(total, modifier)
    val fm = MaterialTheme.fm
    val eaten = plate.eaten
    val target = plate.target
    val katoris = listOf(
        Katori(eaten.proteinG.toFloat(), target.proteinG.toFloat(), fm.macroProtein, "Protein", total.proteinG.toFloat()),
        Katori(eaten.carbsG.toFloat(), target.carbsG.toFloat(), fm.macroCarbs, "Carbs", total.carbsG.toFloat()),
        Katori(eaten.fatG.toFloat(), target.fatG.toFloat(), fm.macroFat, "Fat", total.fatG.toFloat()),
    )
    val line = TodayVoice.afterMeal(
        day = TodayVoice.dayName(plate.dayEpoch, DateKeys.today()),
        eatenKcal = eaten.calories.roundToInt(),
        addingKcal = total.calories.roundToInt(),
        targetKcal = target.calories,
    )

    FmCard(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Fm.gap)) {
        Text(line, style = MaterialTheme.typography.titleMedium)
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Fm.snug),
        ) {
            FmThali(
                kcal = eaten.calories.toFloat(),
                kcalTarget = target.calories.toFloat(),
                katoris = katoris,
                kcalAdding = total.calories.toFloat(),
                size = 168.dp,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text("+${"%,d".format(total.calories.roundToInt())}", style = numeric(28))
                Text(
                    "  kcal this meal",
                    style = MaterialTheme.typography.labelSmall,
                    color = fm.textSecondary,
                    modifier = Modifier.padding(bottom = Fm.hair),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            katoris.forEach { FmKatoriLegend(it) }
        }
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
