package com.kevinjones.fitmasala.barcode

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.kevinjones.fitmasala.data.local.entity.PortionUnit
import com.kevinjones.fitmasala.data.remote.apiJson
import com.kevinjones.fitmasala.data.remote.food.FoodLookupClient
import com.kevinjones.fitmasala.data.remote.food.LookupResult
import com.kevinjones.fitmasala.data.remote.food.OpenFoodFactsApi
import com.kevinjones.fitmasala.data.remote.food.isPlausibleBarcode
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import java.io.IOException

/**
 * Barcode lookup through the real Retrofit service and JSON converter; only the
 * network is replaced, by an interceptor that records the request and answers
 * with a canned response.
 */
class FoodLookupTest {

    private class Wire(val code: Int = 200, val body: String = "{}") {
        var sent: Request? = null
        var failWith: IOException? = null
    }

    private fun client(wire: Wire): FoodLookupClient {
        val http = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                wire.sent = chain.request()
                wire.failWith?.let { throw it }
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(wire.code)
                    .message("canned")
                    .body(wire.body.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
        val api = Retrofit.Builder()
            .baseUrl(OpenFoodFactsApi.BASE_URL)
            .client(http)
            .addConverterFactory(apiJson().asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenFoodFactsApi::class.java)
        return FoodLookupClient(api)
    }

    private fun fixture(name: String) =
        requireNotNull(javaClass.getResource("/off/$name")) { "missing fixture $name" }.readText()

    @Test fun onlyTheBarcodeAndTheFieldListLeaveThePhone() = runTest {
        val wire = Wire(body = fixture("biscuit.json"))
        client(wire).lookup(" 8901063093063 ")
        val url = wire.sent!!.url.toString()
        assertEquals(
            "https://world.openfoodfacts.org/api/v2/product/8901063093063?fields=${OpenFoodFactsApi.FIELDS}",
            url,
        )
        assertFalse("commas stay unescaped", url.contains("%2C"))
        assertNull("no LLM key on a food lookup", wire.sent!!.header("x-api-key"))
        assertNull(wire.sent!!.header("x-goog-api-key"))
    }

    @Test fun aLabelIsReadPer100gWithItsServing() = runTest {
        val food = (client(Wire(body = fixture("biscuit.json"))).lookup("8901063093063") as LookupResult.Found).food
        assertEquals("Marie biscuits", food.name)
        assertEquals("the first brand only", "Example Foods", food.brand)
        assertEquals(440.0, food.per100.calories, 0.0)
        assertEquals(7.5, food.per100.proteinG, 0.0)
        assertEquals(77.0, food.per100.carbsG, 0.0)
        assertEquals(11.0, food.per100.fatG, 0.0)
        assertEquals("a numeric string still reads", 2.1, food.per100.fiberG!!, 1e-9)
        assertEquals(20.0, food.servingGrams!!, 0.0)
        assertEquals(PortionUnit.GRAMS, food.baseUnit)
        assertEquals("3 biscuits (20 g)", food.servingLabel)
    }

    @Test fun kilojoulesAreConvertedWhenKcalIsMissing() = runTest {
        val food = (client(Wire(body = fixture("kj-only.json"))).lookup("8901725121112") as LookupResult.Found).food
        assertEquals(1590 / 4.184, food.per100.calories, 1e-6)
        assertNull("no serving on the label", food.servingGrams)
        assertNull(food.servingLabel)
        assertNull(food.brand)
    }

    @Test fun aProductWithoutCaloriesAsksForTheLabel() = runTest {
        assertEquals(
            LookupResult.NoNutrition("Chakli"),
            client(Wire(body = fixture("no-nutrition.json"))).lookup("8901725121112"),
        )
    }

    @Test fun unknownBarcodesAreNotFound() = runTest {
        assertEquals(LookupResult.NotFound, client(Wire(code = 404, body = "{}")).lookup("12345678"))
        assertEquals(LookupResult.NotFound, client(Wire(body = fixture("status-zero.json"))).lookup("12345678"))
    }

    @Test fun aTypoNeverLeavesThePhone() = runTest {
        val wire = Wire(body = fixture("biscuit.json"))
        assertEquals(LookupResult.NotFound, client(wire).lookup("12ab5678"))
        assertEquals(LookupResult.NotFound, client(wire).lookup("1234"))
        assertNull(wire.sent)
    }

    @Test fun networkAndServerErrorsAreRetryable() = runTest {
        val offline = Wire().apply { failWith = IOException("no route") }
        assertTrue(client(offline).lookup("8901063093063") is LookupResult.Failed)
        assertTrue(client(Wire(code = 503, body = "busy")).lookup("8901063093063") is LookupResult.Failed)
    }

    @Test fun aDrinkLabelledInMillilitresStepsInMillilitres() = runTest {
        val drink = fixture("biscuit.json").replace("\"g\"", "\"ml\"")
        val food = (client(Wire(body = drink)).lookup("8901063093063") as LookupResult.Found).food
        assertEquals(PortionUnit.MILLILITRES, food.baseUnit)
    }

    @Test fun plausibleBarcodesAreEightToFourteenDigits() {
        assertTrue(isPlausibleBarcode("12345678"))
        assertTrue(isPlausibleBarcode("8901063093063"))
        assertTrue(isPlausibleBarcode("12345678901234"))
        assertFalse(isPlausibleBarcode("1234567"))
        assertFalse(isPlausibleBarcode("123456789012345"))
        assertFalse(isPlausibleBarcode("89010630930a3"))
    }
}
