package com.kevinjones.fitmasala.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.kevinjones.fitmasala.core.ui.components.FabAction
import com.kevinjones.fitmasala.core.ui.components.FmBarAction
import com.kevinjones.fitmasala.core.ui.components.FmExpandableFab
import com.kevinjones.fitmasala.core.ui.components.FmLargeTopBar
import com.kevinjones.fitmasala.core.ui.components.FmNavigationBar
import com.kevinjones.fitmasala.core.ui.components.FmNavigationRail
import com.kevinjones.fitmasala.core.ui.components.rememberCollapsingBarBehavior
import com.kevinjones.fitmasala.core.ui.theme.horizontalMargin
import com.kevinjones.fitmasala.core.ui.theme.screenContentPadding
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevinjones.fitmasala.core.util.DateKeys
import com.kevinjones.fitmasala.presentation.plan.PlanViewModel
import com.kevinjones.fitmasala.presentation.plan.cutWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.kevinjones.fitmasala.presentation.chef.AiChefScreen
import com.kevinjones.fitmasala.presentation.dashboard.DashboardScreen
import com.kevinjones.fitmasala.presentation.plan.PlanScreen
import com.kevinjones.fitmasala.presentation.settings.SettingsScreen
import com.kevinjones.fitmasala.presentation.settings.SettingsViewModel


