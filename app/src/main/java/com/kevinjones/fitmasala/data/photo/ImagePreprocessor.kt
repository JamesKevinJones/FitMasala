package com.kevinjones.fitmasala.data.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device preparation of a food photo before it is sent to the vision model.
 *
 * This is where the phone's own compute earns its keep. A modern camera produces
 * a 12–50 MP JPEG; uploading that raw would be slow on Indian mobile data,
 * expensive in tokens, and no more accurate — vision models downscale server-side
 * anyway. Doing the work locally is strictly better on every axis.
 *
 * Three things matter here:
 *
 * 1. **Two-pass decode.** The first pass reads only the bounds
 *    (`inJustDecodeBounds`), which allocates nothing. The second decodes with an
 *    `inSampleSize` so the full-resolution bitmap never exists in memory. Naively
 *    decoding a 50 MP photo costs ~200 MB and OOMs on a mid-range phone — this is
 *    the single most common crash in photo-logging apps.
 * 2. **EXIF rotation.** Phone cameras write the sensor orientation to EXIF rather
 *    than rotating pixels. Skip this and every portrait photo of a thali arrives
 *    sideways, which measurably degrades what the model can identify.
 * 3. **CPU dispatcher.** Decode and JPEG encode are compute-bound, not IO-bound.
 *    Running them on Dispatchers.IO would park them on a thread pool sized for
 *    blocking calls and compete badly with the network request that follows.
 */
@Singleton
class ImagePreprocessor @Inject constructor() {

    /**
     * 1568px on the long edge is the point past which the major vision APIs stop
     * gaining accuracy and start just costing more — they downscale to roughly
     * this internally. Sending more pixels than this is pure waste.
     */
    private val maxEdgePx = 1568

    /**
     * 85 is the knee of the quality/size curve for photographic content. Below
     * about 75, compression artefacts start blurring the texture cues the model
     * uses to tell a dry sabzi from one swimming in oil.
     */
    private val jpegQuality = 85

    suspend fun prepare(file: File): PreparedImage = withContext(Dispatchers.Default) {
        require(file.exists() && file.length() > 0) { "photo file missing or empty: ${file.path}" }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "not a decodable image" }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxEdgePx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            ?: error("failed to decode ${file.name}")

        val oriented = applyExifRotation(file, decoded)
        val scaled = scaleToMaxEdge(oriented, maxEdgePx)

        val bytes = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
            out.toByteArray()
        }

        // Free eagerly. GC will get there, but a queue of food photos is exactly
        // the workload where "eventually" is too late.
        if (scaled !== oriented) scaled.recycle()
        if (oriented !== decoded) oriented.recycle()
        decoded.recycle()

        PreparedImage(
            jpegBytes = bytes,
            widthPx = scaled.width,
            heightPx = scaled.height,
            originalWidthPx = bounds.outWidth,
            originalHeightPx = bounds.outHeight,
            originalBytes = file.length(),
        )
    }

    /**
     * Largest power-of-two subsample that still leaves the image at or above the
     * target. Powers of two because BitmapFactory rounds down to one anyway, and
     * they are the only values it decodes without an intermediate allocation.
     */
    internal fun calculateSampleSize(width: Int, height: Int, targetEdge: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (maxOf(w, h) / 2 >= targetEdge) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleToMaxEdge(source: Bitmap, targetEdge: Int): Bitmap {
        val longEdge = maxOf(source.width, source.height)
        if (longEdge <= targetEdge) return source
        val ratio = targetEdge.toDouble() / longEdge
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

data class PreparedImage(
    val jpegBytes: ByteArray,
    val widthPx: Int,
    val heightPx: Int,
    val originalWidthPx: Int,
    val originalHeightPx: Int,
    val originalBytes: Long,
) {
    val compressionRatio: Double
        get() = if (jpegBytes.isEmpty()) 0.0 else originalBytes.toDouble() / jpegBytes.size

    // ByteArray in a data class gives reference equality from the generated
    // equals(), which is a silent correctness trap. Stated explicitly.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreparedImage) return false
        return jpegBytes.contentEquals(other.jpegBytes) &&
            widthPx == other.widthPx &&
            heightPx == other.heightPx
    }

    override fun hashCode(): Int =
        31 * (31 * jpegBytes.contentHashCode() + widthPx) + heightPx
}
