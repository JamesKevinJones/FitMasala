package com.kevinjones.fitmasala.compare

import com.kevinjones.fitmasala.data.local.entity.PortionUnit
import com.kevinjones.fitmasala.data.remote.LlmResult
import com.kevinjones.fitmasala.data.remote.dto.PhotoEstimateDto
import com.kevinjones.fitmasala.data.remote.structuredPortion
import kotlin.math.abs

/**
 * The comparison run's arithmetic and its report (#23), kept apart from the
 * network so it is tested on every build, not only on the night of the run.
 */

/** One row of Kevin's known-values table. */
data class Known(
    val photo: String,
    val calories: Double,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val mealType: String?,
    val portions: List<KnownPortion>,
)

data class KnownPortion(val dish: String, val quantity: Double?, val unit: PortionUnit)

/**
 * The known-values table, as CSV with a header row:
 *
 * ```
 * photo,calories,protein_g,carbs_g,fat_g,meal_type,portions
 * thali.jpg,850,28,110,30,lunch,"Dal=1 KATORI; Roti=2 ROTI; Rice=1 KATORI"
 * ```
 *
 * Only `photo` and `calories` are required; blank macros are left out of the
 * comparison rather than read as zero. `portions` lists Dishes as
 * `name=quantity UNIT`, with UNIT a `PortionUnit` name.
 */
object KnownTable {

    fun parse(csv: String): List<Known> {
        val lines = csv.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() }.toList()
        require(lines.isNotEmpty()) { "the known-values table is empty" }
        val header = splitCsv(lines.first()).map { it.trim().lowercase() }
        fun column(name: String) = header.indexOf(name)
        val photo = column("photo").also { require(it >= 0) { "the table needs a 'photo' column" } }
        val calories = column("calories").also { require(it >= 0) { "the table needs a 'calories' column" } }

        return lines.drop(1).mapIndexed { index, line ->
            val cells = splitCsv(line)
            fun cell(i: Int) = cells.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }
            fun number(name: String) = cell(column(name))?.let {
                it.toDoubleOrNull() ?: error("row ${index + 2}: '$it' in $name is not a number")
            }
            Known(
                photo = cell(photo) ?: error("row ${index + 2} has no photo"),
                calories = cell(calories)?.toDoubleOrNull()?.takeIf { it > 0 }
                    ?: error("row ${index + 2} needs known calories above zero"),
                proteinG = number("protein_g"),
                carbsG = number("carbs_g"),
                fatG = number("fat_g"),
                mealType = cell(column("meal_type")),
                portions = cell(column("portions"))?.let(::parsePortions).orEmpty(),
            )
        }
    }

    internal fun parsePortions(raw: String): List<KnownPortion> =
        raw.split(';').map { it.trim() }.filter { it.isNotEmpty() }.map { entry ->
            val (dish, amount) = entry.split('=', limit = 2).map { it.trim() }
                .takeIf { it.size == 2 } ?: error("portion '$entry' should read name=quantity UNIT")
            val parts = amount.split(Regex("\\s+"))
            val unitName = parts.last()
            val unit = PortionUnit.entries.firstOrNull { it.name.equals(unitName, ignoreCase = true) }
                ?: error("'$unitName' is not a portion unit (${PortionUnit.entries.joinToString()})")
            KnownPortion(dish, parts.dropLast(1).singleOrNull()?.toDoubleOrNull(), unit)
        }

    /** Commas split cells; double quotes protect them; "" is a literal quote. */
    private fun splitCsv(line: String): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && line.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { cells += cell.toString(); cell.clear() }
                else -> cell.append(c)
            }
            i++
        }
        cells += cell.toString()
        return cells
    }
}

/** Dollars per million tokens. */
data class Price(val inputPerMTok: Double, val outputPerMTok: Double) {
    fun cost(inputTokens: Int, outputTokens: Int) =
        (inputTokens * inputPerMTok + outputTokens * outputPerMTok) / 1_000_000

    companion object {
        /** "in,out", e.g. "5,25". */
        fun parse(raw: String?): Price? = raw?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() }
            ?.takeIf { it.size == 2 }?.let { (i, o) -> Price(i, o) }
    }
}

