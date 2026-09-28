package com.kevinjones.fitmasala.data.remote.prompt

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * The same schema Anthropic receives, rewritten as Gemini's typed `Schema` (an
 * OpenAPI 3 subset) - converted, never written twice, so the two providers
 * cannot be asked for different shapes.
 *
 * Every key emitted is a field of `Schema` in the Gemini API discovery document
 * (revision 20260927), which rejects unknown fields:
 * - `type` becomes the upper-case enum; `["string", "null"]` becomes STRING
 *   with `nullable: true`.
 * - `enum` gains `format: "enum"`, as the document's example shows.
 * - `properties` gains `propertyOrdering`, so fields come back in schema order.
 * - `additionalProperties: false` is dropped: `Schema` has no such field, and a
 *   stray key is ignored by the app's parser anyway.
 *
 * Any other JSON Schema keyword throws, so adding one to [RecipeSchemas] fails a
 * test here instead of a request at Google.
 */
object GeminiSchema {

    fun from(jsonSchema: JsonObject): JsonObject = buildJsonObject {
        for ((key, value) in jsonSchema) {
            when (key) {
                "type" -> {
                    val (type, nullable) = typeOf(value)
                    put("type", type)
                    if (nullable) put("nullable", true)
                }
                "description" -> put("description", value)
                "required" -> put("required", value)
                "enum" -> {
                    put("format", "enum")
                    put("enum", value)
                }
                "items" -> put("items", from(value as JsonObject))
                "properties" -> {
                    val properties = value as JsonObject
                    put("properties", JsonObject(properties.mapValues { (_, schema) -> from(schema as JsonObject) }))
                    putJsonArray("propertyOrdering") { properties.keys.forEach { add(JsonPrimitive(it)) } }
                }
                "additionalProperties" -> Unit
                else -> throw IllegalArgumentException("No Gemini Schema equivalent for JSON Schema keyword '$key'")
            }
        }
    }

    /** `"string"` → STRING; `["string", "null"]` → STRING, nullable. */
    private fun typeOf(value: JsonElement): Pair<String, Boolean> = when (value) {
        is JsonPrimitive -> value.content.uppercase() to false
        is JsonArray -> {
            val names = value.map { it.jsonPrimitive.contentOrNull.orEmpty() }
            val concrete = names.filter { it != "null" }
            require(concrete.size == 1) { "Gemini Schema has one type per value, not $names" }
            concrete.single().uppercase() to ("null" in names)
        }
        else -> throw IllegalArgumentException("Unexpected JSON Schema type: $value")
    }
}
