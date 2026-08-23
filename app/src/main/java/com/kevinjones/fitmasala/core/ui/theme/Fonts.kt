package com.kevinjones.fitmasala.core.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.kevinjones.fitmasala.R

/**
 * DM Sans, bundled as a single VARIABLE font.
 *
 * Google Fonts no longer ships static instances of DM Sans - the family is one
 * 240KB file carrying an optical-size and a weight axis. Compose reaches the
 * weights through `FontVariation.Settings`, so five weights cost one file rather
 * than five, and any weight in between is available if a design ever needs it.
 *
 * Bundled rather than fetched through the downloadable-fonts provider on
 * purpose: a downloadable font needs Play Services, fails silently to a system
 * fallback when it is unavailable, and would mean the app's typography depends
 * on a network. Two hundred kilobytes is a fair price for it always being right.
 *
 * `FontVariation` requires API 26, which is this app's minSdk.
 *
 * Licence: SIL Open Font License 1.1, shipped at
 * `assets/licenses/dm-sans-OFL.txt`.
 */
@OptIn(ExperimentalTextApi::class)
private fun dmSans(weight: Int) = Font(
    resId = R.font.dm_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        // Optical size: DM Sans tightens spacing and thins strokes as this
        // rises. 14 is tuned for text, and the display styles override it.
        FontVariation.Setting("opsz", 14f),
    ),
)

@OptIn(ExperimentalTextApi::class)
private fun dmSansDisplay(weight: Int) = Font(
    resId = R.font.dm_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        // Larger optical size for headlines: slightly tighter, slightly finer,
        // which is what stops big type looking clumsy.
        FontVariation.Setting("opsz", 40f),
    ),
)

val DmSans = FontFamily(
    dmSans(400),
    dmSans(500),
    dmSans(600),
    dmSans(700),
    dmSans(800),
)

/** Used only for display and headline styles. */
val DmSansDisplay = FontFamily(
    dmSansDisplay(600),
    dmSansDisplay(700),
    dmSansDisplay(800),
)
