package com.kevinjones.fitmasala.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.RamenDining
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevinjones.fitmasala.core.ui.components.EstimateBadge
import com.kevinjones.fitmasala.core.ui.components.FmCard
import com.kevinjones.fitmasala.core.ui.components.FmDivider
import com.kevinjones.fitmasala.core.ui.components.FmEmptyState
import com.kevinjones.fitmasala.core.ui.components.FmListIcon
import com.kevinjones.fitmasala.core.ui.components.FmListItem
import com.kevinjones.fitmasala.core.ui.components.FmListValue
import com.kevinjones.fitmasala.core.ui.components.FmSkeletonCard
import com.kevinjones.fitmasala.core.ui.components.FmStat
import com.kevinjones.fitmasala.core.ui.components.FmStatRow
import com.kevinjones.fitmasala.core.ui.components.FmKatoriLegend
import com.kevinjones.fitmasala.core.ui.components.FmThali
import com.kevinjones.fitmasala.core.ui.components.Katori
import com.kevinjones.fitmasala.core.ui.components.LevelBar
import com.kevinjones.fitmasala.core.ui.components.SectionHeader
import com.kevinjones.fitmasala.core.ui.components.StreakBadge
import com.kevinjones.fitmasala.core.ui.components.animatedCount
import com.kevinjones.fitmasala.core.ui.components.enterFromBelow
import com.kevinjones.fitmasala.core.ui.theme.Fm
import com.kevinjones.fitmasala.core.ui.theme.MaxContentWidth
import com.kevinjones.fitmasala.core.ui.theme.fm
import com.kevinjones.fitmasala.core.ui.theme.horizontalMargin
import com.kevinjones.fitmasala.core.ui.theme.numeric
import com.kevinjones.fitmasala.core.ui.theme.screenContentPadding
import com.kevinjones.fitmasala.core.util.DateKeys
import com.kevinjones.fitmasala.data.local.entity.LoggedMealEntity
import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.data.local.relation.SessionSummary
import java.time.LocalTime

/**
 * Today: one plate, then what went on it, then the training around it.
 *
 * Built as a FEED - Android's canonical layout for a screen of equivalent blocks
 * scanned quickly - in a `LazyColumn`, because the meal list is unbounded and a
 * plain Column composes rows that are off screen.
 */
@Composable
fun DashboardScreen(
    windowSizeClass: WindowSizeClass,
    scaffoldPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val margin = windowSizeClass.horizontalMargin()
    var selectedDish by remember { mutableStateOf<LoggedMealEntity?>(null) }
    // Grouped as the day was eaten, not as a flat log of rows.
    val mealsByType = remember(state.meals) {
        state.meals.groupBy { it.mealType }.toSortedMap(compareBy { it.ordinal })
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Bar insets belong here, not on a parent: the list scrolls under the
            // bars while its first and last items stay clear of them.
            contentPadding = screenContentPadding(scaffoldPadding, margin),
            verticalArrangement = Arrangement.spacedBy(Fm.section),
        ) {
            item("plate") {
                PlateCard(state, Modifier.widthIn(max = MaxContentWidth).enterFromBelow(0))
            }

            item("meals-header") { SectionHeader("Meals") }
            when {
                state.loading -> item("loading") { FmSkeletonCard(Modifier.fillMaxWidth()) }
                !state.hasAnythingLogged -> item("empty") {
                    // The first-run state IS the product on day one. It gets a real
                    // design and a next action, not an empty list.
                    FmEmptyState(
                        icon = Icons.Outlined.RamenDining,
                        title = "Nothing on the plate yet",
                        body = "Tap + to snap or log what you ate. Chef keeps the dishes " +
                            "you eat often one tap away.",
                    )
                }
                else -> mealsByType.entries.forEachIndexed { index, (type, dishes) ->
                    item("meal-${type.name}") {
                        MealGroup(
                            type = type,
                            dishes = dishes,
                            onDish = { selectedDish = it },
                            modifier = Modifier
                                .widthIn(max = MaxContentWidth)
                                .enterFromBelow(index + 1),
                        )
                    }
                }
            }

            if (state.recentWorkouts.isNotEmpty()) {
                item("workout-header") { SectionHeader("Recent training") }
                item("workouts") {
                    WorkoutCard(
                        workouts = state.recentWorkouts,
                        modifier = Modifier
                            .widthIn(max = MaxContentWidth)
                            .enterFromBelow(mealsByType.size + 1),
                    )
                }
            }

            item("progress-header") { SectionHeader("Progress") }
            item("progress") {
                ProgressCard(state, Modifier.widthIn(max = MaxContentWidth))
            }
        }

        selectedDish?.let { meal ->
            DishDetailDialog(
                meal = meal,
                onDismiss = { selectedDish = null },
                onDelete = {
                    viewModel.deleteMeal(meal.id)
                    selectedDish = null
                },
            )
        }
    }
}

/**
 * The day on one plate: what Today has to say, the thali, and the numbers the
 * thali stands for. Replaces a big-number-plus-three-rings card that could have
 * belonged to any tracker.
 */
