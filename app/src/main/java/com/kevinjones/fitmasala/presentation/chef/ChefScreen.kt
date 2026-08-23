package com.kevinjones.fitmasala.presentation.chef

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevinjones.fitmasala.core.ui.components.FmButton
import com.kevinjones.fitmasala.core.ui.components.FmCard
import com.kevinjones.fitmasala.core.ui.components.FmEmptyState
import com.kevinjones.fitmasala.core.ui.components.FmQuickAdd
import com.kevinjones.fitmasala.core.ui.components.FmSkeletonCard
import com.kevinjones.fitmasala.core.ui.components.FmStickyLogBar
import com.kevinjones.fitmasala.core.ui.components.SectionHeader
import com.kevinjones.fitmasala.core.ui.components.enterFromBelow
import com.kevinjones.fitmasala.core.ui.theme.Fm
import com.kevinjones.fitmasala.core.ui.theme.fm
import com.kevinjones.fitmasala.data.local.relation.FrequentMeal

/**
 * The Chef tab, now reading and writing real data.
 */
@Composable
fun ChefScreen(
    contentPadding: PaddingValues,
    onAskChef: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChefViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(Fm.snug),
        ) {
            if (state.loading) {
                items(3) { FmSkeletonCard(Modifier.fillMaxWidth()) }
            } else if (state.isEmpty) {
                item("empty") {
                    FmEmptyState(
                        icon = Icons.Outlined.Restaurant,
                        title = "Nothing logged yet",
                        body = "Once you have logged a few meals, the ones you eat " +
                            "often appear here and re-log in a single tap.",
                    )
                }
            } else {
                item("header") {
                    SectionHeader("You eat these often")
                }
                itemsIndexed(state.frequent, key = { _, m -> m.name }) { index, meal ->
                    MealCard(
                        meal = meal,
                        count = state.pending[meal.name] ?: 0,
                        onCountChange = { viewModel.setPortions(meal.name, it) },
                        modifier = Modifier.enterFromBelow(index),
                    )
                }
            }

            item("ask") {
                FmCard(
                    Modifier.fillMaxWidth().padding(top = Fm.gutter),
                    verticalArrangement = Arrangement.spacedBy(Fm.snug),
                ) {
                    Text("Cooking something new?", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tell the chef what is in your dabba and it works out the dish " +
                            "and the macros, oil and ghee included.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.fm.textSecondary,
                    )
                    FmButton("Ask the chef", onClick = onAskChef, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        FmStickyLogBar(
            visible = state.hasPending,
            itemCount = state.pendingCount,
            totalKcal = state.pendingKcal(),
            proteinG = state.pendingProtein(),
            onCommit = viewModel::logPending,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Fm.gutter),
        )
    }
}

/** Totals for what is staged but not yet committed. */
private fun ChefUiState.pendingKcal(): Int =
    frequent.sumOf { m -> (pending[m.name] ?: 0) * perPortionKcal(m) }.toInt()

private fun ChefUiState.pendingProtein(): Int =
    frequent.sumOf { m -> (pending[m.name] ?: 0) * perPortionProtein(m) }.toInt()

private fun perPortionKcal(m: FrequentMeal): Double =
    if (m.avgPortionQuantity > 0) m.avgCalories / m.avgPortionQuantity else m.avgCalories

private fun perPortionProtein(m: FrequentMeal): Double =
    if (m.avgPortionQuantity > 0) m.avgProteinG / m.avgPortionQuantity else m.avgProteinG

@Composable
private fun MealCard(
    meal: FrequentMeal,
    count: Int,
    onCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FmCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Fm.gutter),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.padding(end = Fm.snug)) {
                Text(meal.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${perPortionKcal(meal).toInt()} kcal · logged ${meal.timesLogged}x",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.fm.textSecondary,
                )
            }
            FmQuickAdd(
                count = count,
                onIncrement = { onCountChange(count + 1) },
                onDecrement = { onCountChange((count - 1).coerceAtLeast(0)) },
            )
        }
    }
}
