package com.kevinjones.fitmasala.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.kevinjones.fitmasala.core.ui.theme.FmRadius
import com.kevinjones.fitmasala.core.ui.theme.fm
import com.kevinjones.fitmasala.domain.progress.StreakEngine
import com.kevinjones.fitmasala.domain.progress.StreakTier
import com.kevinjones.fitmasala.domain.progress.XpEngine

/**
 * Streak badge. The flame only warms as the streak grows - grey under a week,
 * saffron to thirty days, then red.
 *
 * Colour is the reward here, which is why the early tier is deliberately dull:
 * a day-one streak that already looks like a day-hundred streak has nothing
 * left to give.
 */
@Composable
fun StreakBadge(
    days: Int,
    modifier: Modifier = Modifier,
    atRisk: Boolean = false,
) {
    val fm = MaterialTheme.fm
    val tint = when (StreakEngine.tierFor(days)) {
        StreakTier.COLD -> fm.streakCold
        StreakTier.WARM -> fm.streakWarm
        StreakTier.HOT -> fm.streakHot
    }

    Row(
        modifier = modifier
            .clip(FmRadius.Pill)
            .background(tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = 0.35f), FmRadius.Pill)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🔥", style = MaterialTheme.typography.labelMedium)
        Text(
            text = if (days == 0) "No streak" else "$days day${if (days == 1) "" else "s"}",
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
        if (atRisk) {
            Text(
                text = "· log today",
                style = MaterialTheme.typography.labelSmall,
                color = fm.textMuted,
            )
        }
    }
}

/**
 * Level and XP bar.
 *
 * Shows XP remaining rather than XP earned. "180 XP to level 6" is an
 * instruction; "1,420 XP" is trivia.
 */
@Composable
fun LevelBar(
    totalXp: Int,
    modifier: Modifier = Modifier,
) {
    val level = XpEngine.levelFor(totalXp)
    val progress = XpEngine.levelProgress(totalXp)
    val remaining = XpEngine.xpToNextLevel(totalXp)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("Level $level", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "$remaining XP to ${level + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.fm.textSecondary,
            )
        }
        FmMeter(
            value = progress,
            target = 1f,
            color = MaterialTheme.fm.progress,
            height = 10.dp,
        )
    }
}

/**
 * Marks a number as an AI estimate rather than a weighed value.
 *
 * This replaces the diagonal hazard stripes, which shouted a caution the
 * content did not warrant - the app's whole premise is estimates, so treating
 * every one as a hazard trains the user to ignore the marker entirely. A quiet
 * inline badge keeps the distinction available without making it an alarm.
 */
@Composable
fun EstimateBadge(
    modifier: Modifier = Modifier,
    confidence: Double? = null,
) {
    val label = when {
        confidence == null -> "Estimate"
        confidence >= 0.8 -> "Estimate · high confidence"
        confidence >= 0.5 -> "Estimate · medium confidence"
        else -> "Estimate · low confidence"
    }
    Box(
        modifier = modifier
            .clip(FmRadius.Badge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.fm.textMuted,
        )
    }
}

/** A completed goal. Only rendered once actually hit - never as an empty promise. */
@Composable
fun GoalMetPill(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(FmRadius.Pill)
            .background(MaterialTheme.fm.progressSoft)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✓", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fm.progress)
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.fm.progress,
        )
    }
}
