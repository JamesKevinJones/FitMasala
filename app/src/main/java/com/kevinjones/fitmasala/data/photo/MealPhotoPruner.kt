package com.kevinjones.fitmasala.data.photo

import com.kevinjones.fitmasala.data.local.dao.MealDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deletes meal photos older than [PHOTO_RETENTION_DAYS] and clears their paths.
 * The Dishes are never touched beyond that: history, macros and the days they
 * count on stay exactly as logged.
 *
 * Run at app start - the app has no WorkManager, and a phone that is never
 * opened is not accumulating new photos either.
 */
@Singleton
class MealPhotoPruner @Inject constructor(
    private val meals: MealDao,
) {
    /** Returns how many Dishes lost their photo. Never on the main thread. */
    suspend fun prune(now: Long = System.currentTimeMillis()): Int = withContext(Dispatchers.IO) {
        val expired = meals.photoPathsOnlyEatenBefore(photoRetentionCutoff(now))
        if (expired.isEmpty()) return@withContext 0
        // Files first: a path is cleared only once its file is really gone.
        deletePhotoFiles(expired)
            // SQLite caps bound parameters at 999 on older Android versions.
            .chunked(MAX_PATHS_PER_UPDATE)
            .sumOf { meals.clearPhotoPaths(it) }
    }

    private companion object {
        const val MAX_PATHS_PER_UPDATE = 500
    }
}
