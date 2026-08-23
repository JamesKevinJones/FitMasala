package com.kevinjones.fitmasala.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Theme preference.
 *
 * SYSTEM is the default and the one to keep unless the user says otherwise -
 * following the OS preference is what Material asks for, and someone who set
 * their phone to dark at night expects apps to honour it. LIGHT and DARK exist
 * because a preference is not a preference unless it can be overridden: a user
 * who finds light-on-dark halates needs light mode in this app even when their
 * phone is dark.
 */
enum class ThemeMode(val label: String, val description: String) {
    SYSTEM("System", "Match your phone's setting"),
    LIGHT("Light", "Always light"),
    DARK("Dark", "Always dark"),
}

/**
 * Which provider the pasted key belongs to.
 *
 * Single-valued on purpose. The original brief said "Anthropic/OpenAI", but a
 * selectable OPENAI with no implementation behind it is a stub that fails at
 * runtime - the enum stays as the seam a second provider would slot into.
 */
enum class LlmProvider(val label: String) {
    ANTHROPIC("Anthropic"),
}

/** Everything on the Settings screen, as one immutable snapshot. */
data class AppSettings(
    val apiKey: String = "",
    val provider: LlmProvider = LlmProvider.ANTHROPIC,
    val modelId: String = "",
    val dailyCalorieTarget: Int = 2200,
    val proteinTargetG: Int = 150,
    val carbTargetG: Int = 250,
    val fatTargetG: Int = 70,
    val defaultRestSeconds: Int = 90,
    val restTimerVibrate: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    /** Never render the key itself — this is what Settings shows once it's saved. */
    val maskedApiKey: String
        get() = when {
            apiKey.isBlank() -> ""
            apiKey.length <= 8 -> "•".repeat(apiKey.length)
            else -> apiKey.take(4) + "•".repeat(12) + apiKey.takeLast(4)
        }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fitmasala_settings")

/**
 * Settings live in DataStore rather than as a Room row.
 *
 * The prompt asked for an API-key entity, and this deliberately deviates: a
 * single-row settings table needs its own upsert dance, its own migration on
 * every new preference, and gives nothing back. DataStore is built for exactly
 * this shape and hands back a Flow for free.
 *
 * The key is NOT encrypted at rest. `androidx.security:security-crypto` is
 * deprecated and its replacement isn't stable, so instead the real exfiltration
 * path is closed: cloud backup and device transfer are excluded app-wide in the
 * manifest. On a non-rooted device, app-private storage is not readable by other
 * apps. See docs/DECISIONS.md.
 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val API_KEY = stringPreferencesKey("api_key")
        val PROVIDER = stringPreferencesKey("provider")
        val MODEL_ID = stringPreferencesKey("model_id")
        val CALORIE_TARGET = intPreferencesKey("calorie_target")
        val PROTEIN_TARGET = intPreferencesKey("protein_target")
        val CARB_TARGET = intPreferencesKey("carb_target")
        val FAT_TARGET = intPreferencesKey("fat_target")
        val REST_SECONDS = intPreferencesKey("rest_seconds")
        val REST_VIBRATE = booleanPreferencesKey("rest_vibrate")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    /**
     * A corrupt or unreadable preferences file emits defaults instead of
     * throwing. An IOException here would otherwise crash the app at startup
     * with no way back in — and the recovery is simply "re-paste the key".
     */
    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { prefs ->
            val provider = prefs[Keys.PROVIDER]
                ?.let { raw -> LlmProvider.entries.firstOrNull { it.name == raw } }
                ?: LlmProvider.ANTHROPIC
            AppSettings(
                apiKey = prefs[Keys.API_KEY].orEmpty(),
                provider = provider,
                modelId = prefs[Keys.MODEL_ID].orEmpty(),
                dailyCalorieTarget = prefs[Keys.CALORIE_TARGET] ?: 2200,
                proteinTargetG = prefs[Keys.PROTEIN_TARGET] ?: 150,
                carbTargetG = prefs[Keys.CARB_TARGET] ?: 250,
                fatTargetG = prefs[Keys.FAT_TARGET] ?: 70,
                defaultRestSeconds = prefs[Keys.REST_SECONDS] ?: 90,
                restTimerVibrate = prefs[Keys.REST_VIBRATE] ?: true,
                themeMode = prefs[Keys.THEME_MODE]
                    ?.let { raw -> ThemeMode.entries.firstOrNull { it.name == raw } }
                    ?: ThemeMode.SYSTEM,
            )
        }

    /** For the network interceptor, which needs the key once per call, not as a stream. */
    suspend fun current(): AppSettings = settings.first()

    suspend fun setApiKey(key: String) = edit { it[Keys.API_KEY] = key.trim() }

    suspend fun clearApiKey() = edit { it.remove(Keys.API_KEY) }

    suspend fun setProvider(provider: LlmProvider) = edit { it[Keys.PROVIDER] = provider.name }

    suspend fun setModelId(modelId: String) = edit { it[Keys.MODEL_ID] = modelId.trim() }

    suspend fun setMacroTargets(calories: Int, proteinG: Int, carbsG: Int, fatG: Int) = edit {
        it[Keys.CALORIE_TARGET] = calories
        it[Keys.PROTEIN_TARGET] = proteinG
        it[Keys.CARB_TARGET] = carbsG
        it[Keys.FAT_TARGET] = fatG
    }

    suspend fun setDefaultRestSeconds(seconds: Int) = edit { it[Keys.REST_SECONDS] = seconds }

    suspend fun setRestTimerVibrate(enabled: Boolean) = edit { it[Keys.REST_VIBRATE] = enabled }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
