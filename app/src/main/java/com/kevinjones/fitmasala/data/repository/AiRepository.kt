package com.kevinjones.fitmasala.data.repository

import com.kevinjones.fitmasala.data.remote.AiService
import com.kevinjones.fitmasala.data.remote.AiRequest
import com.kevinjones.fitmasala.data.remote.SystemPrompts
import com.kevinjones.fitmasala.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepository @Inject constructor(
    private val aiService: AiService,
    private val settingsStore: SettingsStore
) {
    suspend fun getChefAdvice(userPrompt: String): String? {
        val apiKey = settingsStore.settings.first().apiKey
        if (apiKey.isBlank()) return "Please set your API key in Settings."

        val request = AiRequest(
            prompt = userPrompt,
            systemPrompt = SystemPrompts.AUTHENTIC_INDIAN_CHEF
        )

        return try {
            val response = aiService.generateResponse(apiKey, request = request)
            if (response.isSuccessful) {
                response.body()?.text
            } else {
                "Error: ${response.code()} - ${response.message()}"
            }
        } catch (e: Exception) {
            "Exception: ${e.localizedMessage}"
        }
    }
}
