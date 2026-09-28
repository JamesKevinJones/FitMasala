package com.kevinjones.fitmasala.presentation.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevinjones.fitmasala.domain.plan.BodyFatSource
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

    fun logWeighIn(weighIn: WeighIn) = viewModelScope.launch {
        plans.logWeight(weighIn.weightKg, weighIn.waistCm, weighIn.neckCm, weighIn.hipCm)
    }

    fun updateCut(edit: CutEdit) = viewModelScope.launch {
        plans.updateCut(
            ageYears = edit.ageYears,
            activityLevel = edit.activity.name,
            aggression = edit.aggression.name,
            goalBodyFatPercent = edit.goalBodyFatPercent,
        )
    }

    fun startCut(setup: CutSetup) = viewModelScope.launch {
        plans.startCut(
            sex = setup.sex.name,
            heightCm = setup.heightCm,
            ageYears = setup.ageYears,
            activityLevel = setup.activity.name,
            aggression = setup.aggression.name,
            startWeightKg = setup.weightKg,
            startBodyFatPercent = setup.bodyFatPercent,
            goalBodyFatPercent = setup.goalBodyFatPercent,
            // Only a tape-derived start seeds the tape. A typed estimate wins in
            // the form, and a seeded tape would override it on the first reading.
            tape = setup.tape.takeIf { setup.bodyFatSource == BodyFatSource.NAVY_TAPE },
        )
    }
}
