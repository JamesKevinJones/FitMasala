package com.kevinjones.fitmasala.core.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kevinjones.fitmasala.core.ui.theme.Fm
import com.kevinjones.fitmasala.core.ui.theme.FmRadius
import com.kevinjones.fitmasala.core.ui.theme.fm
import com.kevinjones.fitmasala.data.local.entity.PortionUnit

/**
 * Portion picker in Indian units.
 *
 * This is the component the app should be recognised by, and nothing borrowed
 * from a delivery kit or a Western tracker would have produced it. Every other
 * calorie app asks for grams. Nobody weighs a katori of dal - they think "one
 * katori", "two rotis", "half a plate", and forcing a conversion into grams at
 * the point of logging is exactly the friction that makes people stop logging.
 *
 * The unit row and the count stepper together answer the question the way it is
 * actually held in the head. Grams remain available for anyone with a scale,
 * last in the list rather than first.
 *
 * The live macro readout is the load-bearing part: it turns portion choice from
 * data entry into a DECISION. Seeing the third roti cost 120 kcal before
 * committing is the whole value.
 */
@Composable
fun FmPortionPicker(
    count: Int,
    unit: PortionUnit,
    onCountChange: (Int) -> Unit,
    onUnitChange: (PortionUnit) -> Unit,
    kcalPerUnit: Double,
    proteinPerUnit: Double,
    modifier: Modifier = Modifier,
    units: List<PortionUnit> = DefaultIndianUnits,
) {
    Column(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(Fm.snug),
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Fm.tight),
            contentPadding = PaddingValues(end = Fm.tight),
        ) {
            items(units, key = { it.name }) { candidate ->
                FmChip(
                    text = candidate.displayName(),
                    selected = candidate == unit,
                    onClick = { onUnitChange(candidate) },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "${(kcalPerUnit * count).toInt()} kcal",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "${(proteinPerUnit * count).toInt()}g protein · " +
                        "${kcalPerUnit.toInt()} kcal per ${unit.displayName().lowercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.fm.textSecondary,
                )
            }
            FmQuickAdd(
                count = count,
                onIncrement = { onCountChange(count + 1) },
                onDecrement = { onCountChange((count - 1).coerceAtLeast(0)) },
                label = unit.displayName().lowercase(),
            )
        }
    }
}

/**
 * Ordered by how often they are actually used at a home dining table, not
 * alphabetically and not by the database enum order. Grams last: available, not
 * suggested.
 */
val DefaultIndianUnits = listOf(
    PortionUnit.KATORI,
    PortionUnit.ROTI,
    PortionUnit.PIECE,
    PortionUnit.PLATE,
    PortionUnit.GLASS,
    PortionUnit.TABLESPOON,
    PortionUnit.GRAMS,
)

fun PortionUnit.displayName(): String = when (this) {
    PortionUnit.KATORI -> "Katori"
    PortionUnit.ROTI -> "Roti"
    PortionUnit.PIECE -> "Piece"
    PortionUnit.PLATE -> "Plate"
    PortionUnit.GLASS -> "Glass"
    PortionUnit.TABLESPOON -> "Tbsp"
    PortionUnit.GRAMS -> "Grams"
    PortionUnit.MILLILITRES -> "ml"
    PortionUnit.SERVING -> "Serving"
}

/** Approximate gram weights, shown as a hint rather than used for maths. */
fun PortionUnit.approximateGrams(): Int? = when (this) {
    PortionUnit.KATORI -> 180
    PortionUnit.ROTI -> 45
    PortionUnit.PLATE -> 350
    PortionUnit.GLASS -> 200
    PortionUnit.TABLESPOON -> 15
    else -> null
}
