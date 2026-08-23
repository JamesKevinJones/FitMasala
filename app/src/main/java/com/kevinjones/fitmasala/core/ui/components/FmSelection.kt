package com.kevinjones.fitmasala.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.kevinjones.fitmasala.core.ui.theme.Fm
import com.kevinjones.fitmasala.core.ui.theme.fm
import kotlin.math.roundToInt

// Selection controls, per Material's selection category: checkbox, radio,
// switch, slider, segmented button.
//
// Each is wrapped so the whole ROW is the touch target. The bare controls are
// about 20dp of visible art, and anyone tapping the label of a settings row
// expects it to toggle. Putting the semantics on the row also means TalkBack
// announces "Vibrate when rest ends, switch, on" rather than an unlabelled
// switch floating next to some text.

/** Settings row with a switch. The row toggles, not just the thumb. */
@Composable
fun FmSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .defaultMinSize(minHeight = Fm.touchTarget)
            .padding(horizontal = Fm.gutter, vertical = Fm.snug),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Fm.gutter),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.fm.textSecondary,
                )
            }
        }
        // null handler: the row already owns the interaction. A second handler
        // here would double-fire when the thumb itself is tapped.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** One option in a single-choice group. Used by the theme picker. */
@Composable
fun FmRadioRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .defaultMinSize(minHeight = Fm.touchTarget)
            .padding(horizontal = Fm.gutter, vertical = Fm.snug),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Fm.snug),
    ) {
        RadioButton(selected = selected, onClick = null)
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = MaterialTheme.fm.textSecondary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.fm.textSecondary,
                )
            }
        }
    }
}

/**
 * Wraps a set of radio rows so assistive tech reads them as ONE control with
 * n options, rather than n unrelated radio buttons.
 */
@Composable
fun FmRadioGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.selectableGroup()) { content() }
}

@Composable
fun FmCheckboxRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .defaultMinSize(minHeight = Fm.touchTarget)
            .padding(horizontal = Fm.gutter, vertical = Fm.snug),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Fm.snug),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.fm.textSecondary,
                )
            }
        }
    }
}

/**
 * Slider with its live value shown.
 *
 * A slider with no readout is unusable for anything numeric - "roughly two
 * thirds" is not a rest interval. `steps` snaps it so 90 seconds is actually
 * reachable instead of landing on 87.
 */
@Composable
fun FmSliderRow(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    modifier: Modifier = Modifier,
    step: Int = 1,
    unit: String = "",
) {
    val steps = ((valueRange.last - valueRange.first) / step) - 1
    Column(
        modifier.fillMaxWidth().padding(horizontal = Fm.gutter, vertical = Fm.tight),
        verticalArrangement = Arrangement.spacedBy(Fm.hair),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                "$value$unit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = steps.coerceAtLeast(0),
        )
    }
}

/** Segmented buttons: a small, mutually exclusive set shown all at once. */
@Composable
fun <T> FmSegmentedButtons(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Text(label(option), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
