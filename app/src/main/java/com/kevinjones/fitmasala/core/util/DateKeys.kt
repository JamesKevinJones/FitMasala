package com.kevinjones.fitmasala.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The one place epoch-millis becomes an epoch-DAY.
 *
 * `dayEpoch` is stored on meals and sessions so "today" is an indexed integer
 * comparison rather than timezone arithmetic inside SQLite. Because it is
 * computed once at write time, a meal stays on the day it was actually eaten
 * even if the phone later crosses a timezone.
 */
object DateKeys {

    fun dayEpochOf(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toEpochDay()

    fun today(zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.now(zone).toEpochDay()

    fun localDateOf(dayEpoch: Long): LocalDate = LocalDate.ofEpochDay(dayEpoch)

    /** Inclusive start of an N-day window ending today — for trend charts. */
    fun daysAgo(days: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.now(zone).minusDays(days).toEpochDay()
}
