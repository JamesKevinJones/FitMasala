package com.kevinjones.fitmasala.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class AiRequest(
    val prompt: String,
    val systemPrompt: String? = null,
    val maxTokens: Int = 2000
)

@Serializable
data class AiResponse(
    val text: String,
    val usage: AiUsage? = null
)

@Serializable
data class AiUsage(
    val promptTokens: Int,
    val completionTokens: Int
)

@Serializable
data class ChefResponse(
    val recipeName: String,
    val ingredients: List<String>,
    val instructions: List<String>,
    val macros: AiMacros,
    val notes: String? = null
)

@Serializable
data class AiMacros(
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int
)
