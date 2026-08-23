package com.kevinjones.fitmasala.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kevinjones.fitmasala.core.ui.theme.Fm
import com.kevinjones.fitmasala.core.ui.theme.FmMotion
import com.kevinjones.fitmasala.core.ui.theme.FmRadius

/**
 * Quick-add: a compact ADD button that becomes a stepper once the count is
 * above zero.
 *
 * Borrowed from Indian quick-commerce (Blinkit, Zepto, Instamart), where it
 * exists because adding six items to a basket has to cost six taps, not six
 * navigations. It transfers to food logging almost unchanged, and is arguably a
 * better fit here: portions are naturally counted - two rotis, one katori - so
 * the stepper maps onto the actual unit rather than an abstract quantity.
 *
 * What it replaces: tap meal, open a detail screen, set a portion, confirm, go
 * back. Logging a repeat breakfast should take one tap, and this is the pattern
 * that makes that possible.
 *
 * The swap between the two states is a single element changing shape, not a
 * crossfade between two components - the button WIDENS into the stepper, so the
 * plus stays put and the minus arrives beside it.
 */
@Composable
fun FmQuickAdd(
    count: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "portion",
    enabled: Boolean = true,
) {
    val accent = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.primaryContainer

    AnimatedContent(
        targetState = count > 0,
        transitionSpec = {
            (fadeIn(tween(FmMotion.Press)) + scaleIn(initialScale = 0.9f, animationSpec = tween(FmMotion.Press)))
                .togetherWith(fadeOut(tween(FmMotion.Press / 2)) + scaleOut(targetScale = 0.9f))
        },
        label = "quickAdd",
        modifier = modifier,
    ) { expanded ->
        if (!expanded) {
            Row(
                modifier = Modifier
                    .clip(FmRadius.Input)
                    .background(container)
                    .border(1.dp, accent.copy(alpha = 0.35f), FmRadius.Input)
                    .clickable(enabled = enabled, onClick = onIncrement)
                    .defaultMinSize(minWidth = 76.dp, minHeight = Fm.touchTarget)
                    .padding(horizontal = Fm.snug),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Add",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    modifier = Modifier.semantics { contentDescription = "Add one $label" },
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .clip(FmRadius.Input)
                    .background(accent)
                    .defaultMinSize(minWidth = 76.dp, minHeight = Fm.touchTarget),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepperButton(
                    icon = Icons.Filled.Remove,
                    description = "Remove one $label",
                    onClick = onDecrement,
                )
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                StepperButton(
                    icon = Icons.Filled.Add,
                    description = "Add one $label",
                    onClick = onIncrement,
                )
            }
        }
    }
}

/**
 * Each half of the stepper is a full 48dp target even though the glyph is 16dp.
 * A stepper whose minus is a 20dp hit area is the single most common way this
 * pattern gets shipped broken - it looks right and misses taps.
 */
@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(Fm.touchTarget)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(16.dp),
        )
    }
}
