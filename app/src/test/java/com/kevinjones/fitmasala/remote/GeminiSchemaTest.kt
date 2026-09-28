package com.kevinjones.fitmasala.remote

import com.kevinjones.fitmasala.data.remote.prompt.GeminiSchema
import com.kevinjones.fitmasala.data.remote.prompt.RecipeSchemas
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Gemini is asked for exactly the shape Anthropic is (#22): its schema is
 * converted from the one in [RecipeSchemas], and every key it contains is a real
 * field of Gemini's `Schema`, which rejects unknown fields.
 */
class GeminiSchemaTest {

    /** `Schema`'s fields in the Gemini API discovery document, revision 20260927. */
    private val schemaFields = setOf(
        "anyOf", "default", "description", "enum", "example", "format", "items", "maxItems", "maxLength",
        "maxProperties", "maximum", "minItems", "minLength", "minProperties", "minimum", "nullable", "pattern",
        "properties", "propertyOrdering", "required", "title", "type",
    )

    /** `Schema.type`'s enum in the same document. */
    private val schemaTypes = setOf("STRING", "NUMBER", "INTEGER", "BOOLEAN", "ARRAY", "OBJECT", "NULL")

    private val schemas = mapOf("photo estimate" to RecipeSchemas.PHOTO_ESTIMATE, "recipe" to RecipeSchemas.RECIPE)

    private fun walk(schema: JsonObject, path: String, visit: (JsonObject, String) -> Unit) {
        visit(schema, path)
        schema["properties"]?.jsonObject?.forEach { (name, child) -> walk(child.jsonObject, "$path.$name", visit) }
        schema["items"]?.let { walk(it.jsonObject, "$path[]", visit) }
    }

    @Test
    fun everyKeyIsAGeminiSchemaFieldAndEveryTypeAGeminiType() {
        schemas.forEach { (name, source) ->
            walk(GeminiSchema.from(source), name) { node, path ->
                val unknown = node.keys - schemaFields
                assertTrue("$path has fields Gemini would reject: $unknown", unknown.isEmpty())
                val type = node["type"]?.jsonPrimitive?.content
                assertTrue("$path has type $type", type in schemaTypes)
            }
        }
    }

    @Test
    fun theConvertedSchemaAsksForTheSameFieldsRequirementsAndUnits() {
        schemas.forEach { (name, source) ->
            val converted = GeminiSchema.from(source)
            val sourceNodes = mutableMapOf<String, JsonObject>()
            walk(source, name) { node, path -> sourceNodes[path] = node }
            val convertedNodes = mutableMapOf<String, JsonObject>()
            walk(converted, name) { node, path -> convertedNodes[path] = node }

            assertEquals("same fields at every level of the $name", sourceNodes.keys, convertedNodes.keys)
            sourceNodes.forEach { (path, node) ->
                val out = convertedNodes.getValue(path)
                assertEquals("$path required", node["required"], out["required"])
                assertEquals("$path enum", node["enum"], out["enum"])
                assertEquals("$path description", node["description"], out["description"])
                node["properties"]?.jsonObject?.let { properties ->
                    assertEquals(
                        "$path keeps its field order",
                        properties.keys.toList(),
                        out["propertyOrdering"]!!.jsonArray.map { it.jsonPrimitive.content },
                    )
                }
            }
        }
    }

    @Test
    fun nullableAndEnumAreSpelledTheGeminiWay() {
        val converted = GeminiSchema.from(
            buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("nameLocal", buildJsonObject { put("type", JsonArray(listOf(JsonPrimitive("string"), JsonPrimitive("null")))) })
                    put("unit", buildJsonObject {
                        put("type", "string")
                        put("enum", JsonArray(listOf(JsonPrimitive("KATORI"), JsonPrimitive("ROTI"))))
                    })
                })
                put("additionalProperties", false)
            },
        )
        val properties = converted["properties"]!!.jsonObject
        assertEquals("STRING", properties["nameLocal"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(true, properties["nameLocal"]!!.jsonObject["nullable"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("enum", properties["unit"]!!.jsonObject["format"]!!.jsonPrimitive.content)
        assertTrue("additionalProperties has no Gemini field", "additionalProperties" !in converted)
    }

    @Test
    fun aJsonSchemaKeywordWithNoGeminiEquivalentFailsHereNotAtGoogle() {
        try {
            GeminiSchema.from(buildJsonObject { put("type", "string"); put("pattern_", "x") })
            fail("expected an unsupported keyword to throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("pattern_"))
        }
    }
}
