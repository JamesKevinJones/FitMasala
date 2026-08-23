package com.kevinjones.fitmasala.presentation.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevinjones.fitmasala.core.ui.components.FmButton
import com.kevinjones.fitmasala.core.ui.components.FmCard
import com.kevinjones.fitmasala.core.ui.components.FmTextField
import com.kevinjones.fitmasala.core.ui.theme.Fm
import com.kevinjones.fitmasala.core.ui.theme.fm
import com.kevinjones.fitmasala.data.local.entity.ExerciseEntity
import com.kevinjones.fitmasala.data.local.relation.SetWithExercise

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(
    contentPadding: PaddingValues,
    onFinish: () -> Unit,
    viewModel: ActiveSessionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddExercise by remember { mutableStateOf(false) }

    if (state.session == null && !state.loading) {
        onFinish()
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddExercise = true }) {
                Icon(Icons.Default.Add, "Add exercise")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.isResting) {
                RestTimer(state.restSeconds, viewModel::cancelRest)
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(Fm.gutter),
                verticalArrangement = Arrangement.spacedBy(Fm.snug)
            ) {
                val grouped = state.setsWithExercise.groupBy { it.exercise.id }
                grouped.forEach { (exerciseId, sets) ->
                    item(key = exerciseId) {
                        ExerciseSection(sets[0].exercise, sets, viewModel::addSet)
                    }
                }
            }

            FmButton(
                "Finish Workout",
                onClick = {
                    viewModel.finishSession()
                    onFinish()
                },
                modifier = Modifier.fillMaxWidth().padding(Fm.gutter)
            )
        }
    }

    if (showAddExercise) {
        AddExerciseDialog(
            exercises = state.exercises,
            onDismiss = { showAddExercise = false },
            onSelect = { exercise ->
                // Adding an empty set to start the exercise
                viewModel.addSet(exercise.id, 0.0, 0, false)
                showAddExercise = false
            }
        )
    }
}

@Composable
private fun RestTimer(seconds: Int, onCancel: () -> Unit) {
    FmCard(
        modifier = Modifier.fillMaxWidth().padding(Fm.gutter),
        container = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            Modifier.fillMaxWidth().padding(Fm.snug),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Rest: $seconds s", style = MaterialTheme.typography.titleMedium)
            FmButton("Skip", onClick = onCancel)
        }
    }
}

@Composable
private fun ExerciseSection(
    exercise: ExerciseEntity,
    sets: List<SetWithExercise>,
    onAddSet: (Long, Double, Int, Boolean) -> Unit
) {
    var weightInput by remember { mutableStateOf("") }
    var repsInput by remember { mutableStateOf("") }

    FmCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Fm.gutter)
    ) {
        Text(exercise.name, style = MaterialTheme.typography.titleMedium)
        
        sets.filter { it.set.reps > 0 }.forEach { set ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Set ${set.set.setIndex}", style = MaterialTheme.typography.bodySmall)
                Text("${set.set.weightKg} kg x ${set.set.reps}", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = Fm.snug),
            horizontalArrangement = Arrangement.spacedBy(Fm.snug),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FmTextField(
                value = weightInput,
                onValueChange = { weightInput = it },
                modifier = Modifier.weight(1f),
                placeholder = "kg"
            )
            FmTextField(
                value = repsInput,
                onValueChange = { repsInput = it },
                modifier = Modifier.weight(1f),
                placeholder = "reps"
            )
            IconButton(onClick = {
                val weight = weightInput.toDoubleOrNull() ?: 0.0
                val reps = repsInput.toIntOrNull() ?: 0
                if (reps > 0) {
                    onAddSet(exercise.id, weight, reps, false)
                    weightInput = ""
                    repsInput = ""
                }
            }) {
                Icon(Icons.Default.Check, "Log set")
            }
        }
    }
}

@Composable
private fun AddExerciseDialog(
    exercises: List<ExerciseEntity>,
    onDismiss: () -> Unit,
    onSelect: (ExerciseEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Exercise") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(exercises) { exercise ->
                    TextButton(
                        onClick = { onSelect(exercise) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(exercise.name, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