@Composable
private fun PlateCard(state: DashboardUiState, modifier: Modifier = Modifier) {
    val target = state.targets
    val fm = MaterialTheme.fm
    val line = TodayVoice.line(
        hour = LocalTime.now().hour,
        mealsLogged = state.meals.mapTo(mutableSetOf()) { it.mealType },
        eatenKcal = state.eatenKcal,
        targetKcal = target?.calories,
        proteinG = state.proteinG,
        proteinTargetG = target?.proteinG,
        streakDays = state.progress?.currentStreak ?: 0,
    )
    val katoris = listOf(
        Katori(state.proteinG.toFloat(), (target?.proteinG ?: 0).toFloat(), fm.macroProtein, "Protein"),
        Katori(state.carbsG.toFloat(), (target?.carbsG ?: 0).toFloat(), fm.macroCarbs, "Carbs"),
        Katori(state.fatG.toFloat(), (target?.fatG ?: 0).toFloat(), fm.macroFat, "Fat"),
    )

    FmCard(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Fm.gap)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Fm.snug),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = line,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            state.progress?.let {
                StreakBadge(days = it.currentStreak, atRisk = it.streakAtRisk)
            }
        }

        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Fm.snug),
        ) {
            FmThali(
                kcal = state.eatenKcal.toFloat(),
                kcalTarget = (target?.calories ?: 0).toFloat(),
                katoris = katoris,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text("%,d".format(animatedCount(state.eatenKcal)), style = numeric(32))
                Text(
                    "  of ${"%,d".format(target?.calories ?: 0)} kcal",
                    style = MaterialTheme.typography.labelSmall,
                    color = fm.textSecondary,
                    modifier = Modifier.padding(bottom = Fm.hair),
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            katoris.forEach { FmKatoriLegend(it) }
        }

        if (state.maintenanceKcal != null || state.weeklyRateKg != null) {
            FmDivider(insetStart = 0.dp)
            FmStatRow {
                state.maintenanceKcal?.let { maintenance ->
                    FmStat(
                        "%,d".format(state.eatenKcal - maintenance),
                        "vs maintenance",
                        tint = fm.progress,
                    )
                }
                state.weeklyRateKg?.let { FmStat("%.1f".format(it), "kg / week") }
            }
        }
    }
}

@Composable
private fun ProgressCard(state: DashboardUiState, modifier: Modifier = Modifier) {
    FmCard(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Fm.gutter)) {
        LevelBar(totalXp = state.progress?.totalXp ?: 0)
        Text(
            text = if (state.planNeedsSetup) {
                "Set up your cut on the Plan tab and targets start adapting to " +
                    "what the scale actually does."
            } else {
                "Targets are adapting to your weight trend."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.fm.textSecondary,
        )
    }
}

/** The recent sessions in one card, rows divided, rather than a card each. */
@Composable
private fun WorkoutCard(workouts: List<SessionSummary>, modifier: Modifier = Modifier) {
    val today = DateKeys.today()
    FmCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = Fm.hair),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        workouts.forEachIndexed { index, workout ->
            if (index > 0) FmDivider()
            FmListItem(
                headline = workout.name,
                supporting = "${workout.setCount} sets · " +
                    "${"%,d".format(workout.totalVolumeKg.toInt())} kg moved",
                leading = {
                    FmListIcon(Icons.Outlined.FitnessCenter, tint = MaterialTheme.fm.progress)
                },
                trailing = {
                    Text(
                        TodayVoice.dayName(workout.dayEpoch, today).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.fm.textSecondary,
                    )
                },
            )
        }
    }
}

/**
 * One Meal: its dishes in a single card under the meal's own name and total.
 * A card per dish turned three rotis and a dal into four unrelated slabs.
 */
@Composable
private fun MealGroup(
    type: MealType,
    dishes: List<LoggedMealEntity>,
    onDish: (LoggedMealEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = dishes.sumOf { it.macros.calories }.toInt()
    FmCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = Fm.gutter, bottom = Fm.hair),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Fm.gutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Fm.tight),
        ) {
            Icon(
                imageVector = iconFor(type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = type.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            FmListValue("%,d".format(total), "kcal")
        }
        dishes.forEachIndexed { index, meal ->
            if (index > 0) FmDivider()
            FmListItem(
                headline = meal.name,
                supporting = "${meal.portionQuantity.trimZeros()} " +
                    meal.portionUnit.name.lowercase(),
                trailing = { FmListValue("${meal.macros.calories.toInt()}", "kcal") },
                onClick = { onDish(meal) },
            )
            if (meal.isAiEstimate) {
                EstimateBadge(
                    confidence = meal.estimateConfidence,
                    modifier = Modifier.padding(start = Fm.gutter, bottom = Fm.tight),
                )
            }
        }
    }
}

private fun iconFor(type: MealType): ImageVector = when (type) {
    MealType.BREAKFAST -> Icons.Outlined.WbTwilight
    MealType.LUNCH -> Icons.Outlined.WbSunny
    MealType.DINNER -> Icons.Outlined.NightsStay
    MealType.SNACK -> Icons.Outlined.Cookie
}

@Composable
private fun DishDetailDialog(
    meal: LoggedMealEntity,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = meal.name,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = meal.mealType.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.fm.textSecondary,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Fm.snug)) {
                Text(
                    text = "${meal.portionQuantity.trimZeros()} ${meal.portionUnit.name.lowercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FmStatRow {
                    FmStat("${meal.macros.calories.toInt()}", "kcal")
                    FmStat("${meal.macros.proteinG.toInt()}g", "protein")
                    FmStat("${meal.macros.carbsG.toInt()}g", "carbs")
                    FmStat("${meal.macros.fatG.toInt()}g", "fat")
                }
                if (meal.isAiEstimate) {
                    EstimateBadge(
                        confidence = meal.estimateConfidence,
                        modifier = Modifier.padding(top = Fm.tight),
                    )
                }
            }
        },
        // Deleting history is destructive, not this dialog's primary action, so it
        // gets an error-coloured text button rather than the tactile FmButton.
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text("Delete dish", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

/** 2.0 renders as "2", 1.5 stays "1.5". Trailing zeros on a portion look wrong. */
private fun Double.trimZeros(): String =
    if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this)
