package com.kevinjones.fitmasala.presentation.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevinjones.fitmasala.core.ui.components.FmButton
import com.kevinjones.fitmasala.core.ui.components.FmButtonGhost
import com.kevinjones.fitmasala.core.ui.components.FmCard
import com.kevinjones.fitmasala.core.ui.components.FmEmptyState
import com.kevinjones.fitmasala.core.ui.components.FmInsightStrip
import com.kevinjones.fitmasala.core.ui.components.FmRadioRow
import com.kevinjones.fitmasala.core.ui.components.FmSegmentedButtons
import com.kevinjones.fitmasala.core.ui.components.FmStat
import com.kevinjones.fitmasala.core.ui.components.FmStatRow
import com.kevinjones.fitmasala.core.ui.components.FmTextField
import com.kevinjones.fitmasala.core.ui.components.FmTrendChart
import com.kevinjones.fitmasala.core.ui.components.SectionHeader
import com.kevinjones.fitmasala.core.ui.components.enterFromBelow
import com.kevinjones.fitmasala.core.ui.theme.Fm
import com.kevinjones.fitmasala.core.ui.theme.fm
import com.kevinjones.fitmasala.core.ui.theme.numeric
import com.kevinjones.fitmasala.domain.plan.ActivityLevel
import com.kevinjones.fitmasala.domain.plan.CutAggression
import com.kevinjones.fitmasala.domain.plan.Sex

/**
 * The cut, made visible, from real weigh-ins.
 *
 * The trend chart is the point: raw weigh-ins scatter, the smoothed line does
 * not, and only the line drives the plan. Seeing both at once is what stops a
 * two-kilo water swing reading as failure - the most common reason a working cut
 * gets abandoned.
 *
 * The smoothing drawn here is the SAME WeightTrend the engine uses, taken from
 * the repository snapshot. A chart that smoothed differently from the maths
 * would be a lie about how the app works.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: PlanViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    val projection = snapshot.projection
    val adjustment = snapshot.adjustment
    var settingUp by rememberSaveable { mutableStateOf(false) }
    var weighing by rememberSaveable { mutableStateOf(false) }

    if (weighing) {
        ModalBottomSheet(
            onDismissRequest = { weighing = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            WeighInSheet(
                sex = snapshot.sex ?: Sex.MALE,
                lastWeightKg = snapshot.weights.lastOrNull()?.weightKg,
                onSave = {
                    viewModel.logWeighIn(it)
                    weighing = false
                },
                onCancel = { weighing = false },
            )
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Fm.gap),
    ) {
        if (snapshot.needsSetup) {
            if (settingUp) {
                item("setup-form") {
                    CutSetupCard(
                        onStart = {
                            viewModel.startCut(it)
                            settingUp = false
                        },
                        onCancel = { settingUp = false },
                    )
                }
            } else {
                item("setup") { SetupPrompt(onStart = { settingUp = true }) }
            }
            return@LazyColumn
        }

        if (adjustment != null) {
            item("verdict") {
                FmInsightStrip(
                    emphasis = adjustment.verdict.name.lowercase()
                        .replace('_', ' ')
                        .replaceFirstChar { it.uppercase() },
                    text = adjustment.rationale,
                    modifier = Modifier.enterFromBelow(0),
                )
            }
        }

        item("chart-header") {
            SectionHeader(
                title = "Weight",
                action = "Log weigh-in",
                onAction = { weighing = true },
            )
        }

        item("chart") {
            FmCard(
                Modifier.fillMaxWidth().enterFromBelow(1),
                verticalArrangement = Arrangement.spacedBy(Fm.gutter),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "%.1f".format(snapshot.trend.lastOrNull()?.weightKg ?: 0.0),
                        style = numeric(34),
                    )
                    Text(
                        "  kg trend",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.fm.textSecondary,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                FmTrendChart(
                    raw = snapshot.weights.map { it.weightKg.toFloat() },
                    trend = snapshot.trend.map { it.weightKg.toFloat() },
                    goal = (projection?.projectedGoalWeightKg ?: 0.0).toFloat(),
                )
            }
        }

        if (projection != null) {
            item("projection-header") { SectionHeader("Projection") }
            item("projection") { ProjectionCard(projection) }
        }
    }
}

@Composable
private fun SetupPrompt(onStart: () -> Unit) {
    FmEmptyState(
        icon = Icons.Outlined.MonitorWeight,
        title = "No cut set up yet",
        body = "Add your height, weight and a body-fat estimate, and the app works " +
            "out a target that adapts to what the scale actually does.",
        action = { FmButton(text = "Start a cut", onClick = onStart) },
    )
}

/**
 * The one-off setup. Body fat is the input the whole plan pivots on and the
 * least reliable one, so it can come from either a typed number (scale, DEXA,
 * an honest guess) or the tape, and the tape's result is shown before it is
 * used. Errors appear only after the first attempt, next to their own field.
 */
