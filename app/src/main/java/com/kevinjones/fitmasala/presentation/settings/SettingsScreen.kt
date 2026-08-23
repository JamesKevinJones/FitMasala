package com.kevinjones.fitmasala.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brightness4
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import com.kevinjones.fitmasala.core.ui.components.FmButton
import com.kevinjones.fitmasala.core.ui.components.FmButtonGhost
import com.kevinjones.fitmasala.core.ui.components.FmCard
import com.kevinjones.fitmasala.core.ui.components.FmRadioGroup
import com.kevinjones.fitmasala.core.ui.components.FmRadioRow
import com.kevinjones.fitmasala.core.ui.components.FmSliderRow
import com.kevinjones.fitmasala.core.ui.components.FmSwitchRow
import com.kevinjones.fitmasala.core.ui.components.FmTextField
import com.kevinjones.fitmasala.core.ui.components.SectionHeader
import com.kevinjones.fitmasala.data.prefs.AppSettings
import com.kevinjones.fitmasala.data.prefs.ThemeMode
import com.kevinjones.fitmasala.core.ui.theme.Fm

/**
 * Settings.
 *
 * Grouped into cards by topic rather than presented as one long list, so the
 * three unrelated concerns here - appearance, the API key, training defaults -
 * are visibly separate.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onThemeMode: (ThemeMode) -> Unit,
    onApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onRestSeconds: (Int) -> Unit,
    onRestVibrate: (Boolean) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Fm.section),
    ) {
        item("appearance-header") { SectionHeader("Appearance") }
        item("appearance") {
            FmCard(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = Fm.tight),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                // A radio group, not a switch. A switch has two states and the
                // theme has three - collapsing "follow the system" into an
                // implicit fourth option is how apps end up unable to go back to
                // it once the user has touched the toggle.
                FmRadioGroup {
                    ThemeMode.entries.forEach { mode ->
                        FmRadioRow(
                            title = mode.label,
                            supporting = mode.description,
                            selected = settings.themeMode == mode,
                            onSelect = { onThemeMode(mode) },
                            leading = when (mode) {
                                ThemeMode.SYSTEM -> Icons.Outlined.Brightness4
                                ThemeMode.LIGHT -> Icons.Outlined.LightMode
                                ThemeMode.DARK -> Icons.Outlined.DarkMode
                            },
                        )
                    }
                }
            }
        }

        item("api-header") { SectionHeader("AI chef") }
        item("api") { ApiKeyCard(settings, onApiKey, onClearApiKey) }

        item("training-header") { SectionHeader("Training") }
        item("training") {
            FmCard(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = Fm.tight),
                verticalArrangement = Arrangement.spacedBy(Fm.hair),
            ) {
                FmSliderRow(
                    title = "Default rest",
                    value = settings.defaultRestSeconds,
                    onValueChange = onRestSeconds,
                    valueRange = 30..300,
                    step = 15,
                    unit = "s",
                )
                FmSwitchRow(
                    title = "Vibrate when rest ends",
                    supporting = "So you can pocket the phone between sets",
                    checked = settings.restTimerVibrate,
                    onCheckedChange = onRestVibrate,
                )
            }
        }

        item("privacy") {
            Text(
                text = "Everything stays on this phone. Backups are switched off " +
                    "app-wide, so your meal log and API key are never copied to " +
                    "your Google account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Fm.hair),
            )
        }
    }
}

/**
 * The API key card.
 *
 * The saved key is shown MASKED and is not editable in place - editing a
 * password field you cannot read is guesswork. Replacing it means pasting a new
 * one, which is what actually happens: nobody edits four characters in the
 * middle of an API key.
 */
@Composable
private fun ApiKeyCard(
    settings: AppSettings,
    onApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }

    FmCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Fm.snug)) {
        if (settings.hasApiKey) {
            Text("Key saved", style = MaterialTheme.typography.titleMedium)
            Text(
                text = settings.maskedApiKey,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FmButtonGhost("Remove key", onClick = onClearApiKey, modifier = Modifier.fillMaxWidth())
        } else {
            Text("Add your API key", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "The app talks to Anthropic directly with your own key. " +
                    "There is no server in between, and the key never leaves this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FmTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = "sk-ant-...",
                modifier = Modifier.fillMaxWidth(),
                // Password transformation by default, with a reveal, because a
                // pasted key that cannot be checked is a key pasted wrong.
                visualTransformation = if (reveal) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                ),
            )
            FmSwitchRow(
                title = "Show key while typing",
                checked = reveal,
                onCheckedChange = { reveal = it },
            )
            FmButton(
                text = "Save key",
                onClick = {
                    onApiKey(draft)
                    draft = ""
                    reveal = false
                },
                enabled = draft.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
