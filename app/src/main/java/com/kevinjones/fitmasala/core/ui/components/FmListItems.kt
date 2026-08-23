package com.kevinjones.fitmasala.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kevinjones.fitmasala.core.ui.theme.Fm
import com.kevinjones.fitmasala.core.ui.theme.FmRadius
import com.kevinjones.fitmasala.core.ui.theme.fm

/**
 * List rows at M3's three heights: 56 / 72 / 88dp for one, two and three lines.
 *
 * `defaultMinSize` rather than a fixed height, so a row grows if the user has
 * scaled their font up. A hard height is the standard way a list breaks at large
 * font sizes - the text clips and nobody with default settings ever sees it.
 */
@Composable
fun FmListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    overline: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val minHeight = when {
        overline != null && supporting != null -> Fm.listItemThreeLine
        supporting != null || overline != null -> Fm.listItemTwoLine
        else -> Fm.listItemOneLine
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = minHeight)
            .padding(horizontal = Fm.gutter, vertical = Fm.snug),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Fm.gutter),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (overline != null) {
                Text(
                    text = overline,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.fm.textMuted,
                )
            }
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.fm.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * Leading icon in a tinted container. The container is what stops a list of
 * icons reading as a column of loose glyphs.
 */
@Composable
fun FmListIcon(
    icon: ImageVector,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(40.dp)
            .clip(FmRadius.Input)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Trailing numeric value plus unit. Tabular figures keep columns aligned. */
@Composable
fun FmListValue(value: String, unit: String? = null, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        if (unit != null) {
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.fm.textMuted,
                modifier = Modifier.padding(bottom = 1.dp),
            )
        }
    }
}

/**
 * Divider inset past the leading element, so the rule starts where the TEXT
 * starts. A full-bleed divider under an icon list visually detaches each icon
 * from its own row.
 */
@Composable
fun FmDivider(modifier: Modifier = Modifier, insetStart: androidx.compose.ui.unit.Dp = Fm.gutter) {
    HorizontalDivider(
        modifier = modifier.padding(start = insetStart),
        thickness = 1.dp,
        color = MaterialTheme.fm.borderSubtle,
    )
}

/** A labelled row of stats, used in card headers. */
@Composable
fun FmStatRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
        content = content,
    )
}

/** One stat in a [FmStatRow]: big number, small label under it. */
@Composable
fun FmStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = tint)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.fm.textSecondary)
    }
}
