package com.kevinjones.fitmasala.presentation.plan

import com.kevinjones.fitmasala.domain.plan.ActivityLevel
import com.kevinjones.fitmasala.domain.plan.BodyFatEstimator
import com.kevinjones.fitmasala.domain.plan.BodyFatSource
import com.kevinjones.fitmasala.domain.plan.CutAggression
import com.kevinjones.fitmasala.domain.plan.Sex
import com.kevinjones.fitmasala.domain.plan.TapeMeasurements

/**
 * The rules behind the two Plan forms, as plain Kotlin so they run as JVM tests.
 *
 * Fields hold the text as typed. Nothing is parsed until [validate], so a half-typed
 * "82." never fights the cursor, and each field gets its own message rather than
 * one generic "check your input".
 */
enum class PlanField { HEIGHT, AGE, WEIGHT, BODY_FAT, NECK, WAIST, HIP, GOAL }

/** Either the parsed value, or a message per field that stopped it. */
sealed interface FormResult<out T> {
    data class Valid<T>(val value: T) : FormResult<T>
    data class Invalid(val errors: Map<PlanField, String>) : FormResult<Nothing>
}

/** What the setup form hands to `PlanRepository.startCut`. */
data class CutSetup(
    val sex: Sex,
    val heightCm: Double,
    val ageYears: Int,
    val weightKg: Double,
    val bodyFatPercent: Double,
    val bodyFatSource: BodyFatSource,
    val tape: TapeMeasurements?,
    val activity: ActivityLevel,
    val aggression: CutAggression,
    val goalBodyFatPercent: Double,
)

data class CutSetupForm(
    val sex: Sex = Sex.MALE,
    val height: String = "",
    val age: String = "",
    val weight: String = "",
    /** A typed estimate (scale, DEXA, a guess). Wins over the tape when both are given. */
    val bodyFat: String = "",
    val neck: String = "",
    val waist: String = "",
    /** Only read for the female formula. */
    val hip: String = "",
    val activity: ActivityLevel = ActivityLevel.MODERATE,
    val aggression: CutAggression = CutAggression.STANDARD,
    val goal: String = "12",
) {
    /**
     * Body fat from the tape, as soon as enough of it is typed - shown live under
     * the tape fields so the number the plan will use is never a surprise.
     */
    val tapeBodyFat: Double?
        get() {
            val tape = tapeOrNull(sex, height.toDecimal(), neck, waist, hip) ?: return null
            return BodyFatEstimator.navy(sex, tape)
        }

    fun validate(): FormResult<CutSetup> {
        val errors = mutableMapOf<PlanField, String>()

        val height = height.toDecimal()
        if (height == null || height !in HEIGHT_RANGE) errors[PlanField.HEIGHT] = "Height in cm, 120 to 230"

        val age = age.trim().toIntOrNull()
        if (age == null || age !in AGE_RANGE) errors[PlanField.AGE] = "Age in years, 16 to 90"

        val weight = weight.toDecimal()
        if (weight == null || weight !in WEIGHT_RANGE) errors[PlanField.WEIGHT] = "Weight in kg, 30 to 300"

        val typed = bodyFat.toDecimal()
        val tape = if (height != null) tapeOrNull(sex, height, neck, waist, hip) else null
        val tapeResult = tape?.let { BodyFatEstimator.navy(sex, it) }

        val start: Double?
        val source: BodyFatSource
        when {
            bodyFat.isNotBlank() -> {
                if (typed == null || typed !in BODY_FAT_RANGE) {
                    errors[PlanField.BODY_FAT] = "Body fat in %, 3 to 60"
                }
                start = typed
                source = BodyFatSource.MANUAL_ENTRY
            }
            tape != null && tapeResult == null -> {
                errors[PlanField.WAIST] = "Those measurements don't give a body fat - check waist and neck"
                start = null
                source = BodyFatSource.NAVY_TAPE
            }
            tapeResult != null -> {
                start = tapeResult
                source = BodyFatSource.NAVY_TAPE
            }
            else -> {
                errors[PlanField.BODY_FAT] = if (sex == Sex.FEMALE) {
                    "Enter a body-fat estimate, or neck, waist and hip"
                } else {
                    "Enter a body-fat estimate, or neck and waist"
                }
                start = null
                source = BodyFatSource.ESTIMATED
            }
        }

        val goal = goal.toDecimal()
        when {
            goal == null || goal !in GOAL_RANGE -> errors[PlanField.GOAL] = "Goal in %, 5 to 40"
            start != null && goal >= start -> errors[PlanField.GOAL] = "The goal has to be below where you start"
        }

        if (errors.isNotEmpty()) return FormResult.Invalid(errors)
        return FormResult.Valid(
            CutSetup(
                sex = sex,
                heightCm = height!!,
                ageYears = age!!,
                weightKg = weight!!,
                bodyFatPercent = start!!,
                bodyFatSource = source,
                tape = tape,
                activity = activity,
                aggression = aggression,
                goalBodyFatPercent = goal!!,
            ),
        )
    }
}

