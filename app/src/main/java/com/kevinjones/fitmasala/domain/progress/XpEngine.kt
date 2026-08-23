package com.kevinjones.fitmasala.domain.progress

import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * XP awards.
 *
 * Every award is tied to a behaviour that genuinely advances the goal, and
 * nothing is awarded for opening the app or tapping around. The numbers are
 * weighted by how hard the behaviour is: finishing a workout is worth five
 * logged meals because it is five times harder to do and matters more.
 *
 * Deliberately absent: XP for logging a meal that is over target, or for a
 * bigger deficit. Rewarding under-eating in a cutting app is how a fitness
 * tracker turns into something harmful. XP tracks CONSISTENCY, never severity.
 */
object XpEngine {

    const val MEAL_LOGGED = 10
    const val DAY_FULLY_LOGGED = 20      // cleared the 2-meal bar
    const val PROTEIN_TARGET_HIT = 25    // the macro that protects lean mass
    const val WORKOUT_COMPLETED = 50
    const val PROGRESSIVE_OVERLOAD = 30  // beat a previous best on any lift
    const val WEIGHED_IN = 5             // cheap, but it feeds the trend

    /**
     * Levels follow a square-root curve, so early levels come fast and later
     * ones stretch out. Level n needs 100 * n^2 XP.
     */
    fun levelFor(totalXp: Int): Int {
        if (totalXp < 100) return 1
        return floor(sqrt(totalXp / 100.0)).toInt() + 1
    }

    fun xpForLevel(level: Int): Int = 100 * (level - 1).toDouble().pow(2).toInt()

    /** Progress through the current level, 0..1 - what the level bar renders. */
    fun levelProgress(totalXp: Int): Float {
        val level = levelFor(totalXp)
        val floorXp = xpForLevel(level)
        val ceilXp = xpForLevel(level + 1)
        if (ceilXp <= floorXp) return 0f
        return ((totalXp - floorXp).toFloat() / (ceilXp - floorXp)).coerceIn(0f, 1f)
    }

    fun xpToNextLevel(totalXp: Int): Int =
        (xpForLevel(levelFor(totalXp) + 1) - totalXp).coerceAtLeast(0)

    /** Awards for one completed day, given what actually happened. */
    fun dailyAward(
        mealsLogged: Int,
        proteinTargetHit: Boolean,
        workoutsCompleted: Int,
        overloadPrs: Int,
        weighedIn: Boolean,
    ): Int {
        var xp = mealsLogged * MEAL_LOGGED
        if (mealsLogged >= StreakEngine.MIN_MEALS_FOR_A_LOGGED_DAY) xp += DAY_FULLY_LOGGED
        if (proteinTargetHit) xp += PROTEIN_TARGET_HIT
        xp += workoutsCompleted * WORKOUT_COMPLETED
        xp += overloadPrs * PROGRESSIVE_OVERLOAD
        if (weighedIn) xp += WEIGHED_IN
        return xp
    }
}
