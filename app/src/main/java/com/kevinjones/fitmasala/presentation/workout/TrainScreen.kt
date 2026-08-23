package com.kevinjones.fitmasala.presentation.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevinjones.fitmasala.core.ui.components.FmButton
import com.kevinjones.fitmasala.core.ui.components.FmCard
import com.kevinjones.fitmasala.core.ui.components.FmEmptyState
import com.kevinjones.fitmasala.core.ui.components.FmSkeletonCard
import com.kevinjones.fitmasala.core.ui.components.SectionHeader
import com.kevinjones.fitmasala.core.ui.components.enterFromBelow
import com.kevinjones.fitmasala.core.ui.theme.Fm
import com.kevinjones.fitmasala.core.ui.theme.fm
import com.kevinjones.fitmasala.data.local.relation.SessionSummary
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.ZoneId

@Composable
fun TrainScreen(
    contentPadding: PaddingValues,
    onActiveSession: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrainViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // If a session is already running, the primary action is to return to it.
    val active = state.activeSession
    if (active != null) {
        LaunchedEffect(active.id) {
            onActiveSession(active.id)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Fm.snug),
    ) {
        if (state.loading) {
            items(3) { FmSkeletonCard(Modifier.fillMaxWidth()) }
        } else {
            item {
                SectionHeader("Ready to sweat?")
            }
            item {
                FmCard(Modifier.fillMaxWidth().padding(horizontal = Fm.gutter)) {
                    Text("Routine selection", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Pick a routine or start an empty session to track your progress.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.fm.textSecondary
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = Fm.gutter),
                        horizontalArrangement = Arrangement.spacedBy(Fm.gutter)
                    ) {
                        FmButton("New Workout", onClick = viewModel::startEmpty, modifier = Modifier.weight(1f))
                    }
                }
            }

            if (state.recentSessions.isEmpty()) {
                item {
                    FmEmptyState(
                        icon = Icons.Outlined.History,
                        title = "No sessions logged",
                        body = "Your training history will appear here once you finish your first session."
                    )
                }
            } else {
                item {
                    SectionHeader("Recent history")
                }
                items(state.recentSessions, key = { it.sessionId }) { session ->
                    SessionCard(session)
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: SessionSummary) {
    val date = Instant.ofEpochMilli(session.startedAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEE, MMM dd"))

    FmCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Fm.gutter),
        contentPadding = PaddingValues(Fm.gutter)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(session.name, style = MaterialTheme.typography.titleMedium)
                Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fm.textSecondary)
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text("${session.setCount} sets", style = MaterialTheme.typography.labelSmall)
                Text("${session.totalVolumeKg.toInt()} kg volume", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