/** One morning's reading. Tape is optional, but all-or-nothing when given. */
data class WeighIn(
    val weightKg: Double,
    val waistCm: Double?,
    val neckCm: Double?,
    val hipCm: Double?,
)

data class WeighInForm(
    val weight: String = "",
    val waist: String = "",
    val neck: String = "",
    val hip: String = "",
) {
    fun validate(sex: Sex): FormResult<WeighIn> {
        val errors = mutableMapOf<PlanField, String>()

        val weight = weight.toDecimal()
        if (weight == null || weight !in WEIGHT_RANGE) errors[PlanField.WEIGHT] = "Weight in kg, 30 to 300"

        // A partial tape set is almost always a forgotten field, and the
        // repository would silently drop it (body fat needs the complete set).
        // Saying so beats storing a waist that never becomes a number.
        val needed = buildList {
            add(PlanField.WAIST to waist)
            add(PlanField.NECK to neck)
            if (sex == Sex.FEMALE) add(PlanField.HIP to hip)
        }
        val anyTape = needed.any { it.second.isNotBlank() }
        val parsed = needed.associate { (field, text) -> field to text.toDecimal() }
        if (anyTape) {
            needed.forEach { (field, text) ->
                val value = parsed[field]
                if (text.isBlank()) errors[field] = "Add this too, or clear the tape"
                else if (value == null || value !in TAPE_RANGE) errors[field] = "In cm, 20 to 200"
            }
        }

        if (errors.isNotEmpty()) return FormResult.Invalid(errors)
        return FormResult.Valid(
            WeighIn(
                weightKg = weight!!,
                waistCm = if (anyTape) parsed[PlanField.WAIST] else null,
                neckCm = if (anyTape) parsed[PlanField.NECK] else null,
                hipCm = if (anyTape && sex == Sex.FEMALE) parsed[PlanField.HIP] else null,
            ),
        )
    }
}

/** Week 1 is the week the cut started; null before a cut exists. */
fun cutWeek(startedAtDayEpoch: Long?, todayDayEpoch: Long): Int? =
    startedAtDayEpoch?.let { ((todayDayEpoch - it).coerceAtLeast(0) / 7 + 1).toInt() }

private fun tapeOrNull(sex: Sex, heightCm: Double?, neck: String, waist: String, hip: String): TapeMeasurements? {
    val h = heightCm ?: return null
    val n = neck.toDecimal() ?: return null
    val w = waist.toDecimal() ?: return null
    val hp = if (sex == Sex.FEMALE) hip.toDecimal() ?: return null else null
    return TapeMeasurements(heightCm = h, neckCm = n, waistCm = w, hipCm = hp)
}

/** Accepts "82,5" as well as "82.5" - the keyboard decides which one the user gets. */
internal fun String.toDecimal(): Double? =
    trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

private val HEIGHT_RANGE = 120.0..230.0
private val AGE_RANGE = 16..90
private val WEIGHT_RANGE = 30.0..300.0
private val BODY_FAT_RANGE = 3.0..60.0
private val GOAL_RANGE = 5.0..40.0
private val TAPE_RANGE = 20.0..200.0
