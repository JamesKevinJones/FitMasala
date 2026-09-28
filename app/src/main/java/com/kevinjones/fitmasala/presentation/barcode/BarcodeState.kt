package com.kevinjones.fitmasala.presentation.barcode

import com.kevinjones.fitmasala.core.util.mealTypeAt
import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.data.remote.food.LookupResult
import java.time.ZoneId

/** Where "Scan a barcode" is. Each lookup outcome gets its own screen, as with the photo flow. */
sealed interface BarcodeState {
    /** Ready to scan, or to type the digits under the barcode. */
    data class Ready(val typed: String = "", val error: String? = null) : BarcodeState

    data class LookingUp(val barcode: String) : BarcodeState

    /** The label, a portion to log, and which meal it was. Eaten now. */
    data class Found(
        val portion: PackagedPortion,
        val eatenAt: Long,
        val mealType: MealType,
        val saving: Boolean = false,
        val saveError: String? = null,
    ) : BarcodeState {
        fun withPortion(change: (PackagedPortion) -> PackagedPortion): Found =
            if (saving) this else copy(portion = change(portion))

        fun withMealType(chosen: MealType): Found = if (saving) this else copy(mealType = chosen)
    }

    /** Not on Open Food Facts, or no calories there: the label has to be typed. */
    data class NeedsLabel(val barcode: String, val message: String) : BarcodeState

    /** Offline or a server error: the same barcode can be tried again. */
    data class Failed(val barcode: String, val message: String) : BarcodeState

    data class Logged(val summary: String) : BarcodeState

    companion object {
        /** What a lookup's answer becomes on screen. */
        fun after(
            barcode: String,
            result: LookupResult,
            now: Long,
            zone: ZoneId = ZoneId.systemDefault(),
        ): BarcodeState = when (result) {
            is LookupResult.Found -> Found(
                portion = PackagedPortion.startingFor(result.food),
                eatenAt = now,
                mealType = mealTypeAt(now, zone),
            )
            LookupResult.NotFound -> NeedsLabel(
                barcode,
                "Open Food Facts doesn't know barcode $barcode. Type the numbers from the label instead.",
            )
            is LookupResult.NoNutrition -> NeedsLabel(
                barcode,
                (result.name ?: "This product") + " is on Open Food Facts, but without calories. " +
                    "Type the numbers from the label instead.",
            )
            is LookupResult.Failed -> Failed(barcode, result.message)
        }
    }
}