/** One provider's answer for one photo. */
data class Row(
    val photo: String,
    val provider: String,
    val model: String?,
    val known: Known,
    /** Null when the call failed; see [failure]. */
    val estimated: Totals?,
    val failure: String?,
    val advisories: List<String>,
    val unitsMatched: Int,
    val unitsKnown: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val costUsd: Double?,
) {
    /** Signed, in percent of the known calories. */
    val calorieErrorPct: Double? get() = estimated?.let { (it.calories - known.calories) / known.calories * 100 }

    companion object {
        fun of(
            known: Known,
            provider: String,
            result: LlmResult<PhotoEstimateDto>,
            price: Price?,
        ): Row = when (result) {
            is LlmResult.Success -> {
                val (matched, total) = unitMatches(known.portions, result.value)
                Row(
                    photo = known.photo,
                    provider = provider,
                    model = result.model,
                    known = known,
                    estimated = Totals.of(result.value),
                    failure = null,
                    advisories = result.advisories,
                    unitsMatched = matched,
                    unitsKnown = total,
                    inputTokens = result.inputTokens,
                    outputTokens = result.outputTokens,
                    costUsd = price?.cost(result.inputTokens, result.outputTokens),
                )
            }
            is LlmResult.Failure -> Row(
                photo = known.photo, provider = provider, model = null, known = known,
                estimated = null, failure = "${result::class.simpleName}: ${result.message}",
                advisories = emptyList(), unitsMatched = 0, unitsKnown = known.portions.size,
                inputTokens = 0, outputTokens = 0, costUsd = null,
            )
        }

        /**
         * A known Dish matches when some estimated Dish's name contains it (or the
         * other way round, ignoring case) and carries the same unit. Unmatched
         * names count as misses: a missed Dish is a wrong answer too.
         */
        internal fun unitMatches(known: List<KnownPortion>, estimate: PhotoEstimateDto): Pair<Int, Int> {
            val matched = known.count { portion ->
                estimate.items.any { item ->
                    val a = item.name.lowercase()
                    val b = portion.dish.lowercase()
                    (a.contains(b) || b.contains(a)) && item.structuredPortion()?.second == portion.unit
                }
            }
            return matched to known.size
        }
    }
}

data class Totals(val calories: Double, val proteinG: Double, val carbsG: Double, val fatG: Double) {
    companion object {
        /**
         * The sum of the Dishes, as the review sheet shows and logs it - not the
         * model's stated total, which only feeds the mismatch advisory.
         */
        fun of(dto: PhotoEstimateDto): Totals =
            if (dto.items.isEmpty()) {
                with(dto.totalMacros) { Totals(calories, proteinG, carbsG, fatG) }
            } else {
                Totals(
                    dto.items.sumOf { it.macros.calories },
                    dto.items.sumOf { it.macros.proteinG },
                    dto.items.sumOf { it.macros.carbsG },
                    dto.items.sumOf { it.macros.fatG },
                )
            }
    }
}

