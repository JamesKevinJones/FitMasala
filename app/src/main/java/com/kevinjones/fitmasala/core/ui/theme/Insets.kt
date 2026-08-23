package com.kevinjones.fitmasala.core.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp

/**
 * Content padding for a scrolling screen under edge-to-edge.
 *
 * The point of edge-to-edge is that content scrolls BENEATH the system bars
 * rather than stopping above them. That means the bar insets belong in a
 * scrolling container's `contentPadding`, never in a `Modifier.padding` on its
 * parent - padding the parent clips the list above the navigation bar and
 * produces a dead strip exactly where the design intends to show content
 * passing under glass.
 *
 * This merges four things that are each easy to forget on their own:
 *
 * 1. **Scaffold's own padding** — the app bar above, the navigation bar below.
 * 2. **The horizontal window margin** for the size class.
 * 3. **The display cutout**, horizontally. In landscape a camera cutout eats
 *    into the side of the window, and text will otherwise run under it. This is
 *    invisible in portrait, which is where it usually ships.
 * 4. **Clearance for the floating action button**, so the last item in a list is
 *    not permanently sitting under it.
 */
@Composable
fun screenContentPadding(
    scaffoldPadding: PaddingValues,
    horizontalMargin: Dp,
    topExtra: Dp = Fm.tight,
    /** Room for the FAB plus a gap, so the final row is never obscured. */
    bottomExtra: Dp = Fm.fabSize + Fm.gap,
): PaddingValues {
    val direction = LocalLayoutDirection.current
    val cutout = WindowInsets.displayCutout.asPaddingValues()

    return PaddingValues(
        start = horizontalMargin + cutout.calculateStartPadding(direction),
        end = horizontalMargin + cutout.calculateEndPadding(direction),
        top = scaffoldPadding.calculateTopPadding() + topExtra,
        bottom = scaffoldPadding.calculateBottomPadding() + bottomExtra,
    )
}
