package com.kevinjones.fitmasala.progress

import com.kevinjones.fitmasala.domain.progress.StreakEngine
import com.kevinjones.fitmasala.domain.progress.StreakTier
import com.kevinjones.fitmasala.domain.progress.XpEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakAndXpTest {

    private val today = 20_000L

    @Test
    fun anUnfinishedTodayDoesNotBreakTheStreak() {
        // Logged through yesterday, nothing yet today. At 7am that is a normal
        // state, not a failure - showing a broken streak here would punish the
        // user for the passage of time.
        val days = setOf(today - 1, today - 2, today - 3)
        assertEquals(3, StreakEngine.currentStreak(days, today))
        assertTrue(StreakEngine.isAtRisk(days, today))
    }

    @Test
    fun aFullyMissedDayEndsTheStreak() {
        val days = setOf(today - 2, today - 3, today - 4)
        assertEquals(0, StreakEngine.currentStreak(days, today))
        assertFalse(StreakEngine.isAtRisk(days, today))
    }

    @Test
    fun onceTodayIsLoggedTheNudgeStopsFiring() {
        val days = setOf(today, today - 1)
        assertEquals(2, StreakEngine.currentStreak(days, today))
        // Prompting for something already done is noise.
        assertFalse(StreakEngine.isAtRisk(days, today))
    }

    @Test
    fun aGapIsNotCountedThroughToAnOlderRun() {
        val days = setOf(today, today - 1, /* gap */ today - 3, today - 4, today - 5)
        assertEquals(2, StreakEngine.currentStreak(days, today))
        assertEquals(3, StreakEngine.longestStreak(days))
    }

    @Test
    fun tiersOnlyWarmUpAsTheStreakEarnsIt() {
        assertEquals(StreakTier.COLD, StreakEngine.tierFor(1))
        assertEquals(StreakTier.COLD, StreakEngine.tierFor(6))
        assertEquals(StreakTier.WARM, StreakEngine.tierFor(7))
        assertEquals(StreakTier.HOT, StreakEngine.tierFor(30))
    }

    @Test
    fun milestonesStaySparseEnoughToMeanSomething() {
        assertTrue(StreakEngine.isMilestone(7))
        assertTrue(StreakEngine.isMilestone(30))
        assertFalse(StreakEngine.isMilestone(8))
        assertFalse(StreakEngine.isMilestone(29))
    }

    /**
     * The alignment that makes the streak worth having: it uses the same 2-meal
     * bar AdaptiveTdee needs, so the game rewards exactly the behaviour the
     * maths depends on.
     */
    @Test
    fun theStreakBarMatchesTheBarTheTdeeMathsRequires() {
        assertEquals(2, StreakEngine.MIN_MEALS_FOR_A_LOGGED_DAY)
    }

    @Test
    fun levelsStartFastAndThenStretchOut() {
        assertEquals(1, XpEngine.levelFor(0))
        assertEquals(1, XpEngine.levelFor(99))
        assertEquals(2, XpEngine.levelFor(100))
        assertEquals(3, XpEngine.levelFor(400))
        assertEquals(4, XpEngine.levelFor(900))
        // Level 2 costs 300 XP; level 9 costs 1700. The curve stretches.
        assertTrue(
            XpEngine.xpForLevel(10) - XpEngine.xpForLevel(9) >
                XpEngine.xpForLevel(3) - XpEngine.xpForLevel(2),
        )
    }

    @Test
    fun levelProgressAndRemainingAgreeWithEachOther() {
        val xp = 500
        val level = XpEngine.levelFor(xp)
        val progress = XpEngine.levelProgress(xp)
        val remaining = XpEngine.xpToNextLevel(xp)

        assertEquals(3, level)
        assertTrue(progress > 0f && progress < 1f)
        assertEquals(XpEngine.xpForLevel(4) - xp, remaining)
    }

    @Test
    fun aFullDayScoresMoreThanTheSumOfItsMeals() {
        val oneMeal = XpEngine.dailyAward(1, false, 0, 0, false)
        val twoMeals = XpEngine.dailyAward(2, false, 0, 0, false)

        assertEquals(10, oneMeal)
        // 2 meals = 20, plus the 20 bonus for clearing the usable-day bar.
        assertEquals(40, twoMeals)
        assertTrue("the bonus must reward consistency", twoMeals > oneMeal * 2)
    }

    @Test
    fun aTrainingDayWithAPersonalBestScoresHighest() {
        val award = XpEngine.dailyAward(
            mealsLogged = 3,
            proteinTargetHit = true,
            workoutsCompleted = 1,
            overloadPrs = 1,
            weighedIn = true,
        )
        // 30 + 20 + 25 + 50 + 30 + 5
        assertEquals(160, award)
    }
}
