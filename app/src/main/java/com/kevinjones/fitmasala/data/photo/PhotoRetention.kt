package com.kevinjones.fitmasala.data.photo

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * How long a meal photo is kept. Photos are evidence for when the scale and the
 * calorie log disagree, and a period that has left the weight trend no longer
 * needs evidence - but the files would otherwise grow by ~0.3 GB a year on a
 * phone with backups disabled. See the 2026-09-27 "Photo meal logging" decision.
 */
const val PHOTO_RETENTION_DAYS = 90L

/** Photos of meals eaten before this moment are pruned. */
fun photoRetentionCutoff(now: Long): Long = now - TimeUnit.DAYS.toMillis(PHOTO_RETENTION_DAYS)

/**
 * Deletes each photo and returns the paths that are now gone - deleted here, or
 * already missing - whose references can be cleared. A file that exists but
 * could not be deleted keeps its reference, so the next app start tries again.
 * Blocking IO.
 */
fun deletePhotoFiles(paths: List<String>): List<String> = paths.filter { path ->
    val file = File(path)
    !file.exists() || file.delete()
}
