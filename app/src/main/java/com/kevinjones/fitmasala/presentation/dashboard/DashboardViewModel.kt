package com.kevinjones.fitmasala.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevinjones.fitmasala.data.local.entity.LoggedMealEntity
import com.kevinjones.fitmasala.data.prefs.AppSettings
import com.kevinjones.fitmasala.data.prefs.SettingsStore
import com.kevinjones.fitmasala.data.local.dao.SessionDao
import com.kevinjones.fitmasala.data.local.relation.DailyMacroTotals
import com.kevinjones.fitmasala.data.local.relation.SessionSummary
import com.kevinjones.fitmasala.domain.plan.MacroTarget
import com.kevinjones.fitmasala.domain.repository.MealRepository
import com.kevinjones.fitmasala.domain.repository.PlanRepository
import com.kevinjones.fitmasala.domain.repository.PlanSnapshot
import com.kevinjones.fitmasala.domain.repository.ProgressRepository
import com.kevinjones.fitmasala.domain.repository.ProgressSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Dashboard state.
 *
 * `targets` comes from the PLAN when a cut is active, and falls back to the
 * manual numbers in Settings otherwise. That precedence matters: an adaptive
 * target the user never set should still win over a default they never changed,
 * because it is derived from their actual data.
 */
data class DashboardUiState(
    val loading: Boolean = true,
    val meals: List<LoggedMealEntity> = emptyList(),
    val eatenKcal: Int = 0,
    val proteinG: Int = 0,
    val carbsG: Int = 0,
    val fatG: Int = 0,
    val targets: MacroTarget? = null,
    val progress: ProgressSnapshot? = null,
    val maintenanceKcal: Int? = null,
    val weeklyRateKg: Double? = null,
    val planNeedsSetup: Boolean = true,
    val recentWorkouts: List<SessionSummary> = emptyList()
) {
    val remainingKcal: Int get() = ((targets?.calories ?: 0) - eatenKcal)
    val hasAnythingLogged: Boolean get() = meals.isNotEmpty()
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val meals: MealRepository,
    private val sessionDao: SessionDao,
    plans: PlanRepository,
    progress: ProgressRepository,
    settings: SettingsStore,
) : ViewModel() {

    val state: StateFlow<DashboardUiState> = combine(
        meals.observeToday(),
        meals.observeTodayTotals(),
        plans.observePlan(),
        progress.observeProgress(),
        settings.settings,
        sessionDao.observeRecentSessions()
    ) { flows ->
        val today = flows[0] as List<LoggedMealEntity>
        val totals = flows[1] as DailyMacroTotals
        val plan = flows[2] as PlanSnapshot
        val prog = flows[3] as ProgressSnapshot
        val prefs = flows[4] as AppSettings
        val workouts = flows[5] as List<SessionSummary>

        DashboardUiState(
            loading = false,
            meals = today,
            eatenKcal = totals.calories.toInt(),
            proteinG = totals.proteinG.toInt(),
            carbsG = totals.carbsG.toInt(),
            fatG = totals.fatG.toInt(),
            targets = plan.projection?.dailyTarget ?: manualTargets(prefs),
            progress = prog,
            maintenanceKcal = plan.projection?.maintenanceCalories,
            weeklyRateKg = plan.projection?.weeklyRateKg,
            planNeedsSetup = plan.needsSetup,
            recentWorkouts = workouts.take(3)
        )
    }.stateIn(
        scope = viewModelScope,
        // Survives a rotation without restarting every query underneath.
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )

    fun deleteMeal(id: Long) = viewModelScope.launch { meals.delete(id) }

    private fun manualTargets(prefs: AppSettings) = MacroTarget(
        calories = prefs.dailyCalorieTarget,
        proteinG = prefs.proteinTargetG,
        carbsG = prefs.carbTargetG,
        fatG = prefs.fatTargetG,
    )
}
