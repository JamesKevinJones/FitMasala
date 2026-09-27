package com.kevinjones.fitmasala.snap

import com.kevinjones.fitmasala.core.util.combineDateAndTime
import com.kevinjones.fitmasala.core.util.exifTakenAt
import com.kevinjones.fitmasala.core.util.pickerDateOf
import com.kevinjones.fitmasala.core.util.resolveEatenAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** When a gallery photo's meal was eaten - the rules that decide which day it counts on. */
class PhotoTimesTest {

    private val india = ZoneId.of("Asia/Kolkata")
    private fun millis(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0, zone: ZoneId = india) =
        LocalDateTime.of(y, mo, d, h, mi, s).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun exifTimeIsReadInTheLocalZoneWhenTheCameraWroteNoOffset() {
        assertEquals(millis(2026, 9, 27, 13, 4, 5), exifTakenAt("2026:09:27 13:04:05", null, india))
    }

    @Test
    fun theCamerasOwnOffsetWinsOverWhereThePhoneIsNow() {
        // Photographed at 13:04 in Delhi, logged later from London.
        val taken = exifTakenAt("2026:09:27 13:04:05", "+05:30", ZoneId.of("Europe/London"))
        assertEquals(millis(2026, 9, 27, 13, 4, 5), taken)
    }

    @Test
    fun missingZeroedOrUnreadableTimesGiveNothing() {
        assertNull(exifTakenAt(null, null, india))
        assertNull(exifTakenAt("  ", null, india))
        assertNull(exifTakenAt("0000:00:00 00:00:00", null, india))
        assertNull(exifTakenAt("27/09/2026 13:04", null, india))
        // A broken offset falls back to the zone rather than dropping the time.
        assertEquals(millis(2026, 9, 27, 13, 4, 5), exifTakenAt("2026:09:27 13:04:05", "+99:99", india))
    }

    @Test
    fun aMealIsNeverEatenInTheFuture() {
        val now = millis(2026, 9, 27, 21, 0)
        assertEquals(now, resolveEatenAt(millis(2026, 9, 28, 8, 0), now))
        assertEquals(now, resolveEatenAt(null, now))
        assertEquals(millis(2026, 9, 27, 13, 4), resolveEatenAt(millis(2026, 9, 27, 13, 4), now))
    }

    @Test
    fun aPickedDateAndTimeNameThatMomentLocally() {
        val pickerDate = LocalDateTime.of(2026, 9, 26, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(millis(2026, 9, 26, 23, 50), combineDateAndTime(pickerDate, 23, 50, india))
    }

    @Test
    fun theDatePickerStartsOnTheMealsLocalDay() {
        // 00:30 on the 27th in India is still the 26th in UTC - the picker must show the 27th.
        val justAfterMidnight = millis(2026, 9, 27, 0, 30)
        assertEquals(
            LocalDateTime.of(2026, 9, 27, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            pickerDateOf(justAfterMidnight, india),
        )
    }
}
