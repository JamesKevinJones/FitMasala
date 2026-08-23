package com.kevinjones.fitmasala.core.ui.theme

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The 8dp grid, per Android's grids-and-units guidance: 8dp is the primary grid
 * for layout and spacing, 4dp the secondary grid for icons, type and component
 * detail.
 *
 * Named by role rather than by size. `Fm.gutter` survives a decision to change
 * the gutter; `Fm.space16` does not, and a codebase full of raw `16.dp` is how
 * spacing quietly drifts screen by screen.
 */
object Fm {
    /** Secondary grid. Icon insets, tight label gaps. */
    val hair: Dp = 4.dp

    /** Inside a chip or badge. */
    val tight: Dp = 8.dp

    /** Between related items in a group. */
    val snug: Dp = 12.dp

    /** Standard padding inside a card or list item. */
    val gutter: Dp = 16.dp

    /** Between distinct items in a list or grid. */
    val gap: Dp = 20.dp

    /** Between sections on a screen. */
    val section: Dp = 28.dp

    /** Above a new major block after a section header. */
    val block: Dp = 36.dp

    /**
     * Minimum touch target. Anything interactive smaller than this needs padding
     * to reach it, even when the visible art is smaller - a 24dp icon button
     * must still occupy 48dp of tappable area.
     */
    val touchTarget: Dp = 48.dp

    /** M3 component heights, so screens do not each invent their own. */
    val navBarHeight: Dp = 80.dp
    val topBarSmall: Dp = 64.dp
    val topBarLarge: Dp = 152.dp
    val fabSize: Dp = 56.dp
    val listItemOneLine: Dp = 56.dp
    val listItemTwoLine: Dp = 72.dp
    val listItemThreeLine: Dp = 88.dp
}

/**
 * Horizontal window margin by size class.
 *
 * Compact phones get 16dp; anything wider gets 24dp and a max content width, so
 * text on a tablet or unfolded device does not run to a 900dp measure. Line
 * length is a legibility constraint, not a layout preference.
 */
fun WindowSizeClass.horizontalMargin(): Dp = when (widthSizeClass) {
    WindowWidthSizeClass.Compact -> 16.dp
    else -> 24.dp
}

/** Above this, content centres rather than stretching. */
val MaxContentWidth: Dp = 640.dp
