package com.kevinjones.fitmasala.presentation.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevinjones.fitmasala.domain.repository.PlanRepository
import com.kevinjones.fitmasala.domain.repository.PlanSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val plans: PlanRepository,
) : ViewModel() {

    val state: StateFlow<PlanSnapshot> = plans.observePlan()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlanSnapshot(null, null, null, emptyList(), emptyList(), needsSetup = true),
        )

    fun logWeight(kg: Double, waistCm: Double? = null, neckCm: Double? = null) =
        viewModelScope.launch { plans.logWeight(kg, waistCm, neckCm) }

    fun startCut(
        sex: String,
        heightCm: Double,
        ageYears: Int,
        activityLevel: String,
        aggression: String,
        startWeightKg: Double,
        startBodyFatPercent: Double,
        goalBodyFatPercent: Double = 12.0,
    ) = viewModelScope.launch {
        plans.startCut(
            sex = sex,
            heightCm = heightCm,
            ageYears = ageYears,
            activityLevel = activityLevel,
            aggression = aggression,
            startWeightKg = startWeightKg,
            startBodyFatPercent = startBodyFatPercent,
            goalBodyFatPercent = goalBodyFatPercent,
        )
    }
}
