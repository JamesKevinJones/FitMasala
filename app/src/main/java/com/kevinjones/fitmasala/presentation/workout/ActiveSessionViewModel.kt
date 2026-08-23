package com.kevinjones.fitmasala.presentation.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevinjones.fitmasala.data.local.dao.SessionDao
import com.kevinjones.fitmasala.data.local.dao.WorkoutDao
import com.kevinjones.fitmasala.data.local.entity.ExerciseEntity
import com.kevinjones.fitmasala.data.local.entity.ExerciseSetEntity
import com.kevinjones.fitmasala.data.local.relation.SessionWithSets
import com.kevinjones.fitmasala.data.local.relation.SetWithExercise
import com.kevinjones.fitmasala.data.prefs.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class ActiveSessionUiState(
    val session: SessionWithSets? = null,
    val setsWithExercise: List<SetWithExercise> = emptyList(),
    val exercises: List<ExerciseEntity> = emptyList(),
    val loading: Boolean = true,
    val restSeconds: Int = 0,
    val isResting: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    private val sessions: SessionDao,
    private val workouts: WorkoutDao,
    private val settingsStore: SettingsStore
) : ViewModel() {

    private val _restSeconds = MutableStateFlow(0)
    private val _isResting = MutableStateFlow(false)
    private var restJob: Job? = null

    val state: StateFlow<ActiveSessionUiState> = sessions.observeActiveSession()
        .flatMapLatest { session ->
            if (session == null) flowOf(ActiveSessionUiState(loading = false))
            else {
                combine(
                    sessions.observeSessionWithSets(session.id),
                    sessions.observeSetsWithExercise(session.id),
                    workouts.observeAllExercises(),
                    _restSeconds,
                    _isResting
                ) { sessionWithSets, setsWithExercise, exercises, rest, resting ->
                    ActiveSessionUiState(
                        session = sessionWithSets,
                        setsWithExercise = setsWithExercise,
                        exercises = exercises,
                        loading = false,
                        restSeconds = rest,
                        isResting = resting
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveSessionUiState())

    fun addSet(exerciseId: Long, weight: Double, reps: Int, isWarmup: Boolean) = viewModelScope.launch {
        val sessionId = state.value.session?.session?.id ?: return@launch
        val index = sessions.lastSetIndex(sessionId, exerciseId) + 1
        sessions.insertSet(
            ExerciseSetEntity(
                sessionId = sessionId,
                exerciseId = exerciseId,
                setIndex = index,
                reps = reps,
                weightKg = weight,
                isWarmup = isWarmup
            )
        )
        if (!isWarmup) startRestTimer()
    }

    private fun startRestTimer() {
        restJob?.cancel()
        restJob = viewModelScope.launch {
            val defaultRest = settingsStore.current().defaultRestSeconds
            _restSeconds.value = defaultRest
            _isResting.value = true
            while (_restSeconds.value > 0) {
                delay(1000)
                _restSeconds.value -= 1
            }
            _isResting.value = false
        }
    }

    fun finishSession() = viewModelScope.launch {
        val id = state.value.session?.session?.id ?: return@launch
        sessions.finishSession(id, System.currentTimeMillis())
    }

    fun deleteSession() = viewModelScope.launch {
        val session = state.value.session?.session ?: return@launch
        sessions.deleteSession(session)
    }

    fun cancelRest() {
        restJob?.cancel()
        _restSeconds.value = 0
        _isResting.value = false
    }
}
