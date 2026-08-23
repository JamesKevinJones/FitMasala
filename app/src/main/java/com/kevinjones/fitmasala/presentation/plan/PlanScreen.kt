package com.kevinjones.fitmasala.presentation.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevinjones.fitmasala.core.ui.components.FmButton
import com.kevinjones.fitmasala.core.ui.components.FmCard
import com.kevinjones.fitmasala.core.ui.components.FmEmptyState
import com.kevinjones.fitmasala.core.ui.components.FmInsightStrip
import com.kevinjones.fitmasala.core.ui.components.FmStat
import com.kevinjones.fitmasala.core.ui.components.FmStatRow
import com.kevinjones.fitmasala.core.ui.components.FmTrendChart
import com.kevinjones.fitmasala.core.ui.components.SectionHeader
import com.kevinjones.fitmasala.core.ui.components.enterFromBelow
import com.kevinjones.fitmasala.core.ui.theme.Fm
import com.kevinjones.fitmasala.core.ui.theme.fm
import com.kevinjones.fitmasala.core.ui.theme.numeric

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
@Composable
fun PlanScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: PlanViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    val projection = snapshot.projection
    val adjustment = snapshot.adjustment

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Fm.gap),
    ) {
        if (snapshot.needsSetup) {
            item("setup") { SetupCard(viewModel) }
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
                onAction = {
                    // Placeholder until the weigh-in sheet exists. It proves the
                    // write path end to end.
                    viewModel.logWeight(snapshot.trend.lastOrNull()?.weightKg ?: 85.0)
                },
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
private fun SetupCard(viewModel: PlanViewModel) {
    FmEmptyState(
        icon = Icons.Outlined.MonitorWeight,
        title = "No cut set up yet",
        body = "Add your height, weight and a body-fat estimate, and the app works " +
            "out a target that adapts to what the scale actually does.",
        action = {
            // Seeded with realistic starting values until the onboarding form is
            // built. Everything after this point is driven by real weigh-ins.
            FmButton(
                text = "Start a cut",
                onClick = {
                    viewModel.startCut(
                        sex = "MALE",
                        heightCm = 178.0,
                        ageYears = 27,
                        activityLevel = "MODERATE",
                        aggression = "STANDARD",
                        startWeightKg = 85.0,
                        startBodyFatPercent = 22.0,
                    )
                },
            )
        },
    )
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
