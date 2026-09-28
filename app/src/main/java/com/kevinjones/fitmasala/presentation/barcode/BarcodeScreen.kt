package com.kevinjones.fitmasala.presentation.barcode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.kevinjones.fitmasala.core.ui.components.FmButton
import com.kevinjones.fitmasala.core.ui.components.FmButtonGhost
import com.kevinjones.fitmasala.core.ui.components.FmButtonTonal
import com.kevinjones.fitmasala.core.ui.components.FmCard
import com.kevinjones.fitmasala.core.ui.components.FmChip
import com.kevinjones.fitmasala.core.ui.components.FmEmptyState
import com.kevinjones.fitmasala.core.ui.components.FmErrorState
import com.kevinjones.fitmasala.core.ui.components.FmSegmentedButtons
import com.kevinjones.fitmasala.core.ui.components.FmSkeletonCard
import com.kevinjones.fitmasala.core.ui.components.FmStat
import com.kevinjones.fitmasala.core.ui.components.FmStatRow
import com.kevinjones.fitmasala.core.ui.components.FmStepper
import com.kevinjones.fitmasala.core.ui.components.FmTextField
import com.kevinjones.fitmasala.core.ui.components.SectionHeader
import com.kevinjones.fitmasala.core.ui.components.displayName
import com.kevinjones.fitmasala.core.ui.theme.Fm
import com.kevinjones.fitmasala.core.ui.theme.MaxContentWidth
import com.kevinjones.fitmasala.core.ui.theme.fm
import com.kevinjones.fitmasala.data.local.entity.MealType
import com.kevinjones.fitmasala.data.local.entity.PortionUnit
import com.kevinjones.fitmasala.presentation.snap.label
import com.kevinjones.fitmasala.presentation.snap.trimmed
import kotlin.math.roundToInt

/**
 * "Scan a barcode": a packaged food's label from Open Food Facts, logged as
 * label values - not an Estimate. Google's code scanner reads the barcode (no
 * camera permission; Play services owns the camera), and the digits can always
 * be typed instead. When the product isn't known, the user is sent to "Log a
 * meal" to type the label.
 */
@Composable
fun BarcodeScreen(
    contentPadding: PaddingValues,
    onClose: () -> Unit,
    onLogged: (summary: String) -> Unit,
    onTypeLabel: () -> Unit,
    viewModel: BarcodeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val scan: () -> Unit = {
        val options = GmsBarcodeScannerOptions.Builder()
            // Retail product codes only: a QR code is never a food.
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
            )
            .build()
        GmsBarcodeScanning.getClient(context, options)
            .startScan()
            .addOnSuccessListener { barcode -> barcode.rawValue?.let(viewModel::lookup) }
            // Cancelling is not an error: the screen just stays where it was.
            .addOnFailureListener { viewModel.scannerUnavailable() }
    }

    LaunchedEffect(state) {
        (state as? BarcodeState.Logged)?.let { onLogged(it.summary) }
    }

    val itemModifier = Modifier.fillMaxWidth().widthIn(max = MaxContentWidth)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Fm.gap),
    ) {
        when (val current = state) {
            is BarcodeState.Ready -> item {
                ReadyCard(
                    ready = current,
                    modifier = itemModifier,
                    onScan = scan,
                    onTyped = viewModel::setTyped,
                    onLookUp = { viewModel.lookup(current.typed) },
                    onClose = onClose,
                )
            }

            is BarcodeState.LookingUp -> {
                item {
                    Column(itemModifier, verticalArrangement = Arrangement.spacedBy(Fm.hair)) {
                        Text("Looking up ${current.barcode}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Asking Open Food Facts for the label. Only the barcode is sent.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.fm.textSecondary,
                        )
                    }
                }
                item { FmSkeletonCard(itemModifier) }
            }

            is BarcodeState.Found -> item {
                FoundCard(
                    found = current,
                    modifier = itemModifier,
                    onUnit = viewModel::setUnit,
                    onStepUp = viewModel::stepUp,
                    onStepDown = viewModel::stepDown,
                    onMealType = viewModel::setMealType,
                    onLog = viewModel::log,
                    onScanAnother = viewModel::startOver,
                )
            }

            is BarcodeState.NeedsLabel -> item {
                Column(itemModifier, verticalArrangement = Arrangement.spacedBy(Fm.snug)) {
                    FmErrorState(title = "No label for this barcode", body = current.message)
                    FmButton("Type the label", onClick = onTypeLabel, modifier = Modifier.fillMaxWidth())
                    FmButtonGhost("Scan another", onClick = viewModel::startOver, modifier = Modifier.fillMaxWidth())
                }
            }

            is BarcodeState.Failed -> item {
                Column(itemModifier, verticalArrangement = Arrangement.spacedBy(Fm.snug)) {
                    FmErrorState(
                        title = "Couldn't look that up",
                        body = current.message,
                        onRetry = { viewModel.lookup(current.barcode) },
                    )
                    FmButtonGhost("Type the label", onClick = onTypeLabel, modifier = Modifier.fillMaxWidth())
                }
            }

            is BarcodeState.Logged -> Unit
        }
    }
}

