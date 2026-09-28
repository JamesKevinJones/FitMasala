package com.kevinjones.fitmasala.data.remote.food

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class OffProductResponse(
    /** 1 found, 0 not found or not a valid barcode. */
    val status: Int? = null,
    val product: OffProduct? = null,
)

/**
 * Crowd-sourced data, so every field is optional and numbers may arrive as
 * strings (the SDK types `serving_quantity` as a string): they are kept as raw
 * JSON and read leniently in [toPackagedFood].
 */
@Serializable
data class OffProduct(
    val code: String? = null,
    @SerialName("product_name") val productName: String? = null,
    val brands: String? = null,
    @SerialName("serving_size") val servingSize: String? = null,
    @SerialName("serving_quantity") val servingQuantity: JsonElement? = null,
    @SerialName("serving_quantity_unit") val servingQuantityUnit: String? = null,
    val nutriments: JsonObject? = null,
)
