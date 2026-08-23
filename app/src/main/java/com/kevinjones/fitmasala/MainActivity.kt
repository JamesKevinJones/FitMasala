package com.kevinjones.fitmasala

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevinjones.fitmasala.core.ui.theme.FitMasalaTheme
import com.kevinjones.fitmasala.data.prefs.ThemeMode
import com.kevinjones.fitmasala.presentation.settings.SettingsViewModel
import com.kevinjones.fitmasala.presentation.navigation.FitMasalaApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge before super.onCreate, so the first frame is already
        // drawn behind the system bars rather than jumping once laid out.
        // SystemBarStyle.auto flips the icon polarity with the system theme -
        // light icons on the dark scheme, dark icons on the light one. Pinning
        // it to .dark would leave white icons invisible on a light background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
        )
        // Without this the system paints a translucent scrim behind the
        // navigation bar on 3-button nav, so a 'transparent' bar is not.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)

        setContent {
            // Hoisted to the Activity: the theme has to be known before any
            // screen exists, so this cannot live in the settings route.
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            FitMasalaTheme(darkTheme = darkTheme) {
                FitMasalaApp(
                    // Recomputed on rotation and on unfolding, because it is
                    // derived from the WINDOW rather than the device.
                    windowSizeClass = calculateWindowSizeClass(this),
                    settingsViewModel = settingsViewModel,
                )
            }
        }
    }
}
