package com.kevinjones.fitmasala.domain.progress

/**
 * Streaks and XP.
 *
 * The rule that makes this worth building rather than decoration: **a day only
 * counts when it has at least two logged meals** - the same threshold
 * `AdaptiveTdee` uses to decide a day is usable data.
 *
 * That alignment is the entire point. The streak rewards exactly the behaviour
 * the plan engine needs to work, so the game and the maths pull in the same
 * direction. A streak that ticked on a single photographed lunch would train
 * the user into precisely the inconsistent logging that makes an adaptive TDEE
 * estimate worthless - a number going up while the product silently gets worse
 * at its job.
 */
object StreakEngine {

    /** A day is "logged" once it clears the same bar the TDEE maths requires. */
    const val MIN_MEALS_FOR_A_LOGGED_DAY = 2

    /**
     * Consecutive logged days ending today or yesterday.
     *
     * Yesterday counts because the streak must survive a day that is still in
     * progress. Requiring today would show every user a broken streak each
     * morning before breakfast - punishing them for the passage of time.
     */
    fun currentStreak(loggedDays: Set<Long>, today: Long): Int {
        if (loggedDays.isEmpty()) return 0

        val start = when {
            loggedDays.contains(today) -> today
            loggedDays.contains(today - 1) -> today - 1
            else -> return 0 // a full day was missed; the streak is over
        }

        var streak = 0
        var day = start
        while (loggedDays.contains(day)) {
            streak++
            day--
        }
        return streak
    }

    fun longestStreak(loggedDays: Set<Long>): Int {
        if (loggedDays.isEmpty()) return 0
        val sorted = loggedDays.sorted()
        var best = 1
        var run = 1
        for (i in 1 until sorted.size) {
            if (sorted[i] == sorted[i - 1] + 1) run++ else run = 1
            if (run > best) best = run
        }
        return best
    }

    /**
     * Whether today would extend the streak. Drives the nudge on the dashboard,
     * and deliberately returns false once today is already logged - a prompt to
     * do something already done is noise.
     */
    fun isAtRisk(loggedDays: Set<Long>, today: Long): Boolean =
        !loggedDays.contains(today) && loggedDays.contains(today - 1)

    fun tierFor(streakDays: Int): StreakTier = when {
        streakDays >= 30 -> StreakTier.HOT
        streakDays >= 7 -> StreakTier.WARM
        else -> StreakTier.COLD
    }

    /**
     * Milestones worth a celebration. Sparse on purpose: the delight budget for
     * a rare moment is only large because the moment stays rare. Firing
     * confetti every day would make day 30 feel like nothing.
     */
    private val MILESTONES = setOf(3, 7, 14, 30, 60, 100, 180, 365)

    fun isMilestone(streakDays: Int): Boolean = streakDays in MILESTONES
}

enum class StreakTier { COLD, WARM, HOT }
