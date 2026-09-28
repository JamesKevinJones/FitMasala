package com.kevinjones.fitmasala.data.remote.food

import com.kevinjones.fitmasala.data.local.entity.PortionUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A packaged food as its label states it, per 100 g (or 100 ml for a drink).
 * These are label values, not an Estimate: logged, they carry no estimate badge.
 */
data class PackagedFood(
    val barcode: String,
    val name: String,
    val brand: String?,
    val per100: LabelMacros,
    /** GRAMS, or MILLILITRES when the label's serving is in ml. */
    val baseUnit: PortionUnit,
    /** Grams (or ml, per [baseUnit]) in one serving, when the label gives one. */
    val servingGrams: Double?,
    /** The label's own words for a serving, e.g. "1 biscuit (12 g)". */
    val servingLabel: String?,
)

data class LabelMacros(
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double?,
)

/** Every way a lookup ends, each with its own UI - as `LlmResult` does for the models. */
sealed interface LookupResult {
    data class Found(val food: PackagedFood) : LookupResult

    /** Open Food Facts has no such barcode. */
    data object NotFound : LookupResult

    /** The product exists but has no calorie figure - the label must be typed. */
    data class NoNutrition(val name: String?) : LookupResult

    /** Offline, a timeout, or a server error. Retryable. */
    data class Failed(val message: String) : LookupResult
}

/** Digits only, EAN-8 to GTIN-14: anything else is a typo, not a product. */
fun isPlausibleBarcode(raw: String): Boolean = raw.trim().let { it.length in 8..14 && it.all(Char::isDigit) }

@Singleton
class FoodLookupClient @Inject constructor(
    private val api: OpenFoodFactsApi,
) {
    suspend fun lookup(barcode: String): LookupResult = withContext(Dispatchers.IO) {
        val code = barcode.trim()
        if (!isPlausibleBarcode(code)) return@withContext LookupResult.NotFound
        val response = try {
            api.product(code)
        } catch (e: IOException) {
            return@withContext LookupResult.Failed("Couldn't reach Open Food Facts. Check your connection and try again.")
        } catch (e: kotlinx.serialization.SerializationException) {
            return@withContext LookupResult.Failed("Open Food Facts sent something unreadable. Try again later.")
        }
        when {
            response.code() == 404 -> LookupResult.NotFound
            !response.isSuccessful -> LookupResult.Failed("Open Food Facts answered ${response.code()}. Try again later.")
            else -> {
                val body = response.body()
                val product = body?.product
                if (body?.status == 0 || product == null) {
                    LookupResult.NotFound
                } else {
                    product.toPackagedFood(code)?.let { LookupResult.Found(it) }
                        ?: LookupResult.NoNutrition(product.displayName())
                }
            }
        }
    }
}

/**
 * Null when there is no usable calorie figure. kcal comes from `energy-kcal_100g`,
 * else from kJ (`energy-kj_100g`, or `energy_100g`, which Open Food Facts keeps
 * in kJ) at 4.184 kJ per kcal. Missing macros read as 0 - a label with only
 * calories is still worth logging.
 */
internal fun OffProduct.toPackagedFood(barcode: String): PackagedFood? {
    val n = nutriments ?: return null
    fun value(key: String): Double? = n[key]?.number()?.takeIf { it >= 0 }
    val kcal = value("energy-kcal_100g")
        ?: (value("energy-kj_100g") ?: value("energy_100g"))?.div(KJ_PER_KCAL)
        ?: return null
    if (kcal <= 0) return null
    val serving = servingQuantity?.number()?.takeIf { it > 0 }
    return PackagedFood(
        barcode = barcode,
        name = displayName() ?: "Packaged food",
        brand = brands?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
        per100 = LabelMacros(
            calories = kcal,
            proteinG = value("proteins_100g") ?: 0.0,
            carbsG = value("carbohydrates_100g") ?: 0.0,
            fatG = value("fat_100g") ?: 0.0,
            fiberG = value("fiber_100g"),
        ),
        baseUnit = if (servingQuantityUnit?.trim().equals("ml", ignoreCase = true)) {
            PortionUnit.MILLILITRES
        } else {
            PortionUnit.GRAMS
        },
        servingGrams = serving,
        servingLabel = servingSize?.trim()?.takeIf { it.isNotEmpty() && serving != null },
    )
}

private fun OffProduct.displayName(): String? = productName?.trim()?.takeIf { it.isNotEmpty() }

/** A number, or a numeric string ("30", "30.5", "30,5"). */
private fun JsonElement.number(): Double? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.content.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
}

private const val KJ_PER_KCAL = 4.184
