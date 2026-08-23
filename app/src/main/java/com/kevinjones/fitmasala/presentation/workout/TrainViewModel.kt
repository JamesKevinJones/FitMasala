package com.kevinjones.fitmasala.presentation.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevinjones.fitmasala.core.util.DateKeys
import com.kevinjones.fitmasala.data.local.dao.SessionDao
import com.kevinjones.fitmasala.data.local.dao.WorkoutDao
import com.kevinjones.fitmasala.data.local.entity.WorkoutRoutineEntity
import com.kevinjones.fitmasala.data.local.entity.WorkoutSessionEntity
import com.kevinjones.fitmasala.data.local.relation.SessionSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrainUiState(
    val routines: List<WorkoutRoutineEntity> = emptyList(),
    val recentSessions: List<SessionSummary> = emptyList(),
    val activeSession: WorkoutSessionEntity? = null,
    val loading: Boolean = true
)

@HiltViewModel
class TrainViewModel @Inject constructor(
    private val sessions: SessionDao,
    private val workouts: WorkoutDao
) : ViewModel() {

    val state: StateFlow<TrainUiState> = combine(
        workouts.observeAllRoutines(),
        sessions.observeRecentSessions(),
        sessions.observeActiveSession()
    ) { routines, recent, active ->
        TrainUiState(
            routines = routines,
            recentSessions = recent,
            activeSession = active,
            loading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainUiState())

    fun startRoutine(routine: WorkoutRoutineEntity) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        sessions.insertSession(
            WorkoutSessionEntity(
                name = routine.name,
                startedAt = now,
                dayEpoch = DateKeys.dayEpochOf(now)
            )
        )
    }

    fun startEmpty() = viewModelScope.launch {
        val now = System.currentTimeMillis()
        sessions.insertSession(
            WorkoutSessionEntity(
                name = "New Workout",
                startedAt = now,
                dayEpoch = DateKeys.dayEpochOf(now)
            )
        )
    }
}
