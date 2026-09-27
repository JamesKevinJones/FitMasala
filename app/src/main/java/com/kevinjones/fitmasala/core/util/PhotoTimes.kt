package com.kevinjones.fitmasala.core.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * When a photo was taken, from its EXIF `DateTimeOriginal` ("2026:09:27 13:04:05")
 * and, when the camera wrote one, `OffsetTimeOriginal` ("+05:30").
 *
 * The offset wins over [zone] because it records where the shutter was pressed:
 * lunch photographed in Delhi and logged after landing in London still happened
 * at lunchtime in Delhi. Null when the tag is missing, zeroed (some phones write
 * "0000:00:00 00:00:00" rather than nothing) or unreadable - the caller then uses
 * the moment of logging.
 */
fun exifTakenAt(dateTimeOriginal: String?, offsetTimeOriginal: String?, zone: ZoneId): Long? {
    val raw = dateTimeOriginal?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("0000") } ?: return null
    val local = try {
        LocalDateTime.parse(raw, ExifDateTime)
    } catch (e: DateTimeParseException) {
        return null
    }
    val offset = offsetTimeOriginal?.trim()?.let {
        try { ZoneOffset.of(it) } catch (e: java.time.DateTimeException) { null }
    }
    return (if (offset != null) local.atOffset(offset).toInstant() else local.atZone(zone).toInstant())
        .toEpochMilli()
}

/**
 * The moment a meal is logged against: the photo's own time when there is one,
 * never later than now. A camera clock set wrong can claim a photo from next
 * week, and a meal cannot be eaten in the future.
 */
fun resolveEatenAt(photoTakenAt: Long?, now: Long): Long =
    photoTakenAt?.takeIf { it <= now } ?: now

/**
 * A date from a date picker - which reports midnight UTC of the chosen day - plus
 * an hour and minute on the local clock, as the moment they name in [zone].
 */
fun combineDateAndTime(dateUtcMillis: Long, hour: Int, minute: Int, zone: ZoneId): Long {
    val day = LocalDate.ofEpochDay(Math.floorDiv(dateUtcMillis, 86_400_000L))
    return day.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
}

/** The local day of [millis], as the midnight-UTC value a date picker selects. */
fun pickerDateOf(millis: Long, zone: ZoneId): Long =
    java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private val ExifDateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
