package com.kevinjones.fitmasala.remote

import com.kevinjones.fitmasala.data.local.entity.CookingMethod
import com.kevinjones.fitmasala.data.local.entity.MealSource
import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.data.local.entity.PortionUnit
import com.kevinjones.fitmasala.data.local.entity.Region
import com.kevinjones.fitmasala.data.remote.dto.AnthropicResponse
import com.kevinjones.fitmasala.data.remote.dto.PhotoEstimateDto
import com.kevinjones.fitmasala.data.remote.dto.RecipeDto
import com.kevinjones.fitmasala.data.remote.dto.ResponseContentBlock
import com.kevinjones.fitmasala.data.remote.prompt.RecipeSchemas
import com.kevinjones.fitmasala.data.remote.toEntity
import com.kevinjones.fitmasala.data.remote.toIngredientEntities
import com.kevinjones.fitmasala.data.remote.toLoggedMeal
import com.kevinjones.fitmasala.data.remote.toLoggedMeals
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the API wire format, the DTOs and the entity mappers all agree -
 * without a network call or an API key.
 *
 * This is the chain that cannot be checked by reading: the schema lives in
 * RecipeSchemas, the parse target in CulinaryDtos, the storage shape in the
 * entities. Three files that must line up, written in three different languages
 * of description. A fixture is the only cheap way to catch drift between them.
 */
