package com.kevinjones.fitmasala.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kevinjones.fitmasala.core.ui.components.FmEmptyState
import com.kevinjones.fitmasala.core.ui.theme.Fm

/**
 * Destination not yet built.
 *
 * Rendered as a real empty state rather than a bare "Coming soon" string,
 * because this is what the empty state component has to look like in situ - and
 * checking it here is free, where checking it on a screen that only appears for
 * a brand-new user is not.
 */
@Composable
fun PlaceholderScreen(destination: TopLevelDestination, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(horizontal = Fm.gutter), contentAlignment = Alignment.TopCenter) {
        FmEmptyState(
            icon = Icons.Outlined.Construction,
            title = "${destination.label} is next",
            body = when (destination) {
                TopLevelDestination.CHEF ->
                    "The chat with the AI chef lands here, along with every recipe you've kept."
                TopLevelDestination.TRAIN ->
                    "Routine builder, the live session tracker and the rest timer."
                TopLevelDestination.PLAN ->
                    "Your cut: weight trend, adaptive targets and the projection to 12%."
                TopLevelDestination.DASHBOARD -> "Today's summary."
            },
        )
    }
}
