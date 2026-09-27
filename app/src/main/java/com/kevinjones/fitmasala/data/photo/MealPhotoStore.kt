package com.kevinjones.fitmasala.data.photo

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
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

    /**
     * Copies a photo picked from the gallery into app-private storage, so the
     * meal keeps its audit photo even if the original is later deleted. Blocking IO.
     */
    fun importFrom(uri: Uri): File {
        val file = newPhotoFile()
        val input = context.contentResolver.openInputStream(uri) ?: error("could not open $uri")
        input.use { source -> file.outputStream().use { source.copyTo(it) } }
        return file
    }

    /**
     * The photo's own `DateTimeOriginal` and `OffsetTimeOriginal`, raw - read
     * before the file is downscaled, because re-encoding drops EXIF. Blocking IO.
     */
    fun exifDateTime(path: String): Pair<String?, String?> {
        val exif = ExifInterface(path)
        return exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) to
            exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
    }

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
