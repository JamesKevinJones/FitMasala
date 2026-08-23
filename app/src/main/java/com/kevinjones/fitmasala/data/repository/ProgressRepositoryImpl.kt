package com.kevinjones.fitmasala.data.repository

import com.kevinjones.fitmasala.core.util.DateKeys
import com.kevinjones.fitmasala.data.local.dao.MealDao
import com.kevinjones.fitmasala.data.local.dao.SessionDao
import com.kevinjones.fitmasala.domain.progress.StreakEngine
import com.kevinjones.fitmasala.domain.progress.XpEngine
import com.kevinjones.fitmasala.domain.repository.ProgressRepository
import com.kevinjones.fitmasala.domain.repository.ProgressSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streak and XP, DERIVED from logged data rather than stored as counters.
 *
 * Storing a streak as an integer means it can disagree with the log - a crash
 * mid-write, a deleted meal, a clock change, and the number is wrong with no way
 * to notice. Recomputing from the days themselves means the streak cannot be
 * wrong unless the log is, and deleting a meal correctly breaks a streak that
 * the meal was propping up.
 *
 * The same argument applies to XP: it is a pure function of what happened.
 */
@Singleton
class ProgressRepositoryImpl @Inject constructor(
    private val mealDao: MealDao,
    private val sessionDao: SessionDao,
) : ProgressRepository {

    /** A year is plenty for a streak, and bounds the query. */
    private val windowDays = 365L

    override fun observeProgress(): Flow<ProgressSnapshot> {
        val from = DateKeys.daysAgo(windowDays)
        return combine(
            mealDao.observeDayMealCounts(from),
            sessionDao.observeRecentSessions(limit = 200),
        ) { dayCounts, sessions ->
            val today = DateKeys.today()

            // Only days clearing the two-meal bar count - the same threshold
            // AdaptiveTdee requires for a day to be usable data. That alignment
            // is the whole reason the streak is worth having.
            val loggedDays = dayCounts
                .filter { it.mealCount >= StreakEngine.MIN_MEALS_FOR_A_LOGGED_DAY }
                .map { it.dayEpoch }
                .toSet()

            val streak = StreakEngine.currentStreak(loggedDays, today)

            val mealXp = dayCounts.sumOf { day ->
                XpEngine.dailyAward(
                    mealsLogged = day.mealCount,
                    // Not yet derivable per day without joining targets to each
                    // day's totals; deliberately conservative rather than
                    // inflating the number with a guess.
                    proteinTargetHit = false,
                    workoutsCompleted = 0,
                    overloadPrs = 0,
                    weighedIn = false,
                )
            }
            val workoutXp = sessions.count { it.finishedAt != null } * XpEngine.WORKOUT_COMPLETED
            val totalXp = mealXp + workoutXp

            ProgressSnapshot(
                currentStreak = streak,
                longestStreak = StreakEngine.longestStreak(loggedDays),
                streakAtRisk = StreakEngine.isAtRisk(loggedDays, today),
                isMilestone = StreakEngine.isMilestone(streak),
                totalXp = totalXp,
                level = XpEngine.levelFor(totalXp),
                xpToNextLevel = XpEngine.xpToNextLevel(totalXp),
                levelProgress = XpEngine.levelProgress(totalXp),
            )
        }
    }
}
