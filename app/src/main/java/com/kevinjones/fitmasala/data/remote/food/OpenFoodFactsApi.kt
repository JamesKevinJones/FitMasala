package com.kevinjones.fitmasala.data.remote.food

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Open Food Facts product lookup - the one network call the app makes that is
 * not the user's own LLM request (DECISIONS 2026-09-28). It sends a barcode and
 * nothing else: no key, no account, no meal data.
 *
 * Shape from the official SDKs (Python `openfoodfacts` 5.3.0, Node
 * `@openfoodfacts/openfoodfacts-nodejs`): `GET /api/v2/product/{code}`, 404 or
 * `status: 0` when there is no such product, the product under `product`.
 */
interface OpenFoodFactsApi {

    /** `fields` is sent unescaped: the server does not read `%2C` as a comma. */
    @GET("api/v2/product/{code}")
    suspend fun product(
        @Path("code") code: String,
        @Query("fields", encoded = true) fields: String = FIELDS,
    ): Response<OffProductResponse>

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org/"

        /** Only what a log entry needs - a full product is tens of kilobytes. */
        const val FIELDS = "code,product_name,brands,serving_size,serving_quantity,serving_quantity_unit,nutriments"
    }
}
