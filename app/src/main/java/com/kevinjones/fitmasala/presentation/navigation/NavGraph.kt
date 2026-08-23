package com.kevinjones.fitmasala.presentation.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.kevinjones.fitmasala.core.ui.theme.horizontalMargin
import com.kevinjones.fitmasala.core.ui.theme.screenContentPadding
import com.kevinjones.fitmasala.presentation.chef.AiChefScreen
import com.kevinjones.fitmasala.presentation.chef.ChefScreen
import com.kevinjones.fitmasala.presentation.dashboard.DashboardScreen
import com.kevinjones.fitmasala.presentation.plan.PlanScreen
import com.kevinjones.fitmasala.presentation.settings.SettingsScreen
import com.kevinjones.fitmasala.presentation.settings.SettingsViewModel
import com.kevinjones.fitmasala.presentation.workout.ActiveSessionScreen
import com.kevinjones.fitmasala.presentation.workout.TrainScreen

@Composable
fun FitMasalaNavGraph(
    navController: NavHostController,
    windowSizeClass: WindowSizeClass,
    innerPadding: PaddingValues,
    settingsViewModel: SettingsViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(windowSizeClass, innerPadding)
        }
        composable(Screen.Chef.route) {
            ChefScreen(
                contentPadding = innerPadding,
                onAskChef = { navController.navigate(Routes.AI_CHEF) }
            )
        }
        composable(Routes.AI_CHEF) {
            AiChefScreen(contentPadding = innerPadding)
        }
        composable(Screen.Workout.route) {
            TrainScreen(
                contentPadding = innerPadding,
                onActiveSession = { id -> 
                    navController.navigate(Routes.activeSession(id)) {
                        popUpTo(Screen.Workout.route) { inclusive = false }
                    }
                }
            )
        }
        composable(Routes.ACTIVE_SESSION) {
            ActiveSessionScreen(
                contentPadding = innerPadding,
                onFinish = { navController.popBackStack() }
            )
        }
        composable(Screen.Plan.route) {
            PlanScreen(
                contentPadding = screenContentPadding(
                    innerPadding,
                    windowSizeClass.horizontalMargin(),
                )
            )
        }
        composable(Screen.Settings.route) {
            val settings by settingsViewModel.settings.collectAsState()
            SettingsScreen(
                settings = settings,
                onThemeMode = settingsViewModel::setThemeMode,
                onApiKey = settingsViewModel::setApiKey,
                onClearApiKey = settingsViewModel::clearApiKey,
                onRestSeconds = settingsViewModel::setRestSeconds,
                onRestVibrate = settingsViewModel::setRestVibrate,
                contentPadding = screenContentPadding(
                    innerPadding,
                    windowSizeClass.horizontalMargin(),
                ),
            )
        }
    }
}

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Chef : Screen("chef")
    object Workout : Screen("train")
    object Plan : Screen("plan")
    object Settings : Screen("settings")
}
