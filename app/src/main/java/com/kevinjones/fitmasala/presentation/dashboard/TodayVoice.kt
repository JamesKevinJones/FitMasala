package com.kevinjones.fitmasala.presentation.dashboard

import com.kevinjones.fitmasala.data.local.entity.MealType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * What Today says to you. Pure functions of the day, so the voice is tested
 * rather than eyeballed.
 *
 * Every line is a fact about today plus, at most, the next useful move. None
 * praises a deficit: the streak is never awarded for eating less, and the copy
 * holds the same line.
 */
object TodayVoice {

    fun greeting(hour: Int, name: String): String {
        val part = when (hour) {
            in 4..10 -> "Good morning"
            in 11..15 -> "Good afternoon"
            in 16..21 -> "Good evening"
            else -> "Up late"
        }
        val first = name.trim().substringBefore(' ')
        return if (first.isEmpty()) part else "$part, $first"
    }

    /** "today", "yesterday", a weekday within the week, else "3 Oct". Lower case: it sits mid-sentence. */
    fun dayName(dayEpoch: Long, today: Long): String = when (today - dayEpoch) {
        0L -> "today"
        1L -> "yesterday"
        in 2L..6L -> LocalDate.ofEpochDay(dayEpoch).format(DateTimeFormatter.ofPattern("EEEE"))
        else -> LocalDate.ofEpochDay(dayEpoch).format(DateTimeFormatter.ofPattern("d MMM"))
    }

    /**
     * Under the thali on the photo review: where this meal leaves the day it
     * lands on, said before it is logged. Over is said as over.
     */
    fun afterMeal(day: String, eatenKcal: Int, addingKcal: Int, targetKcal: Int?): String {
        val after = eatenKcal + addingKcal
        return when {
            targetKcal == null || targetKcal <= 0 -> "Takes $day to ${"%,d".format(after)} kcal."
            after > targetKcal -> "Puts $day ${"%,d".format(after - targetKcal)} kcal over."
            else -> "Takes $day to ${"%,d".format(after)} of ${"%,d".format(targetKcal)} kcal."
        }
    }

    /** The one sentence under the greeting. First matching rule wins. */
    fun line(
        hour: Int,
        mealsLogged: Set<MealType>,
        eatenKcal: Int,
        targetKcal: Int?,
        proteinG: Int,
        proteinTargetG: Int?,
        streakDays: Int,
    ): String {
        // A Valid day needs two Meals. Say so plainly while it is still reachable.
        if (mealsLogged.isEmpty()) return when {
            hour < 11 -> "Nothing on the plate yet. Breakfast first."
            hour < 16 -> "Nothing logged yet today. What was lunch?"
            streakDays > 0 -> "Log two meals tonight and your $streakDays-day streak carries on."
            else -> "Log two meals tonight and today counts."
        }
        if (mealsLogged.size == 1) {
            return "One meal in. One more and today counts toward your streak."
        }
        if (targetKcal == null) return "${"%,d".format(eatenKcal)} kcal so far today."

        val left = targetKcal - eatenKcal
        val proteinDone = proteinTargetG != null && proteinG >= proteinTargetG
        return when {
            left < 0 -> "${"%,d".format(-left)} kcal over today. Tomorrow is a fresh plate."
            left < 150 -> "Right on target. The kitchen can close."
            proteinDone -> "Protein's done. ${"%,d".format(left)} kcal of room left."
            hour >= 17 && MealType.DINNER !in mealsLogged ->
                "${"%,d".format(left)} kcal of room for dinner."
            proteinTargetG != null ->
                "${"%,d".format(left)} kcal left, ${proteinTargetG - proteinG} g protein to go."
            else -> "${"%,d".format(left)} kcal left today."
        }
    }
}