class ResponseParsingTest {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        serializersModule = SerializersModule {
            polymorphicDefaultDeserializer(ResponseContentBlock::class) {
                ResponseContentBlock.Unknown.serializer()
            }
        }
    }

    /**
     * A realistic envelope: a thinking block (adaptive thinking is on), a block
     * type this build has never heard of, then the text. All three occur.
     */
    private val envelope = """
    {
      "id": "msg_01ABC",
      "model": "claude-opus-5",
      "role": "assistant",
      "content": [
        {"type": "thinking", "thinking": "", "signature": "abc123"},
        {"type": "some_future_block_type", "whatever": {"nested": true}},
        {"type": "text", "text": "the answer"}
      ],
      "stop_reason": "end_turn",
      "usage": {"input_tokens": 1200, "output_tokens": 850}
    }
    """.trimIndent()

    @Test
    fun envelopeParsesAndUnknownBlockTypesDoNotCrashIt() {
        val response = json.decodeFromString<AnthropicResponse>(envelope)

        assertEquals("claude-opus-5", response.model)
        assertEquals(3, response.content.size)
        // The unknown block must degrade, not throw - otherwise a new API block
        // type ships a crash to every installed copy of the app.
        assertTrue(response.content[1] is ResponseContentBlock.Unknown)
        // Only text is surfaced; thinking and unknown blocks are skipped.
        assertEquals("the answer", response.text)
        assertEquals(850, response.usage?.outputTokens)
    }

    @Test
    fun refusalIsDetectedBeforeAnyContentIsTrusted() {
        val refused = """
        {
          "id": "msg_02", "model": "claude-opus-5",
          "content": [{"type": "text", "text": "partial"}],
          "stop_reason": "refusal",
          "stop_details": {"type": "refusal", "category": "cyber", "explanation": "declined"}
        }
        """.trimIndent()

        val response = json.decodeFromString<AnthropicResponse>(refused)
        assertTrue(response.wasRefused)
        assertEquals("cyber", response.stopDetails?.category)
    }

    @Test
    fun truncationIsDistinguishedFromASuccessfulReply() {
        val cut = """
        {"id":"m","model":"claude-opus-5","content":[{"type":"text","text":"half"}],
         "stop_reason":"max_tokens"}
        """.trimIndent()

        val response = json.decodeFromString<AnthropicResponse>(cut)
        assertTrue(response.wasTruncated)
        // stop_details is null for everything except a refusal - guard, never assume.
        assertNull(response.stopDetails)
    }

    /** A realistic structured-output payload, matching RecipeSchemas.RECIPE. */
    private val recipeJson = """
    {
      "title": "Punjabi Rajma Masala",
      "titleLocal": "Rajma Masala",
      "region": "PUNJABI",
      "provenanceNote": "Slow-simmered with onion-tomato masala and no cream.",
      "cookingMethod": "PRESSURE_COOKED",
      "servings": 4,
      "prepMinutes": 15,
      "cookMinutes": 45,
      "ingredients": [
        {"name":"Kidney beans","nameLocal":"rajma","quantity":200,"unit":"g",
         "preparationNote":"soaked overnight","gramsDry":200,"gramsCooked":520,"isOptional":false},
        {"name":"Ghee","nameLocal":"ghee","quantity":2,"unit":"tbsp",
         "preparationNote":"for the tadka","gramsDry":null,"gramsCooked":28,"isOptional":false}
      ],
      "instructions": ["Soak the rajma overnight.", "Pressure cook until soft."],
      "techniqueNotes": ["Rajma weights are DRY; 200g dry yields ~520g cooked.",
                         "2 tbsp ghee in the tadka is ~250 kcal across 4 servings."],
      "macrosPerServing": {"calories":310,"proteinG":13.5,"carbsG":41.0,"fatG":10.0,"fiberG":11.0},
      "portionDescription": "1 katori rajma (~180g) with 2 rotis"
    }
    """.trimIndent()

    @Test
    fun recipePayloadMapsAllTheWayIntoDatabaseEntities() {
        val dto = json.decodeFromString<RecipeDto>(recipeJson)
        val entity = dto.toEntity(rawJson = recipeJson, modelId = "claude-opus-5")

        assertEquals("Punjabi Rajma Masala", entity.title)
        assertEquals(Region.PUNJABI, entity.region)
        assertEquals(CookingMethod.PRESSURE_COOKED, entity.cookingMethod)
        assertEquals(2, entity.instructions.size)
        assertEquals(2, entity.techniqueNotes.size)
        assertEquals(310.0, entity.macrosPerServing.calories, 0.001)
        // rawResponse must survive so a better parser can re-read it later.
        assertNotNull(entity.rawResponse)

        val ingredients = dto.toIngredientEntities(recipeId = 7)
        assertEquals(2, ingredients.size)
        assertEquals(7L, ingredients[0].recipeId)
        assertEquals(0, ingredients[0].orderIndex)
        // Dry AND cooked must both survive - conflating them is the largest
        // available error in Indian macro estimation.
        assertEquals(200.0, ingredients[0].gramsDry!!, 0.001)
        assertEquals(520.0, ingredients[0].gramsCooked!!, 0.001)
        assertNull(ingredients[1].gramsDry)
    }

    @Test
    fun cookAndEatScalesMacrosAndCopiesThemOntoTheMeal() {
        val dto = json.decodeFromString<RecipeDto>(recipeJson)
        val meal = dto.toLoggedMeal(
            mealType = MealType.LUNCH,
            servingsEaten = 2.0,
            sourceRecipeId = 7L,
            eatenAt = 1_700_000_000_000L,
        )

        assertEquals(620.0, meal.macros.calories, 0.001)
        assertEquals(27.0, meal.macros.proteinG, 0.001)
        assertEquals(MealSource.AI_CHAT, meal.source)
        assertTrue(meal.isAiEstimate)
        assertEquals(7L, meal.sourceRecipeId)
        // dayEpoch is derived once at write time, never at read time.
        assertTrue(meal.dayEpoch > 19_000)
    }

    @Test
    fun anUnrecognisedRegionDegradesToOtherRatherThanThrowing() {
        // A model will eventually name a region the app has no constant for.
        val odd = recipeJson.replace("\"PUNJABI\"", "\"MALWA_HIGHLANDS\"")
        val entity = json.decodeFromString<RecipeDto>(odd).toEntity(null, null)

        assertEquals(Region.OTHER, entity.region)
        assertEquals("Punjabi Rajma Masala", entity.title)
    }

    @Test
    fun theFixtureItselfPassesTheAtwaterCheck() {
        val m = json.decodeFromString<RecipeDto>(recipeJson).macrosPerServing
        val derived = m.proteinG * 4 + m.carbsG * 4 + m.fatG * 9
        // 13.5*4 + 41*4 + 10*9 = 308 vs 310 stated.
        assertTrue(
            "fixture is itself inconsistent: $derived vs ${m.calories}",
            kotlin.math.abs(derived - m.calories) / m.calories < 0.15,
        )
    }

    /** A realistic photo estimate, matching RecipeSchemas.PHOTO_ESTIMATE. */
    private val photoJson = """
    {
      "containsFood": true,
      "items": [
        {"name":"Dal tadka","nameLocal":"dal","region":"PUNJABI",
         "portionEstimate":"1 katori (~180g)","portionQuantity":1,"portionUnit":"KATORI",
         "portionBasis":"katori rim against the plate",
         "macros":{"calories":240,"proteinG":14,"carbsG":30,"fatG":8,"fiberG":6},
         "confidence":"medium","uncertaintyNote":"Tadka ghee not visible."},
        {"name":"Phulka","nameLocal":"roti","region":"PUNJABI",
         "portionEstimate":"2 medium rotis","portionQuantity":2,"portionUnit":"ROTI",
         "portionBasis":"roti diameter against the plate",
         "macros":{"calories":240,"proteinG":8,"carbsG":44,"fatG":3,"fiberG":6},
         "confidence":"high","uncertaintyNote":"Unbuttered as far as visible."}
      ],
      "totalMacros":{"calories":480,"proteinG":22,"carbsG":74,"fatG":11,"fiberG":12},
      "overallConfidence":"medium"
    }
    """.trimIndent()

    @Test
    fun photoEstimateCarriesStructuredPortionsOntoEachDish() {
        val dishes = json.decodeFromString<PhotoEstimateDto>(photoJson)
            .toLoggedMeals(MealType.LUNCH, photoPath = "meal-photos/1.jpg", eatenAt = 1_700_000_000_000L)

        assertEquals(2, dishes.size)
        assertEquals(1.0, dishes[0].portionQuantity, 0.001)
        assertEquals(PortionUnit.KATORI, dishes[0].portionUnit)
        assertEquals(2.0, dishes[1].portionQuantity, 0.001)
        assertEquals(PortionUnit.ROTI, dishes[1].portionUnit)
        // The model's own wording survives next to the structured portion.
        assertEquals("2 medium rotis", dishes[1].portionNote)
        assertEquals(MealSource.PHOTO, dishes[1].source)
        assertTrue(dishes.all { it.isAiEstimate })
    }

    @Test
    fun anUnrecognisedPortionUnitFallsBackToOneServing() {
        // The schema constrains the unit, but a model is not a compiler.
        val odd = photoJson.replace("\"ROTI\"", "\"THALI_SECTION\"")
        val roti = json.decodeFromString<PhotoEstimateDto>(odd)
            .toLoggedMeals(MealType.LUNCH, photoPath = null)[1]

        assertEquals(PortionUnit.SERVING, roti.portionUnit)
        assertEquals(1.0, roti.portionQuantity, 0.001)
        // Macros still describe what was eaten; only the stepping is lost.
        assertEquals(240.0, roti.macros.calories, 0.001)
        assertEquals("2 medium rotis", roti.portionNote)
    }

    @Test
    fun aNonPositivePortionQuantityFallsBackToOneServing() {
        // Structured outputs cannot bound numbers, so 0 can arrive.
        val zero = photoJson.replace("\"portionQuantity\":2", "\"portionQuantity\":0")
        val roti = json.decodeFromString<PhotoEstimateDto>(zero)
            .toLoggedMeals(MealType.LUNCH, photoPath = null)[1]

        assertEquals(PortionUnit.SERVING, roti.portionUnit)
        assertEquals(1.0, roti.portionQuantity, 0.001)
    }

    /** Schema and DTO must change together; this is the tripwire for the photo pair. */
    @Test
    fun photoSchemaRequiresStructuredPortionsInRealUnits() {
        val item = RecipeSchemas.PHOTO_ESTIMATE["properties"]!!.jsonObject["items"]!!
            .jsonObject["items"]!!.jsonObject
        val required = item["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue("portionQuantity" in required)
        assertTrue("portionUnit" in required)

        val units = item["properties"]!!.jsonObject["portionUnit"]!!.jsonObject["enum"]!!
            .jsonArray.map { it.jsonPrimitive.content }
        // Every offered unit parses back to a PortionUnit, and SERVING - the
        // fallback for "no real unit" - is never offered.
        assertEquals(PortionUnit.entries.filter { it != PortionUnit.SERVING }.map { it.name }, units)
        assertTrue(units.first() == PortionUnit.KATORI.name)
    }
}
