package com.kevinjones.fitmasala.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kevinjones.fitmasala.core.ui.theme.Fm
import com.kevinjones.fitmasala.core.ui.theme.FmMotion
import com.kevinjones.fitmasala.core.ui.theme.FmRadius
import com.kevinjones.fitmasala.core.ui.theme.fm

/**
 * Empty, loading and error states.
 *
 * These are the majority of what a user sees on day one, and they are the part
 * most often left as "No data." A first-run dashboard IS the empty state, so it
 * gets the same care as the populated one.
 *
 * Every empty state here names a next action. An empty state without one is a
 * dead end dressed as information.
 */
@Composable
fun FmEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Fm.gap, vertical = Fm.block),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Fm.snug),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(FmRadius.Card)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.fm.textMuted,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.fm.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Box(Modifier.padding(top = Fm.tight)) { action() }
        }
    }
}

/**
 * Skeleton placeholder.
 *
 * A shimmering block of the right SHAPE, not a spinner. The skeleton tells the
 * user what is arriving; a spinner only says "wait". Use it above roughly 800ms
 * of expected latency - below that it flashes and reads as a glitch, so an LLM
 * call qualifies and a database read does not.
 */
@Composable
fun FmSkeleton(
    modifier: Modifier = Modifier,
    height: Dp = 20.dp,
    shape: androidx.compose.ui.graphics.Shape = FmRadius.Badge,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            // Slow, symmetric breathing. A fast pulse reads as an error blink.
            animation = tween(900, easing = FmMotion.EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    Box(
        modifier
            .height(height)
            .clip(shape)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

/** A card-shaped skeleton, for a list that is still loading. */
@Composable
fun FmSkeletonCard(modifier: Modifier = Modifier) {
    FmCard(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Fm.snug)) {
        FmSkeleton(Modifier.fillMaxWidth(0.45f), height = 14.dp)
        FmSkeleton(Modifier.fillMaxWidth(0.8f), height = 24.dp)
        FmSkeleton(Modifier.fillMaxWidth(), height = 10.dp)
    }
}

/**
 * Error state.
 *
 * Says what failed and what to do about it. Never "Something went wrong" - the
 * app knows which thing went wrong, and withholding that turns a fixable
 * problem into a mystery.
 */
@Composable
fun FmErrorState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Try again",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(FmRadius.Card)
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f))
            .padding(Fm.gutter),
        verticalArrangement = Arrangement.spacedBy(Fm.tight),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.fm.textSecondary)
        if (onRetry != null) {
            FmButtonGhost(retryLabel, onClick = onRetry, modifier = Modifier.padding(top = Fm.hair))
        }
    }
}

/** Inline advisory - the macro-reconciliation warnings from the LLM client. */
@Composable
fun FmAdvisory(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(FmRadius.Input)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(Fm.snug),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.fm.textSecondary,
        )
    }
}