/**
 * The application shell: app bar, navigation, FAB, snackbar host.
 *
 * Two structural decisions worth stating.
 *
 * **Navigation adapts to the window.** A compact window gets a bottom
 * navigation bar; anything wider gets a rail. Android's guidance is explicit
 * that a bottom bar on a tablet puts the primary controls as far from the hands
 * as the layout allows. Unfolding a foldable swaps them, because the size class
 * is read from the window, not the device.
 *
 * **Scaffold's padding is handed to the screens, not applied here.** Under
 * edge-to-edge, content is meant to scroll BENEATH the bars; padding this Box
 * would clip every list above the navigation bar and leave a dead strip exactly
 * where the design wants content passing under glass. Each screen folds
 * `innerPadding` into its own `contentPadding` via `screenContentPadding`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitMasalaApp(
    windowSizeClass: WindowSizeClass,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    // Activity-scoped, for the app bar's "week N of the cut" line only.
    planViewModel: PlanViewModel = hiltViewModel(),
) {
    val plan by planViewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Derived, not stored: showSettings must always agree with the actual back
    // stack. It was previously a `var` overwritten unconditionally every
    // recomposition, which raced with the back handler below - pressing back
    // set the flag false for one frame, then this same overwrite snapped it
    // back to true because the nav graph's back stack hadn't actually moved,
    // so back on Settings silently did nothing.
    val showSettings = currentRoute == Routes.SETTINGS
    val snapping = currentRoute == Routes.PHOTO_CAPTURE
    val loggingMeal = currentRoute == Routes.LOG_MEAL
    val scanning = currentRoute == Routes.BARCODE
    val inSession = currentRoute == Routes.ACTIVE_SESSION
    var destination by remember(currentRoute) {
        mutableStateOf(
            TopLevelDestination.entries.find { it.route == currentRoute }
                ?: TopLevelDestination.DASHBOARD
        )
    }
    var fabExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollBehavior = rememberCollapsingBarBehavior()

    val compact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    val destinations = TopLevelDestination.entries

    // Back unwinds transient UI before it unwinds navigation, and only leaves
    // the app once there is nothing left to undo. Handling back is what makes
    // the FAB menu feel like part of Android rather than a floating widget -
    // and the manifest's enableOnBackInvokedCallback lets the system play its
    // predictive back-to-home animation once these handlers decline.
    // Actually pops the back stack, rather than flipping a flag the nav
    // graph disagrees with.
    BackHandler(enabled = showSettings) { navController.popBackStack() }
    BackHandler(enabled = !showSettings && fabExpanded) { fabExpanded = false }
    BackHandler(
        enabled = !showSettings && !fabExpanded &&
            destination != TopLevelDestination.DASHBOARD,
    ) {
        destination = TopLevelDestination.DASHBOARD
    }

    // The same move the navigation bar and rail make, so a FAB shortcut lands
    // on a tab with the same back-stack behaviour as tapping the tab itself.
    fun openTab(target: TopLevelDestination) {
        destination = target
        fabExpanded = false
        navController.navigate(target.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val quickActions = listOf(
        FabAction("Snap a meal", Icons.Filled.CameraAlt) {
            fabExpanded = false
            navController.navigate(Routes.PHOTO_CAPTURE) { launchSingleTop = true }
        },
        // For a label or a dish whose numbers you know - no photo, no estimate.
        FabAction("Log a meal", Icons.Filled.EditNote) {
            fabExpanded = false
            navController.navigate(Routes.LOG_MEAL) { launchSingleTop = true }
        },
        // A packet's label from Open Food Facts - the only non-LLM network call.
        FabAction("Scan a barcode", Icons.Filled.QrCodeScanner) {
            fabExpanded = false
            navController.navigate(Routes.BARCODE) { launchSingleTop = true }
        },
        FabAction("Ask the chef", Icons.Filled.Restaurant) {
            fabExpanded = false
            navController.navigate(Routes.AI_CHEF) { launchSingleTop = true }
        },
        // Train, not a blank session: a workout starts from a routine there,
        // and an empty "New Workout" row would be a session nobody meant to log.
        FabAction("Start a workout", Icons.Filled.FitnessCenter) {
            openTab(TopLevelDestination.TRAIN)
        },
    )

    Row(modifier.fillMaxSize()) {
        if (!compact) {
            FmNavigationRail(
                destinations = destinations,
                current = destination,
                onSelect = { openTab(it) },
                modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
            )
        }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                FmLargeTopBar(
                    title = when {
                        showSettings -> "Settings"
                        snapping -> "Snap a meal"
                        loggingMeal -> "Log a meal"
                        scanning -> "Scan a barcode"
                        else -> titleFor(destination)
                    },
                    overline = if (showSettings || snapping || loggingMeal || scanning) {
                        null
                    } else {
                        overlineFor(destination, cutWeek(plan.startedAtDayEpoch, DateKeys.today()))
                    },
                    scrollBehavior = scrollBehavior,
                    actions = {
                        FmBarAction(
                            icon = SettingsIconOutlined,
                            contentDescription = "Settings",
                            onClick = {
                                if (showSettings) navController.popBackStack()
                                else navController.navigate(Routes.SETTINGS)
                            },
                        )
                    },
                )
            },
            bottomBar = {
                if (compact) {
                    FmNavigationBar(
                        destinations = destinations,
                        current = destination,
                        onSelect = { openTab(it) },
                    )
                }
            },
            floatingActionButton = {
                // No FAB on Settings: a FAB is the screen's primary action, and
                // Settings has none. It was also physically covering a switch.
                // None while snapping or logging either: "Log dish" is that screen's action.
                // None in a workout: adding an exercise and finishing are its actions.
                if (!showSettings && !snapping && !loggingMeal && !scanning && !inSession) {
                    FmExpandableFab(
                        actions = quickActions,
                        expanded = fabExpanded,
                        onExpandedChange = { fabExpanded = it },
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .imePadding(),
            ) {
                FitMasalaNavGraph(
                    navController = navController,
                    windowSizeClass = windowSizeClass,
                    innerPadding = innerPadding,
                    settingsViewModel = settingsViewModel,
                    onMessage = { message ->
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    },
                )
            }
        }
    }
}

private fun titleFor(destination: TopLevelDestination) = when (destination) {
    TopLevelDestination.DASHBOARD -> "Today"
    TopLevelDestination.CHEF -> "Chef"
    TopLevelDestination.TRAIN -> "Train"
    TopLevelDestination.PLAN -> "Plan"
}

private fun overlineFor(destination: TopLevelDestination, week: Int?): String? = when (destination) {
    TopLevelDestination.DASHBOARD -> LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))
    TopLevelDestination.CHEF -> "What's in my dabba"
    TopLevelDestination.TRAIN -> "Push · Pull · Legs"
    TopLevelDestination.PLAN -> week?.let { "Week $it of the cut" }
}
