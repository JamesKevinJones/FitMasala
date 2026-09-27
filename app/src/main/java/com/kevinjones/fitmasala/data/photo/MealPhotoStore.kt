package com.kevinjones.fitmasala.data.photo

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where meal photos live: `filesDir/meal-photos`, app-private and never in the
 * shared gallery. Backups exclude the whole files domain (see
 * data_extraction_rules.xml), so a photo exists on this phone and nowhere else.
 *
 * The camera app writes the full-resolution shot here through a FileProvider
 * URI; once estimated, it is replaced by the 1568px copy that was actually sent,
 * so the audit trail costs ~300 KB a meal rather than several MB.
 */
@Singleton
class MealPhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val directory: File
        get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    /** A new, empty destination for the camera app. */
    fun newPhotoFile(): File = File(directory, "meal-${System.currentTimeMillis()}.jpg")

    /** The content:// URI the camera app is allowed to write [file] through. */
    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)

    /** Keeps only the downscaled image that was sent for estimation. Blocking IO. */
    fun replaceWith(path: String, jpegBytes: ByteArray) = File(path).writeBytes(jpegBytes)

    /** Removes a photo nothing will reference. Blocking IO; a missing file is fine. */
    fun discard(path: String?) {
        if (path != null) File(path).delete()
    }

    companion object {
        const val DIRECTORY = "meal-photos"

        /** Must match the provider authority in AndroidManifest.xml. */
        const val AUTHORITY_SUFFIX = ".fileprovider"
    }
}
