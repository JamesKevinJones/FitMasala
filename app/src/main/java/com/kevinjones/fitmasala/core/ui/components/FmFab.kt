package com.kevinjones.fitmasala.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kevinjones.fitmasala.core.ui.theme.Fm
import com.kevinjones.fitmasala.core.ui.theme.FmMotion
import com.kevinjones.fitmasala.core.ui.theme.FmRadius

/** One entry in the expanded FAB menu. */
data class FabAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * The quick-launch FAB.
 *
 * Tapping expands a small stack of labelled actions rather than opening a menu
 * elsewhere - the actions appear where the thumb already is, which is the whole
 * reason the FAB sits in the bottom corner.
 *
 * Three deliberate choices:
 *
 * - **Labels are always visible when expanded.** An icon-only stack is a
 *   guessing game, and these two actions ("Ask the chef", "Log a workout") are
 *   not conventional enough to be recognised from a glyph.
 * - **Actions stagger by 40ms.** Enough to read as a stack unfolding rather than
 *   three things appearing at once; short enough that the last one is on screen
 *   inside 200ms.
 * - **The plus rotates to a close.** One element transforming, not two crossfading
 *   - a crossfade reads as two separate icons swapping places.
 */
@Composable
fun FmExpandableFab(
    actions: List<FabAction>,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(FmMotion.Surface, easing = FmMotion.EaseOutQuart),
        label = "fabRotation",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(Fm.snug),
    ) {
        actions.forEachIndexed { index, action ->
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(FmMotion.Surface, delayMillis = index * 40)) +
                    slideInVertically(
                        animationSpec = tween(FmMotion.Surface, delayMillis = index * 40),
                        initialOffsetY = { it / 2 },
                    ) +
                    scaleIn(
                        // Never from zero: scaling from 0 makes an element appear
                        // to arrive from nowhere. 0.8 reads as it settling in.
                        initialScale = 0.8f,
                        animationSpec = tween(FmMotion.Surface, delayMillis = index * 40),
                    ),
                // Exit is quicker and unstaggered - a menu should get out of the way.
                exit = fadeOut(tween(FmMotion.SurfaceExit)) +
                    scaleOut(targetScale = 0.8f, animationSpec = tween(FmMotion.SurfaceExit)) +
                    slideOutVertically(
                        animationSpec = tween(FmMotion.SurfaceExit),
                        targetOffsetY = { it / 2 },
                    ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Fm.snug),
                ) {
                    Text(
                        text = action.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(FmRadius.Badge)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable {
                                onExpandedChange(false)
                                action.onClick()
                            }
                            .padding(horizontal = Fm.snug, vertical = Fm.tight),
                    )
                    SmallFloatingActionButton(
                        onClick = {
                            onExpandedChange(false)
                            action.onClick()
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.primary,
                        shape = FmRadius.Input,
                    ) {
                        Icon(action.icon, contentDescription = action.label)
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = FmRadius.Card,
            modifier = Modifier
                .size(Fm.fabSize)
                .semantics {
                    contentDescription = if (expanded) "Close quick actions" else "Quick actions"
                },
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}
