package com.kevinjones.fitmasala.data.remote

import com.kevinjones.fitmasala.data.prefs.LlmProvider
import com.kevinjones.fitmasala.data.prefs.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends every LLM call - Chef recipes, photo and typed-dish estimates - to the
 * provider chosen in Settings, and only there. One choice covers the whole app,
 * so a Gemini-only setup needs no Anthropic key (#24).
 *
 * Deliberately no fallback: if the chosen provider fails, the failure is shown
 * (retry, or log by hand). Quietly asking the other model would mix two
 * estimation biases in one log - the one error `AdaptiveTdee` cannot cancel.
 */
@Singleton
class ProviderBackend internal constructor(
    private val anthropic: LlmBackend,
    private val gemini: LlmBackend,
    private val provider: suspend () -> LlmProvider,
) : LlmBackend {

    @Inject
    constructor(anthropic: AnthropicBackend, gemini: GeminiBackend, settingsStore: SettingsStore) :
        this(anthropic, gemini, { settingsStore.current().provider })

    override suspend fun complete(request: LlmRequest): LlmResult<String> =
        when (runCatching { provider() }.getOrDefault(LlmProvider.ANTHROPIC)) {
            LlmProvider.ANTHROPIC -> anthropic.complete(request)
            LlmProvider.GEMINI -> gemini.complete(request)
        }
}
