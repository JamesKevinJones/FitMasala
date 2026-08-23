package com.kevinjones.fitmasala.core.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kevinjones.fitmasala.core.ui.theme.FmMotion
import com.kevinjones.fitmasala.core.ui.theme.FmRadius
import com.kevinjones.fitmasala.core.ui.theme.fm

/**
 * The tactile primary button.
 *
 * A darker tone of the fill sits 4dp beneath the face. Pressing drops the face
 * onto it, so the button compresses instead of just changing colour. Releasing
 * lifts it back.
 *
 * Why this and not the hard offset shadow it replaces: the lip is a TONAL shade
 * of the button's own fill, rounded on the same radius, with no border. It reads
 * as one physical object with thickness. The previous version was a black
 * rectangle offset behind a bordered box - two flat shapes, a sticker rather
 * than a button.
 *
 * It is also rationed. Only primary actions get it; [FmButtonTonal] and
 * [FmButtonGhost] are flat. A screen where everything is tactile has no
 * hierarchy, and the effect stops registering by the third press.
 *
 * 90ms: this fires hundreds of times a day, so it has to feel immediate. Any
 * flourish here would wear out inside a week.
 */
private val LipDepth = 4.dp
private val ButtonHeight = 52.dp

@Composable
fun FmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.primary,
    lip: Color = MaterialTheme.fm.accentPressed,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val drop by animateDpAsState(
        targetValue = if (pressed && enabled) LipDepth else 0.dp,
        animationSpec = tween(FmMotion.Press, easing = FmMotion.EaseOutExpo),
        label = "buttonDrop",
    )

    // Disabled is a flat, lipless surface. Keeping the lip would promise a
    // press that never happens.
    val face = if (enabled) container else MaterialTheme.colorScheme.surfaceContainerHigh
    val lipColor = if (enabled) lip else Color.Transparent
    val ink = if (enabled) contentColor else MaterialTheme.fm.textMuted

    Box(modifier.height(ButtonHeight + LipDepth)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(ButtonHeight)
                .align(Alignment.BottomCenter)
                .background(lipColor, FmRadius.Card),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonHeight)
                .align(Alignment.TopCenter)
                .offset(y = drop)
                .clip(FmRadius.Card)
                .background(face)
                .clickable(
                    interactionSource = interaction,
                    indication = null, // the compression IS the feedback
                    enabled = enabled,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    CompositionLocalProvider(LocalContentColor provides ink) { leading() }
                }
                Text(text, style = MaterialTheme.typography.labelLarge, color = ink)
            }
        }
    }
}

/** Flat, filled with a soft wash. Secondary actions. */
@Composable
fun FmButtonTonal(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier = modifier
            .height(ButtonHeight)
            .clip(FmRadius.Card)
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) contentColor else MaterialTheme.fm.textMuted,
        )
    }
}

/** Outline only. Tertiary - "skip", "not now". */
@Composable
fun FmButtonGhost(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(ButtonHeight)
            .clip(FmRadius.Card)
            .border(1.dp, MaterialTheme.fm.borderStrong, FmRadius.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.fm.textSecondary,
        )
    }
}
