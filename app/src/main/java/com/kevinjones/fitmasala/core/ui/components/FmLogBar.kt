package com.kevinjones.fitmasala.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kevinjones.fitmasala.core.ui.theme.Fm
import com.kevinjones.fitmasala.core.ui.theme.FmMotion
import com.kevinjones.fitmasala.core.ui.theme.FmRadius

/**
 * Sticky bar summarising a pending selection, with the commit action on it.
 *
 * The quick-commerce "N items - view cart" bar, doing a job it does better here
 * than it does there. A basket bar shows a price, which the shopper already
 * knows; this shows the CALORIE TOTAL of what is about to be logged, which is
 * exactly the number the user cannot compute in their head and the one that
 * decides whether they log the fourth roti.
 *
 * It slides up only when there is something pending, and slides out faster than
 * it came in - a bar that lingers while leaving reads as sluggish.
 */
@Composable
fun FmStickyLogBar(
    visible: Boolean,
    itemCount: Int,
    totalKcal: Int,
    proteinG: Int,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
    commitLabel: String = "Log it",
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(FmMotion.Surface, easing = FmMotion.EaseOutQuart),
            initialOffsetY = { it },
        ) + fadeIn(tween(FmMotion.Surface)),
        exit = slideOutVertically(
            animationSpec = tween(FmMotion.SurfaceExit, easing = FmMotion.EaseOutQuart),
            targetOffsetY = { it },
        ) + fadeOut(tween(FmMotion.SurfaceExit)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Fm.gutter)
                .clip(FmRadius.Card)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onCommit)
                .padding(horizontal = Fm.gap, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "$itemCount item${if (itemCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                )
                Text(
                    // Protein alongside calories, because on a cut the protein
                    // number is the one that decides whether a meal is a good
                    // idea. A calorie total alone answers half the question.
                    text = "$totalKcal kcal · ${proteinG}g protein",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                text = commitLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/**
 * A single glanceable fact, rendered as a strip.
 *
 * Quick-commerce apps put "delivery in 10 minutes" at the top of the screen
 * because speed is their whole proposition. The equivalent claim here is not
 * speed but CONTINUITY - what the app knows that the user would otherwise have
 * to work out - so this strip carries things like "You are 190 kcal under, with
 * dinner still to log".
 */
@Composable
fun FmInsightStrip(
    text: String,
    modifier: Modifier = Modifier,
    emphasis: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(FmRadius.Input)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = Fm.gutter, vertical = Fm.snug),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Fm.tight),
    ) {
        if (emphasis != null) {
            Text(
                text = emphasis,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