@Composable
private fun CutSetupCard(onStart: (CutSetup) -> Unit, onCancel: () -> Unit) {
    var form by rememberSaveable(stateSaver = CutSetupFormSaver) { mutableStateOf(CutSetupForm()) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val result = form.validate()
    val errors = if (attempted) (result as? FormResult.Invalid)?.errors.orEmpty() else emptyMap()

    FmCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Fm.gutter)) {
        Text("About you", style = MaterialTheme.typography.titleMedium)
        FmSegmentedButtons(
            options = Sex.entries,
            selected = form.sex,
            onSelect = { form = form.copy(sex = it) },
            label = { if (it == Sex.MALE) "Male" else "Female" },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Fm.tight)) {
            NumberField("Height", "cm", form.height, errors[PlanField.HEIGHT], Modifier.weight(1f)) {
                form = form.copy(height = it)
            }
            NumberField("Age", "years", form.age, errors[PlanField.AGE], Modifier.weight(1f), decimal = false) {
                form = form.copy(age = it)
            }
            NumberField("Weight", "kg", form.weight, errors[PlanField.WEIGHT], Modifier.weight(1f)) {
                form = form.copy(weight = it)
            }
        }

        Text("Body fat", style = MaterialTheme.typography.titleMedium)
        NumberField("Your estimate", "%", form.bodyFat, errors[PlanField.BODY_FAT], Modifier.fillMaxWidth()) {
            form = form.copy(bodyFat = it)
        }
        Text(
            "Or measure with a tape - the same method the weekly weigh-in uses.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.fm.textSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Fm.tight)) {
            NumberField("Neck", "cm", form.neck, null, Modifier.weight(1f)) { form = form.copy(neck = it) }
            NumberField("Waist", "cm", form.waist, errors[PlanField.WAIST], Modifier.weight(1f)) {
                form = form.copy(waist = it)
            }
            if (form.sex == Sex.FEMALE) {
                NumberField("Hip", "cm", form.hip, null, Modifier.weight(1f)) { form = form.copy(hip = it) }
            }
        }
        form.tapeBodyFat?.let { tape ->
            Text(
                "Tape reads %.1f%%".format(tape) +
                    if (form.bodyFat.isNotBlank()) " - your typed estimate is used instead" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.fm.textSecondary,
            )
        }
        NumberField("Goal", "%", form.goal, errors[PlanField.GOAL], Modifier.fillMaxWidth()) {
            form = form.copy(goal = it)
        }

        Text("Activity", style = MaterialTheme.typography.titleMedium)
        Column {
            ActivityLevel.entries.forEach { level ->
                FmRadioRow(
                    title = level.label,
                    selected = form.activity == level,
                    onSelect = { form = form.copy(activity = level) },
                )
            }
        }

        Text("Pace", style = MaterialTheme.typography.titleMedium)
        Column {
            CutAggression.entries.forEach { pace ->
                FmRadioRow(
                    title = pace.label,
                    supporting = "%.1f%% of bodyweight a week".format(pace.weeklyRatePctBodyweight),
                    selected = form.aggression == pace,
                    onSelect = { form = form.copy(aggression = pace) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Fm.tight)) {
            FmButtonGhost(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
            FmButton(
                text = "Start the cut",
                onClick = {
                    attempted = true
                    (result as? FormResult.Valid)?.let { onStart(it.value) }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The daily ritual: weight only, prefilled with nothing - a prefilled number
 * gets confirmed without stepping on the scale. The tape is optional and
 * collapsed into the same sheet, because a weekly measurement should not cost
 * a second screen.
 */
@Composable
private fun WeighInSheet(
    sex: Sex,
    lastWeightKg: Double?,
    onSave: (WeighIn) -> Unit,
    onCancel: () -> Unit,
) {
    var form by rememberSaveable(stateSaver = WeighInFormSaver) { mutableStateOf(WeighInForm()) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val result = form.validate(sex)
    val errors = if (attempted) (result as? FormResult.Invalid)?.errors.orEmpty() else emptyMap()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Fm.gutter)
            .navigationBarsPadding()
            .padding(bottom = Fm.gutter),
        verticalArrangement = Arrangement.spacedBy(Fm.gutter),
    ) {
        Text("Log weigh-in", style = MaterialTheme.typography.titleLarge)
        NumberField(
            label = "Weight",
            unit = if (lastWeightKg != null) "kg - last %.1f".format(lastWeightKg) else "kg",
            value = form.weight,
            error = errors[PlanField.WEIGHT],
            modifier = Modifier.fillMaxWidth(),
        ) { form = form.copy(weight = it) }

        Text(
            "Tape, if you measured today",
            style = MaterialTheme.typography.titleSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Fm.tight)) {
            NumberField("Waist", "cm", form.waist, errors[PlanField.WAIST], Modifier.weight(1f)) {
                form = form.copy(waist = it)
            }
            NumberField("Neck", "cm", form.neck, errors[PlanField.NECK], Modifier.weight(1f)) {
                form = form.copy(neck = it)
            }
            if (sex == Sex.FEMALE) {
                NumberField("Hip", "cm", form.hip, errors[PlanField.HIP], Modifier.weight(1f)) {
                    form = form.copy(hip = it)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Fm.tight)) {
            FmButtonGhost(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
            FmButton(
                text = "Save",
                onClick = {
                    attempted = true
                    (result as? FormResult.Valid)?.let { onSave(it.value) }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A labelled number input with its unit, and its own error under it. */
@Composable
private fun NumberField(
    label: String,
    unit: String,
    value: String,
    error: String?,
    modifier: Modifier = Modifier,
    decimal: Boolean = true,
    onChange: (String) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Fm.hair)) {
        Text(
            "$label ($unit)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.fm.textSecondary,
        )
        FmTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
        )
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ProjectionCard(
    projection: com.kevinjones.fitmasala.domain.plan.PlanProjection,
) {
    FmCard(
        Modifier.fillMaxWidth().enterFromBelow(2),
        verticalArrangement = Arrangement.spacedBy(Fm.gutter),
    ) {
        FmStatRow {
            FmStat("%.0f%%".format(projection.current.bodyFatPercent), "body fat now")
            FmStat(
                "%.0f%%".format(projection.goalBodyFatPercent),
                "goal",
                tint = MaterialTheme.fm.progress,
            )
            FmStat("${projection.estimatedWeeks.toInt()} wks", "to go")
        }
        Text(
            "Target ${projection.dailyTarget.calories} kcal - " +
                "${projection.dailyTarget.proteinG}g protein. " +
                "Maintenance ${projection.maintenanceCalories}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.fm.textSecondary,
        )
        // Clamps and floors are surfaced, never silent: a plan that has been
        // capped must not keep promising the original date.
        projection.warnings.forEach { warning ->
            Text(
                text = warning.name.lowercase().replace('_', ' ')
                    .replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

// Forms survive rotation and process death as their typed text, not as parsed
// values, so a half-typed field comes back exactly as it was left.
private val CutSetupFormSaver = listSaver<CutSetupForm, String>(
    save = {
        listOf(
            it.sex.name, it.height, it.age, it.weight, it.bodyFat, it.neck, it.waist, it.hip,
            it.activity.name, it.aggression.name, it.goal,
        )
    },
    restore = {
        CutSetupForm(
            sex = Sex.valueOf(it[0]),
            height = it[1],
            age = it[2],
            weight = it[3],
            bodyFat = it[4],
            neck = it[5],
            waist = it[6],
            hip = it[7],
            activity = ActivityLevel.valueOf(it[8]),
            aggression = CutAggression.valueOf(it[9]),
            goal = it[10],
        )
    },
)

private val WeighInFormSaver = listSaver<WeighInForm, String>(
    save = { listOf(it.weight, it.waist, it.neck, it.hip) },
    restore = { WeighInForm(weight = it[0], waist = it[1], neck = it[2], hip = it[3]) },
)
