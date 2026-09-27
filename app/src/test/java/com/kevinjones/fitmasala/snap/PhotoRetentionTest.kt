package com.kevinjones.fitmasala.snap

import com.kevinjones.fitmasala.data.photo.deletePhotoFiles
import com.kevinjones.fitmasala.data.photo.photoRetentionCutoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Which photos go after 90 days, and when a Dish may forget its photo. */
class PhotoRetentionTest {

    @get:Rule val folder = TemporaryFolder()

    @Test
    fun theCutoffIsNinetyDaysBeforeNow() {
        val now = LocalDateTime.of(2026, 9, 27, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val cutoff = LocalDateTime.of(2026, 6, 29, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(cutoff, photoRetentionCutoff(now))
    }

    @Test
    fun aDeletedPhotoIsGone() {
        val photo = folder.newFile("meal-1.jpg")
        assertEquals(listOf(photo.path), deletePhotoFiles(listOf(photo.path)))
        assertFalse(photo.exists())
    }

    @Test
    fun anAlreadyMissingPhotoIsStillClearedWithoutCrashing() {
        val missing = folder.root.resolve("meal-gone.jpg").path
        assertEquals(listOf(missing), deletePhotoFiles(listOf(missing)))
    }

    @Test
    fun aPhotoThatCannotBeDeletedKeepsItsReferenceForNextTime() {
        // A non-empty directory stands in for a file the OS refuses to delete.
        val stuck = folder.newFolder("stuck").also { it.resolve("inside").createNewFile() }
        val photo = folder.newFile("meal-2.jpg")

        assertEquals(listOf(photo.path), deletePhotoFiles(listOf(stuck.path, photo.path)))
        assertTrue(stuck.exists())
    }
}
