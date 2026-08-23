package com.kevinjones.fitmasala.domain.plan

import kotlin.math.abs

/** One scale reading. */
data class WeightEntry(val dayEpoch: Long, val weightKg: Double)

/**
 * Exponentially-weighted trend over daily weights.
 *
 * A single morning weigh-in is mostly noise: sodium, carbs, water retention and
 * gut content swing a person 1–2kg day to day, which is larger than a whole
 * week's actual fat loss. Reacting to raw readings is how people conclude a
 * working plan has stalled and abandon it on day four.
 *
 * Alpha 0.1 gives roughly a 10-day half-life — slow enough to ignore a salty
 * meal, fast enough to notice a genuine plateau inside two weeks.
 */
object WeightTrend {

    const val DEFAULT_ALPHA = 0.1

    /** Returns the smoothed series, one point per input entry, oldest first. */
    fun smooth(entries: List<WeightEntry>, alpha: Double = DEFAULT_ALPHA): List<WeightEntry> {
        if (entries.isEmpty()) return emptyList()
        val sorted = entries.sortedBy { it.dayEpoch }
        var ema = sorted.first().weightKg
        return sorted.map { entry ->
            ema += alpha * (entry.weightKg - ema)
            entry.copy(weightKg = ema)
        }
    }

    fun currentTrendWeight(entries: List<WeightEntry>, alpha: Double = DEFAULT_ALPHA): Double? =
        smooth(entries, alpha).lastOrNull()?.weightKg

    /**
     * Rate of change in kg/week, from a least-squares fit over the smoothed
     * series rather than first-vs-last.
     *
     * First-vs-last would let one bloated morning at either end of the window
     * define the entire slope. The fit uses every point.
     */
    fun weeklyChangeKg(
        entries: List<WeightEntry>,
        windowDays: Int = 21,
    ): Double? {
        if (entries.size < 2) return null

        val cutoff = entries.last().dayEpoch - windowDays
        val window = entries.filter { it.dayEpoch >= cutoff }
        if (window.size < 2) return null

        val n = window.size
        val meanX = window.sumOf { it.dayEpoch.toDouble() } / n
        val meanY = window.sumOf { it.weightKg } / n
        var num = 0.0
        var den = 0.0
        window.forEach { p ->
            val dx = p.dayEpoch - meanX
            num += dx * (p.weightKg - meanY)
            den += dx * dx
        }
        if (den == 0.0) return null
        return (num / den) * 7.0 // kg per day -> kg per week
    }

    /**
     * A plateau is defined against measurement noise, not against zero. Under
     * ~0.15 kg/week is indistinguishable from scale drift and should not trigger
     * a plan change.
     */
    fun isStalled(weeklyChangeKg: Double?, thresholdKgPerWeek: Double = 0.15): Boolean =
        weeklyChangeKg != null && abs(weeklyChangeKg) < thresholdKgPerWeek
}
