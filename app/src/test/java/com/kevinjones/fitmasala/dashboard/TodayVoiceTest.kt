package com.kevinjones.fitmasala.dashboard

import com.kevinjones.fitmasala.data.local.entity.MealType.BREAKFAST
import com.kevinjones.fitmasala.data.local.entity.MealType.DINNER
import com.kevinjones.fitmasala.data.local.entity.MealType.LUNCH
import com.kevinjones.fitmasala.presentation.dashboard.TodayVoice
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayVoiceTest {

    @Test
    fun greetingUsesFirstNameOnlyAndCopesWithNone() {
        assertEquals("Good morning, Kevin", TodayVoice.greeting(7, "  Kevin Jones "))
        assertEquals("Good evening", TodayVoice.greeting(19, ""))
        assertEquals("Up late", TodayVoice.greeting(2, ""))
    }

    @Test
    fun anEmptyEveningNamesTheStreakItWouldKeep() {
        val line = TodayVoice.line(20, emptySet(), 0, 2200, 0, 150, streakDays = 4)
        assertEquals("Log two meals tonight and your 4-day streak carries on.", line)
    }

    @Test
    fun oneMealIsNotYetAValidDay() {
        // Two dishes at breakfast are still one Meal.
        val line = TodayVoice.line(9, setOf(BREAKFAST), 400, 2200, 20, 150, 0)
        assertEquals("One meal in. One more and today counts toward your streak.", line)
    }

    @Test
    fun goingOverIsSaidPlainlyAndNeverPraised() {
        val line = TodayVoice.line(21, setOf(LUNCH, DINNER), 2300, 2200, 160, 150, 0)
        assertEquals("100 kcal over today. Tomorrow is a fresh plate.", line)
    }

    @Test
    fun anEveningWithoutDinnerPointsAtDinner() {
        val line = TodayVoice.line(18, setOf(BREAKFAST, LUNCH), 1500, 2200, 90, 150, 0)
        assertEquals("700 kcal of room for dinner.", line)
    }

    @Test
    fun aMealUnderReviewSaysWhereItLeavesTheDay() {
        assertEquals("Takes today to 900 of 950 kcal.", TodayVoice.afterMeal("today", 400, 500, 950))
        assertEquals("Puts yesterday 100 kcal over.", TodayVoice.afterMeal("yesterday", 1800, 500, 2200))
        assertEquals("Takes today to 500 kcal.", TodayVoice.afterMeal("today", 0, 500, null))
    }

    @Test
    fun aPhotoFromEarlierNamesItsOwnDay() {
        val today = 20_732L // Tuesday 6 October 2026
        assertEquals("today", TodayVoice.dayName(today, today))
        assertEquals("yesterday", TodayVoice.dayName(today - 1, today))
        assertEquals("Sunday", TodayVoice.dayName(today - 2, today))
    }

    @Test
    fun proteinDoneOutranksTheDinnerNudge() {
        val line = TodayVoice.line(18, setOf(BREAKFAST, LUNCH), 1500, 2200, 150, 150, 0)
        assertEquals("Protein's done. 700 kcal of room left.", line)
    }
}
