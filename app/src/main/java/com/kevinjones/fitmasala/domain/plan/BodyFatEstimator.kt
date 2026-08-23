package com.kevinjones.fitmasala.domain.plan

import kotlin.math.log10

/**
 * US Navy circumference method. Chosen because it needs a tape and nothing else,
 * and because its error is *consistent* — roughly ±3% absolute against DEXA, but
 * biased in the same direction for the same person every time.
 *
 * That consistency is the point. For a cut, the derivative matters more than the
 * absolute: a tape that always reads 2% high still shows the fat coming off at
 * the right rate, and the plan is driven by the trend either way.
 */
object BodyFatEstimator {

    /**
     * @return body fat percent, or null when the measurements are impossible
     *   (waist at or below neck makes the logarithm undefined — a typo, not a body).
     */
    fun navy(sex: Sex, m: TapeMeasurements): Double? {
        if (m.heightCm <= 0 || m.neckCm <= 0 || m.waistCm <= 0) return null

        val raw = when (sex) {
            Sex.MALE -> {
                val girth = m.waistCm - m.neckCm
                if (girth <= 0) return null
                495.0 / (1.0324 - 0.19077 * log10(girth) + 0.15456 * log10(m.heightCm)) - 450.0
            }
            Sex.FEMALE -> {
                val hip = m.hipCm ?: return null
                val girth = m.waistCm + hip - m.neckCm
                if (girth <= 0) return null
                495.0 / (1.29579 - 0.35004 * log10(girth) + 0.22100 * log10(m.heightCm)) - 450.0
            }
        }
        return if (raw.isFinite() && raw in 1.0..70.0) raw else null
    }

    /** Mifflin–St Jeor. The starting guess only, until real data replaces it. */
    fun basalMetabolicRate(sex: Sex, weightKg: Double, heightCm: Double, ageYears: Int): Double =
        when (sex) {
            Sex.MALE -> 10 * weightKg + 6.25 * heightCm - 5 * ageYears + 5
            Sex.FEMALE -> 10 * weightKg + 6.25 * heightCm - 5 * ageYears - 161
        }

    /**
     * Katch–McArdle, which keys off lean mass instead of total weight.
     *
     * Preferred once body fat is known, because Mifflin systematically
     * over-estimates for a lean, muscular person — exactly the person heading
     * for 12%. Using it there would build the whole plan on an inflated
     * maintenance number.
     */
    fun basalMetabolicRateFromLeanMass(leanMassKg: Double): Double =
        370.0 + 21.6 * leanMassKg
}