@Composable
private fun ReadyCard(
    ready: BarcodeState.Ready,
    modifier: Modifier,
    onScan: () -> Unit,
    onTyped: (String) -> Unit,
    onLookUp: () -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Fm.gap)) {
        FmEmptyState(
            icon = Icons.Outlined.QrCodeScanner,
            title = "Scan a packet",
            body = "Point at the barcode on a packet or bottle. The label comes from Open Food " +
                "Facts and is logged as the label states it, not as an estimate.",
            action = { FmButton("Scan barcode", onClick = onScan, modifier = Modifier.fillMaxWidth()) },
        )
        Column(verticalArrangement = Arrangement.spacedBy(Fm.tight)) {
            SectionHeader("Or type the number under it")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Fm.tight)) {
                FmTextField(
                    value = ready.typed,
                    onValueChange = onTyped,
                    modifier = Modifier.weight(1f),
                    placeholder = "8901063093063",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                )
                FmButtonTonal("Look up", onClick = onLookUp, enabled = ready.typed.isNotEmpty())
            }
            if (ready.error != null) {
                Text(ready.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        FmButtonGhost("Cancel", onClick = onClose, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * The label and the portion. Servings when the label has one, else grams or ml;
 * the macros shown are for the portion chosen, computed from the per-100 values.
 */
@Composable
private fun FoundCard(
    found: BarcodeState.Found,
    modifier: Modifier,
    onUnit: (PortionUnit) -> Unit,
    onStepUp: () -> Unit,
    onStepDown: () -> Unit,
    onMealType: (MealType) -> Unit,
    onLog: () -> Unit,
    onScanAnother: () -> Unit,
) {
    val portion = found.portion
    val food = portion.food
    val macros = portion.macros
    val enabled = !found.saving

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Fm.gap)) {
        FmCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Fm.snug)) {
            Column(verticalArrangement = Arrangement.spacedBy(Fm.hair)) {
                Text(food.name, style = MaterialTheme.typography.titleMedium)
                val source = listOfNotNull(food.brand, "Label from Open Food Facts").joinToString(" · ")
                Text(source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.fm.textSecondary)
            }
            FmStatRow {
                FmStat("${macros.calories.roundToInt()}", "kcal")
                FmStat("${macros.proteinG.roundToInt()} g", "protein", tint = MaterialTheme.fm.macroProtein)
                FmStat("${macros.carbsG.roundToInt()} g", "carbs", tint = MaterialTheme.fm.macroCarbs)
                FmStat("${macros.fatG.roundToInt()} g", "fat", tint = MaterialTheme.fm.macroFat)
            }
            val per = if (food.baseUnit == PortionUnit.MILLILITRES) "100 ml" else "100 g"
            Text(
                "Per $per: ${food.per100.calories.roundToInt()} kcal" +
                    (food.servingLabel?.let { " · a serving is $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.fm.textSecondary,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Fm.tight)) {
            SectionHeader("How much")
            if (portion.canUseServings) {
                Row(horizontalArrangement = Arrangement.spacedBy(Fm.tight)) {
                    listOf(PortionUnit.SERVING, food.baseUnit).forEach { unit ->
                        FmChip(
                            text = if (unit == PortionUnit.SERVING) "Servings" else unit.displayName(),
                            selected = portion.unit == unit,
                            onClick = { if (enabled) onUnit(unit) },
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FmStepper(
                    value = portion.quantity.trimmed(),
                    onIncrement = { if (enabled) onStepUp() },
                    onDecrement = { if (enabled) onStepDown() },
                    incrementDescription = "Increase portion",
                    decrementDescription = "Decrease portion",
                    decrementEnabled = enabled && portion.canStepDown,
                )
                Text(
                    text = portion.label.substringAfter(' '),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.fm.textSecondary,
                    modifier = Modifier.padding(start = Fm.tight),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Fm.tight)) {
            SectionHeader("Which meal was this?")
            FmSegmentedButtons(
                options = MealType.entries,
                selected = found.mealType,
                onSelect = { if (enabled) onMealType(it) },
                label = { it.label() },
            )
        }

        if (found.saveError != null) {
            Text(found.saveError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Column(verticalArrangement = Arrangement.spacedBy(Fm.snug)) {
            FmButton(
                text = if (found.saving) "Logging…" else "Log ${macros.calories.roundToInt()} kcal",
                onClick = onLog,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            FmButtonGhost("Scan another", onClick = onScanAnother, modifier = Modifier.fillMaxWidth())
        }
    }
}
