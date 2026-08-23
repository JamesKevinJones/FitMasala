package com.kevinjones.fitmasala.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kevinjones.fitmasala.data.prefs.AppSettings
import com.kevinjones.fitmasala.data.prefs.SettingsStore
import com.kevinjones.fitmasala.data.prefs.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings state.
 *
 * Hoisted to the Activity rather than owned by the settings screen, because the
 * theme has to be read before any screen exists. A ViewModel scoped to the
 * settings route would mean the app could not know its own theme until the user
 * navigated there.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = store.settings.stateIn(
        scope = viewModelScope,
        // Keeps the flow alive across a rotation instead of restarting it, which
        // would flash the default theme for a frame on every configuration change.
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { store.setThemeMode(mode) }

    fun setApiKey(key: String) = viewModelScope.launch { store.setApiKey(key) }

    fun clearApiKey() = viewModelScope.launch { store.clearApiKey() }

    fun setModelId(id: String) = viewModelScope.launch { store.setModelId(id) }

    fun setMacroTargets(calories: Int, proteinG: Int, carbsG: Int, fatG: Int) =
        viewModelScope.launch { store.setMacroTargets(calories, proteinG, carbsG, fatG) }

    fun setRestSeconds(seconds: Int) =
        viewModelScope.launch { store.setDefaultRestSeconds(seconds) }

    fun setRestVibrate(enabled: Boolean) =
        viewModelScope.launch { store.setRestTimerVibrate(enabled) }
}
