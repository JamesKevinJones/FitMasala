package com.kevinjones.fitmasala.presentation.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevinjones.fitmasala.core.ui.theme.Fm
import com.kevinjones.fitmasala.core.ui.theme.MaxContentWidth
import com.kevinjones.fitmasala.core.util.combineDateAndTime
import com.kevinjones.fitmasala.presentation.snap.EatenAtDateDialog
import com.kevinjones.fitmasala.presentation.snap.EatenAtTimeDialog
import com.kevinjones.fitmasala.presentation.snap.ManualDishForm
import java.time.ZoneId

/**
 * "Log a meal" without a photo: a label, a home recipe, a dish you know the
 * numbers of. The same form as Snap a meal's "Log by hand", plus a movable time
 * for the lunch logged at teatime or last night's dinner.
 */
@Composable
fun LogMealScreen(
    contentPadding: PaddingValues,
    onClose: () -> Unit,
    onLogged: (summary: String) -> Unit,
    viewModel: LogMealViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    /** Changing when the meal was eaten: the date dialog, then the time dialog for [pickedDate]. */
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickedDate by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(state.logged) {
        state.logged?.let(onLogged)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Fm.gap),
    ) {
        item {
            ManualDishForm(
                eatenAt = state.eatenAt,
                mealType = state.mealType,
                saving = state.saving,
                saveError = state.saveError,
                title = "What did you eat?",
                intro = "From a label, a recipe or your own judgement. Logged as you type it, " +
                    "not as an estimate.",
                modifier = Modifier.fillMaxWidth().widthIn(max = MaxContentWidth),
                onMealType = viewModel::setMealType,
                onSave = viewModel::save,
                onDiscard = onClose,
                onChangeTime = { pickingDate = true },
            )
        }
    }

    if (pickingDate) {
        EatenAtDateDialog(
            eatenAt = state.eatenAt,
            onPicked = { date ->
                pickingDate = false
                pickedDate = date
            },
            onDismiss = { pickingDate = false },
        )
    }
    val date = pickedDate
    if (date != null) {
        EatenAtTimeDialog(
            eatenAt = state.eatenAt,
            onPicked = { hour, minute ->
                viewModel.setEatenAt(combineDateAndTime(date, hour, minute, ZoneId.systemDefault()))
                pickedDate = null
            },
            onDismiss = { pickedDate = null },
        )
    }
}