/** One provider's headline numbers. */
data class Summary(
    val provider: String,
    val photos: Int,
    val answered: Int,
    val medianAbsCalorieErrorPct: Double?,
    val meanAbsCalorieErrorPct: Double?,
    val within20PctShare: Double?,
    val unitMatchShare: Double?,
    val costPerPhotoUsd: Double?,
) {
    companion object {
        fun of(provider: String, rows: List<Row>): Summary {
            val errors = rows.mapNotNull { it.calorieErrorPct?.let(::abs) }.sorted()
            val unitsKnown = rows.sumOf { it.unitsKnown }
            val costs = rows.map { it.costUsd }
            return Summary(
                provider = provider,
                photos = rows.size,
                answered = errors.size,
                medianAbsCalorieErrorPct = median(errors),
                meanAbsCalorieErrorPct = errors.takeIf { it.isNotEmpty() }?.average(),
                within20PctShare = errors.takeIf { it.isNotEmpty() }?.let { e -> e.count { it <= 20.0 }.toDouble() / e.size },
                unitMatchShare = if (unitsKnown == 0) null else rows.sumOf { it.unitsMatched }.toDouble() / unitsKnown,
                // Failed calls cost nothing here but count as photos, so a
                // provider that fails more does not look cheaper per answer.
                costPerPhotoUsd = if (rows.isEmpty() || rows.any { it.failure == null && it.costUsd == null }) null
                    else costs.sumOf { it ?: 0.0 } / rows.size,
            )
        }

        internal fun median(sorted: List<Double>): Double? = when {
            sorted.isEmpty() -> null
            sorted.size % 2 == 1 -> sorted[sorted.size / 2]
            else -> (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        }
    }
}

/**
 * The rule fixed in advance in #23 and DECISIONS 2026-09-27: Gemini becomes the
 * default only if its median absolute calorie error is no worse than Claude's
 * plus 5 percentage points.
 */
fun geminiMeetsTheRule(claude: Summary, gemini: Summary): Boolean? {
    val c = claude.medianAbsCalorieErrorPct ?: return null
    val g = gemini.medianAbsCalorieErrorPct ?: return null
    return g <= c + 5.0
}

object ReportWriter {

    fun markdown(rows: List<Row>, summaries: List<Summary>, verdict: Boolean?): String = buildString {
        appendLine("# Claude vs Gemini on known meals")
        appendLine()
        appendLine("Calorie error is (estimated - known) / known. The estimate is the sum of the Dishes, as the app logs it.")
        appendLine()
        appendLine("## Per photo")
        appendLine()
        appendLine("| Photo | Provider | Model | kcal known | kcal est | Error | P / C / F known | P / C / F est | Units | Advisories | Tokens in / out | Cost |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
        rows.forEach { r ->
            val known = r.known
            val est = r.estimated
            appendLine(
                listOf(
                    r.photo, r.provider, r.model ?: "-",
                    fmt(known.calories), est?.let { fmt(it.calories) } ?: "failed",
                    r.calorieErrorPct?.let { "%+.0f%%".format(it) } ?: (r.failure ?: "-"),
                    "${fmt(known.proteinG)} / ${fmt(known.carbsG)} / ${fmt(known.fatG)}",
                    est?.let { "${fmt(it.proteinG)} / ${fmt(it.carbsG)} / ${fmt(it.fatG)}" } ?: "-",
                    if (r.unitsKnown == 0) "-" else "${r.unitsMatched}/${r.unitsKnown}",
                    r.advisories.joinToString("<br>").ifEmpty { "-" },
                    "${r.inputTokens} / ${r.outputTokens}",
                    r.costUsd?.let { "$%.4f".format(it) } ?: "-",
                ).joinToString(" | ", "| ", " |") { it.replace("|", "\\|").replace("\n", " ") },
            )
        }
        appendLine()
        appendLine("## Per provider")
        appendLine()
        summaries.forEach { s ->
            appendLine("**${s.provider}** - ${s.answered} of ${s.photos} photos answered")
            appendLine()
            appendLine("- Median absolute calorie error: ${pct(s.medianAbsCalorieErrorPct)}")
            appendLine("- Mean absolute calorie error: ${pct(s.meanAbsCalorieErrorPct)}")
            appendLine("- Within ±20%: ${share(s.within20PctShare)}")
            appendLine("- Portion units matching: ${share(s.unitMatchShare)}")
            appendLine("- Cost per photo: ${s.costPerPhotoUsd?.let { "$%.4f".format(it) } ?: "not priced - set the provider's price"}")
            appendLine()
        }
        appendLine("## Decision rule")
        appendLine()
        appendLine(
            when (verdict) {
                true -> "Gemini's median error is within Claude's plus 5 points: the rule allows making Gemini the default."
                false -> "Gemini's median error is more than 5 points worse than Claude's: Claude stays the default."
                null -> "Not decidable: one provider answered no photos."
            },
        )
    }

    private fun fmt(value: Double?) = value?.let { "%.0f".format(it) } ?: "-"
    private fun pct(value: Double?) = value?.let { "%.1f%%".format(it) } ?: "-"
    private fun share(value: Double?) = value?.let { "%.0f%%".format(it * 100) } ?: "-"
}
