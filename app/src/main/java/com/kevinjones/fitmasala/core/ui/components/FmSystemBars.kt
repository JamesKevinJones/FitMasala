package com.kevinjones.fitmasala.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Gradient protection behind a transparent system bar.
 *
 * Android's system-bars guidance calls for this specifically where content
 * scrolls beneath a transparent bar: the status icons are drawn by the system
 * over whatever happens to be underneath them, and over a photo or a bright card
 * they become unreadable. A solid bar would fix it and throw away edge-to-edge;
 * a gradient keeps the depth and guarantees the contrast.
 *
 * The gradient runs from the app background at full opacity down to fully
 * transparent, so there is no visible edge where it ends. A flat semi-opaque
 * scrim would draw a hard line across the screen.
 */
@Composable
fun StatusBarProtection(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.background,
) {
    val height = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    // Not a linear ramp: an eased stop keeps the fade from
                    // reading as a visible band partway down.
                    colorStops = arrayOf(
                        0.0f to color.copy(alpha = 1f),
                        0.6f to color.copy(alpha = 0.7f),
                        1.0f to Color.Transparent,
                    ),
                ),
            ),
    )
}

/**
 * The same, for the bottom.
 *
 * Only needed where content scrolls under a transparent navigation bar with no
 * bottom app bar or navigation bar of its own covering it. On the main screens
 * the NavigationBar already fills that region, so this would be a scrim over a
 * surface - use it on full-bleed screens like photo capture.
 */
@Composable
fun NavigationBarProtection(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.background,
) {
    val height = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.4f to color.copy(alpha = 0.7f),
                        1.0f to color.copy(alpha = 1f),
                    ),
                ),
            ),
    )
}
