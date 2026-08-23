package com.kevinjones.fitmasala.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kevinjones.fitmasala.core.ui.theme.Fm

/**
 * App bars.
 *
 * The large variant collapses to the small one as content scrolls under it -
 * `TopAppBarDefaults.exitUntilCollapsedScrollBehavior` does the interpolation.
 * This is the single detail that most separates an Android app from a web page
 * in a WebView, and it is free.
 *
 * Title colours are transparent containers over the app background, so the bar
 * only gains a surface once content is actually behind it. A permanently filled
 * bar on a dark app reads as a heavy stripe across the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberCollapsingBarBehavior(): TopAppBarScrollBehavior =
    TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberPinnedBarBehavior(): TopAppBarScrollBehavior =
    TopAppBarDefaults.pinnedScrollBehavior()

/**
 * Large collapsing bar for a top-level destination.
 *
 * @param overline a short line above the title - the date, the day of the cut.
 *   Optional, and never used for anything the title already says.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FmLargeTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    overline: String? = null,
    actions: @Composable () -> Unit = {},
) {
    LargeTopAppBar(
        title = {
            Column {
                if (overline != null) {
                    Text(
                        text = overline,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = { Row(verticalAlignment = Alignment.CenterVertically) { actions() } },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            // Once content scrolls beneath it, the bar takes a surface so the
            // boundary is legible. Before that it is invisible.
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
        modifier = modifier,
    )
}

/** Small bar for a pushed screen. Always carries a back affordance. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FmTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(Fm.touchTarget)) {
                    Icon(
                        // AutoMirrored: the arrow flips in right-to-left locales.
                        // The plain ArrowBack points the wrong way in Urdu or Arabic.
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            }
        },
        actions = { actions() },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = modifier,
    )
}

/**
 * An app-bar action. Wraps IconButton purely to enforce the 48dp target - a bare
 * 24dp icon is a common and invisible accessibility failure, since it looks
 * correct and simply misses taps.
 */
@Composable
fun FmBarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(Fm.touchTarget)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp).padding(0.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
