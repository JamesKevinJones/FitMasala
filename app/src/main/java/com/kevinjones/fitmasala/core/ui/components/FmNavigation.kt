package com.kevinjones.fitmasala.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kevinjones.fitmasala.core.ui.theme.FmMotion
import com.kevinjones.fitmasala.presentation.navigation.TopLevelDestination

/**
 * Bottom navigation.
 *
 * Wraps M3's NavigationBar rather than rebuilding it. The component already
 * handles the 80dp height, the 48dp targets, the state layers, the pill
 * indicator and TalkBack's selected-state announcement - reimplementing that by
 * hand is how an app ends up with navigation that looks Android-shaped but
 * behaves wrong.
 *
 * What is added on top: the icon swaps outlined to filled on selection, so
 * selection reads by shape as well as colour, and a small scale pop confirms the
 * tap. 90ms, because this is a per-action moment.
 */
@Composable
fun FmNavigationBar(
    destinations: List<TopLevelDestination>,
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    badges: Map<TopLevelDestination, String> = emptyMap(),
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp, // elevation is carried by the surface colour, not a tint
    ) {
        destinations.forEach { destination ->
            val selected = destination == current
            val pop by animateFloatAsState(
                targetValue = if (selected) 1f else 0.92f,
                animationSpec = tween(FmMotion.Press, easing = FmMotion.EaseOutExpo),
                label = "navIconPop",
            )

            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    NavIcon(
                        destination = destination,
                        selected = selected,
                        scale = pop,
                        badge = badges[destination],
                    )
                },
                label = { Text(destination.label) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.semantics { contentDescription = destination.contentDescription },
            )
        }
    }
}

@Composable
private fun NavIcon(
    destination: TopLevelDestination,
    selected: Boolean,
    scale: Float,
    badge: String?,
) {
    val icon = @Composable {
        Icon(
            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = null, // the item carries the description
            modifier = Modifier
                .size(24.dp)
                .scale(scale),
        )
    }
    if (badge != null) {
        BadgedBox(badge = { Badge { Text(badge) } }) { icon() }
    } else {
        Box { icon() }
    }
}

/**
 * The medium/expanded counterpart. Android's guidance is a navigation RAIL once
 * the window is wider than compact - a bottom bar on a tablet puts the primary
 * controls as far from the hands as the layout allows.
 */
@Composable
fun FmNavigationRail(
    destinations: List<TopLevelDestination>,
    current: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable ColumnScope.() -> Unit)? = null,
) {
    NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        header = header,
    ) {
        destinations.forEach { destination ->
            val selected = destination == current
            NavigationRailItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = { Text(destination.label) },
                colors = androidx.compose.material3.NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.semantics { contentDescription = destination.contentDescription },
            )
        }
    }
}

/** Transparent scrim colour used behind bars when content scrolls under them. */
val NavScrim: Color get() = Color.Transparent
