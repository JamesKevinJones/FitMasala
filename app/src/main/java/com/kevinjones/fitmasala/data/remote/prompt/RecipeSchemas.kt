package com.kevinjones.fitmasala.data.remote.prompt

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * JSON schemas passed as `output_config.format`.
 *
 * The schema is what makes the response parseable; the prompt is what makes the
 * contents correct. Both are needed and neither substitutes for the other.
 *
 * Every object sets `additionalProperties: false` and lists everything in
 * `required`. Optional-by-omission would let the model quietly drop the field it
 * was least sure about — which would be the interesting one. A field it cannot
 * determine should come back explicitly null, so the app can show a gap instead
 * of silently treating it as zero.
 */
object RecipeSchemas {

    private fun stringOrNull() = buildJsonObject {
        putJsonArray("type") { add("string"); add("null") }
    }

    private fun numberOrNull() = buildJsonObject {
        putJsonArray("type") { add("number"); add("null") }
    }

    private fun number(description: String) = buildJsonObject {
        put("type", "number")
        put("description", description)
    }

    private fun string(description: String) = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private fun macrosObject(description: String) = buildJsonObject {
        put("type", "object")
        put("description", description)
        putJsonObject("properties") {
            put("calories", number("kcal"))
            put("proteinG", number("grams of protein"))
            put("carbsG", number("grams of carbohydrate"))
            put("fatG", number("grams of fat"))
            put("fiberG", number("grams of fibre"))
        }
        putJsonArray("required") {
            add("calories"); add("proteinG"); add("carbsG"); add("fatG"); add("fiberG")
        }
        put("additionalProperties", false)
    }

    val RECIPE: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            put("title", string("Dish name in English"))
            put("titleLocal", stringOrNull())
            put(
                "region",
                buildJsonObject {
                    put("type", "string")
                    put("description", "One of the app's Region enum names, e.g. PUNJABI, BENGALI, CHETTINAD, SOUTH_INDIAN, OTHER")
                },
            )
            put("provenanceNote", string("One or two sentences on why this is the authentic regional version"))
            put(
                "cookingMethod",
                string("One of: DEEP_FRIED, SHALLOW_FRIED, TADKA, DRY_ROASTED, STEAMED, BOILED, PRESSURE_COOKED, TANDOOR, RAW, OTHER"),
            )
            put("servings", buildJsonObject { put("type", "integer") })
            put("prepMinutes", buildJsonObject { putJsonArray("type") { add("integer"); add("null") } })
            put("cookMinutes", buildJsonObject { putJsonArray("type") { add("integer"); add("null") } })

            putJsonObject("ingredients") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        put("name", string("Ingredient name in English"))
                        put("nameLocal", stringOrNull())
                        put("quantity", numberOrNull())
                        put("unit", stringOrNull())
                        put("preparationNote", stringOrNull())
                        // Both weights, so the app never has to guess which basis
                        // a number is on. Null the one that does not apply.
                        put("gramsDry", numberOrNull())
                        put("gramsCooked", numberOrNull())
                        put("isOptional", buildJsonObject { put("type", "boolean") })
                    }
                    putJsonArray("required") {
                        add("name"); add("nameLocal"); add("quantity"); add("unit")
                        add("preparationNote"); add("gramsDry"); add("gramsCooked"); add("isOptional")
                    }
                    put("additionalProperties", false)
                }
            }

            putJsonObject("instructions") {
                put("type", "array")
                put("description", "Ordered steps")
                putJsonObject("items") { put("type", "string") }
            }

            putJsonObject("techniqueNotes") {
                put("type", "array")
                put(
                    "description",
                    "Notes on anything that materially moves the macros: absorbed frying oil, ghee in the tadka, dry vs cooked basis, assumptions made",
                )
                putJsonObject("items") { put("type", "string") }
            }

            put("macrosPerServing", macrosObject("Per single serving, not per whole recipe"))
            put("portionDescription", string("What one serving is in Indian terms, e.g. '2 katori dal (~180g each) with 2 rotis'"))
        }
        putJsonArray("required") {
            add("title"); add("titleLocal"); add("region"); add("provenanceNote")
            add("cookingMethod"); add("servings"); add("prepMinutes"); add("cookMinutes")
            add("ingredients"); add("instructions"); add("techniqueNotes")
            add("macrosPerServing"); add("portionDescription")
        }
        put("additionalProperties", false)
    }

    val PHOTO_ESTIMATE: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            put("containsFood", buildJsonObject { put("type", "boolean") })
            putJsonObject("items") {
                put("type", "array")
                put("description", "One entry per distinct dish on the plate")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        put("name", string("Dish name"))
                        put("nameLocal", stringOrNull())
                        put("region", string("Region enum name, or OTHER"))
                        put("portionEstimate", string("e.g. '1 katori (~180g)', '2 rotis'"))
                        put("portionBasis", string("What the size was judged against, e.g. 'katori rim vs plate diameter'"))
                        put("macros", macrosObject("For the portion visible, not per 100g"))
                        put(
                            "confidence",
                            string("high, medium or low - be honest; most gravies are low"),
                        )
                        put("uncertaintyNote", string("What could not be seen and how much it could move the number"))
                    }
                    putJsonArray("required") {
                        add("name"); add("nameLocal"); add("region"); add("portionEstimate")
                        add("portionBasis"); add("macros"); add("confidence"); add("uncertaintyNote")
                    }
                    put("additionalProperties", false)
                }
            }
            put("totalMacros", macrosObject("Sum across all items"))
            put("overallConfidence", string("high, medium or low"))
        }
        putJsonArray("required") {
            add("containsFood"); add("items"); add("totalMacros"); add("overallConfidence")
        }
        put("additionalProperties", false)
    }
}
