package com.kevinjones.fitmasala.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Top-level destinations.
 *
 * Four, deliberately. M3 allows three to five in a navigation bar; past five the
 * labels truncate and the targets fall under 48dp. Settings is NOT one of them -
 * it is reached from the app bar, because a destination visited twice a year
 * should not hold a permanent quarter of the bottom bar.
 *
 * Each carries an outlined and a filled icon. The fill is the selected state:
 * colour alone would fail for a user who cannot distinguish saffron from grey,
 * so selection is signalled by shape as well as colour.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    /** Read aloud instead of the label where the label alone is ambiguous. */
    val contentDescription: String,
) {
    DASHBOARD(
        route = "dashboard",
        label = "Today",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        contentDescription = "Today's dashboard",
    ),
    CHEF(
        route = "chef",
        label = "Chef",
        selectedIcon = Icons.Filled.Restaurant,
        unselectedIcon = Icons.Outlined.Restaurant,
        contentDescription = "AI chef and recipes",
    ),
    TRAIN(
        route = "train",
        label = "Train",
        selectedIcon = Icons.Filled.FitnessCenter,
        unselectedIcon = Icons.Outlined.FitnessCenter,
        contentDescription = "Workouts",
    ),
    PLAN(
        route = "plan",
        label = "Plan",
        selectedIcon = Icons.Filled.CameraAlt,
        unselectedIcon = Icons.Outlined.CameraAlt,
        contentDescription = "Cut plan and progress",
    ),
}

/** Destinations reached from the app bar or a row, never the navigation bar. */
object Routes {
    const val SETTINGS = "settings"
    const val AI_CHEF = "ai_chef"
    const val RECIPE_DETAIL = "recipe/{recipeId}"
    const val ACTIVE_SESSION = "session/{sessionId}"
    const val PHOTO_CAPTURE = "capture"

    fun recipeDetail(id: Long) = "recipe/$id"
    fun activeSession(id: Long) = "session/$id"
}

val SettingsIcon = Icons.Filled.Settings
val SettingsIconOutlined = Icons.Outlined.Settings
