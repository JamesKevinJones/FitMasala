package com.kevinjones.fitmasala.presentation.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RamenDining
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.AlertDialog
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
import com.kevinjones.fitmasala.core.ui.components.FmMeter
import com.kevinjones.fitmasala.core.ui.components.FmSkeletonCard
import com.kevinjones.fitmasala.core.ui.components.FmStat
import com.kevinjones.fitmasala.core.ui.components.FmStatRow
import com.kevinjones.fitmasala.core.ui.components.GoalMetPill
import com.kevinjones.fitmasala.core.ui.components.LevelBar
import com.kevinjones.fitmasala.core.ui.components.MacroRing
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
import com.kevinjones.fitmasala.data.local.entity.LoggedMealEntity
import com.kevinjones.fitmasala.data.local.relation.SessionSummary

/**
 * The Synergy Dashboard, reading real data.
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

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Bar insets belong here, not on a parent: the list scrolls under the
            // bars while its first and last items stay clear of them.
            contentPadding = screenContentPadding(scaffoldPadding, margin),
            verticalArrangement = Arrangement.spacedBy(Fm.section),
        ) {
            item("energy") {
                EnergyCard(state, Modifier.widthIn(max = MaxContentWidth).enterFromBelow(0))
            }

            item("progress-header") { SectionHeader("Progress", action = "History", onAction = {}) }
            item("progress") {
                ProgressCard(state, Modifier.widthIn(max = MaxContentWidth).enterFromBelow(1))
            }

            if (state.recentWorkouts.isNotEmpty()) {
                item("workout-header") { SectionHeader("Recent Workouts") }
                itemsIndexed(state.recentWorkouts) { index, workout ->
                    WorkoutRow(workout, Modifier.enterFromBelow(index + 2))
                }
            }

            item("meals-header") { SectionHeader("Eaten today") }

            when {
                state.loading -> item("loading") { FmSkeletonCard(Modifier.fillMaxWidth()) }
                !state.hasAnythingLogged -> item("empty") {
                    // The first-run state IS the product on day one. It gets a real
                    // design and a next action, not an empty list.
                    FmEmptyState(
                        icon = Icons.Outlined.RamenDining,
                        title = "Nothing logged today",
                        body = "Use the button below, or open Chef to re-log something " +
                            "you eat often.",
                    )
                }
                else -> itemsIndexed(state.meals, key = { _, m -> m.id }) { index, meal ->
                    MealRow(
                        meal = meal,
                        onClick = { selectedDish = meal },
                        modifier = Modifier.enterFromBelow(index + 2),
                    )
                }
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

@Composable
private fun EnergyCard(state: DashboardUiState, modifier: Modifier = Modifier) {
    val target = state.targets
    FmCard(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Fm.gap)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    "Eaten",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.fm.textSecondary,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("%,d".format(animatedCount(state.eatenKcal)), style = numeric(36))
                    Text(
                        "  / ${"%,d".format(target?.calories ?: 0)} kcal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.fm.textSecondary,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
            }
            state.progress?.let {
                StreakBadge(days = it.currentStreak, atRisk = it.streakAtRisk)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MacroRing(
                state.proteinG.toFloat(),
                (target?.proteinG ?: 1).toFloat(),
                MaterialTheme.fm.macroProtein,
                label = "Protein",
            )
            MacroRing(
                state.carbsG.toFloat(),
                (target?.carbsG ?: 1).toFloat(),
                MaterialTheme.fm.macroCarbs,
                label = "Carbs",
            )
            MacroRing(
                state.fatG.toFloat(),
                (target?.fatG ?: 1).toFloat(),
                MaterialTheme.fm.macroFat,
                label = "Fat",
            )
        }

        FmStatRow {
            FmStat("${state.remainingKcal}", "left today")
            state.maintenanceKcal?.let { maintenance ->
                FmStat(
                    "${state.eatenKcal - maintenance}",
                    "vs maintenance",
                    tint = MaterialTheme.fm.progress,
                )
            }
            state.weeklyRateKg?.let { FmStat("%.1f".format(it), "kg / week") }
        }

        // Only claimed once actually true. A goal pill shown optimistically is
        // worse than none - it teaches the user to ignore it.
        if (target != null && state.proteinG >= target.proteinG) {
            Row(horizontalArrangement = Arrangement.spacedBy(Fm.tight)) {
                GoalMetPill("Protein hit")
            }
        }
    }
}

@Composable
private fun ProgressCard(state: DashboardUiState, modifier: Modifier = Modifier) {
    FmCard(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Fm.gutter)) {
        LevelBar(totalXp = state.progress?.totalXp ?: 0)
        FmDivider(insetStart = 0.dp)
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
        state.progress?.let { FmMeter(it.levelProgress, 1f, MaterialTheme.fm.progress) }
    }
}

@Composable
private fun WorkoutRow(workout: SessionSummary, modifier: Modifier = Modifier) {
    FmCard(
        modifier = modifier.fillMaxWidth().widthIn(max = MaxContentWidth),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(workout.name, style = MaterialTheme.typography.titleMedium)
            Text("Session Logged", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fm.textSecondary)
        }
    }
}

@Composable
private fun MealRow(
    meal: LoggedMealEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FmCard(
        modifier = modifier.fillMaxWidth().widthIn(max = MaxContentWidth),
        contentPadding = PaddingValues(vertical = Fm.hair),
        onClick = onClick,
    ) {
        FmListItem(
            overline = meal.mealType.name.lowercase().replaceFirstChar { it.uppercase() },
            headline = meal.name,
            supporting = "${meal.portionQuantity.trimZeros()} " +
                meal.portionUnit.name.lowercase(),
            leading = {
                FmListIcon(icon = Icons.Outlined.Restaurant, tint = MaterialTheme.fm.macroProtein)
            },
            trailing = { FmListValue("${meal.macros.calories.toInt()}", "kcal") },
        )
        if (meal.isAiEstimate) {
            EstimateBadge(
                confidence = meal.estimateConfidence,
                modifier = Modifier.padding(start = Fm.gutter, bottom = Fm.tight),
            )
        }
    }
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
                    text = meal.mealType.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.fm.textSecondary,
                )
                Text(
                    text = meal.name,
                    style = MaterialTheme.typography.titleLarge,
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

