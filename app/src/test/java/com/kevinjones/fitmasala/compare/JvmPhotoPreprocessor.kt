package com.kevinjones.fitmasala.compare

import com.kevinjones.fitmasala.data.photo.PhotoSizing
import com.kevinjones.fitmasala.data.photo.PreparedImage
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * The app's `ImagePreprocessor`, on the JVM, for the comparison run (#23).
 *
 * Android's Bitmap can't run here, so decoding and encoding use ImageIO - but
 * every number comes from [PhotoSizing], the same object the app uses: the same
 * power-of-two subsample, the same 1568px long edge, the same JPEG quality, and
 * the same EXIF orientations honoured. So both providers see the photo the app
 * would have sent. JPEG and PNG only; ImageIO has no HEIC reader.
 */
internal object JvmPhotoPreprocessor {

    fun prepare(file: File): PreparedImage {
        require(file.exists() && file.length() > 0) { "photo file missing or empty: ${file.path}" }
        val orientation = ExifOrientation.read(file.readBytes())

        val decoded = ImageIO.createImageInputStream(file).use { input ->
            val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull()
                ?: error("${file.name} is not a JPEG or PNG ImageIO can read")
            try {
                reader.input = input
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                val sample = PhotoSizing.sampleSize(width, height)
                val param = reader.defaultReadParam.apply { setSourceSubsampling(sample, sample, 0, 0) }
                Decoded(reader.read(0, param), width, height)
            } finally {
                reader.dispose()
            }
        }

        val oriented = orient(decoded.image, orientation)
        val (w, h) = PhotoSizing.scaledSize(oriented.width, oriented.height)
        val scaled = redraw(oriented, w, h, AffineTransform.getScaleInstance(
            w.toDouble() / oriented.width, h.toDouble() / oriented.height,
        ))

        return PreparedImage(
            jpegBytes = encodeJpeg(scaled, PhotoSizing.JPEG_QUALITY / 100f),
            widthPx = scaled.width,
            heightPx = scaled.height,
            originalWidthPx = decoded.width,
            originalHeightPx = decoded.height,
            originalBytes = file.length(),
        )
    }

    private class Decoded(val image: BufferedImage, val width: Int, val height: Int)

    /** The same orientations the app handles; anything else is left as decoded. */
    private fun orient(src: BufferedImage, orientation: Int): BufferedImage {
        val w = src.width.toDouble()
        val h = src.height.toDouble()
        return when (orientation) {
            ExifOrientation.FLIP_HORIZONTAL ->
                redraw(src, src.width, src.height, AffineTransform(-1.0, 0.0, 0.0, 1.0, w, 0.0))
            ExifOrientation.ROTATE_180 ->
                redraw(src, src.width, src.height, AffineTransform(-1.0, 0.0, 0.0, -1.0, w, h))
            ExifOrientation.FLIP_VERTICAL ->
                redraw(src, src.width, src.height, AffineTransform(1.0, 0.0, 0.0, -1.0, 0.0, h))
            ExifOrientation.ROTATE_90 ->
                redraw(src, src.height, src.width, AffineTransform(0.0, 1.0, -1.0, 0.0, h, 0.0))
            ExifOrientation.ROTATE_270 ->
                redraw(src, src.height, src.width, AffineTransform(0.0, -1.0, 1.0, 0.0, 0.0, w))
            else -> src
        }
    }

    /** Draws onto opaque RGB - the JPEG writer rejects alpha - with bilinear filtering, as the app does. */
    private fun redraw(src: BufferedImage, width: Int, height: Int, transform: AffineTransform): BufferedImage {
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(src, transform, null)
        } finally {
            g.dispose()
        }
        return out
    }

    private fun encodeJpeg(image: BufferedImage, quality: Float): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        return try {
            ByteArrayOutputStream().use { bytes ->
                ImageIO.createImageOutputStream(bytes).use { output ->
                    writer.output = output
                    val param = writer.defaultWriteParam.apply {
                        compressionMode = ImageWriteParam.MODE_EXPLICIT
                        compressionQuality = quality
                    }
                    writer.write(null, IIOImage(image, null, null), param)
                }
                bytes.toByteArray()
            }
        } finally {
            writer.dispose()
        }
    }
}

/**
 * Reads the EXIF orientation tag (0x0112) from a JPEG's APP1 segment. ImageIO
 * ignores it, and phone cameras store most photos sideways with this tag set.
 * Returns [NORMAL] for anything it cannot read.
 */
internal object ExifOrientation {
    const val NORMAL = 1
    const val FLIP_HORIZONTAL = 2
    const val ROTATE_180 = 3
    const val FLIP_VERTICAL = 4
    const val ROTATE_90 = 6
    const val ROTATE_270 = 8

    fun read(jpeg: ByteArray): Int = runCatching { parse(jpeg) }.getOrNull() ?: NORMAL

    private fun parse(b: ByteArray): Int? {
        if (b.size < 4 || b.u8(0) != 0xFF || b.u8(1) != 0xD8) return null
        var i = 2
        while (i + 4 <= b.size) {
            if (b.u8(i) != 0xFF) return null
            val marker = b.u8(i + 1)
            if (marker == 0xDA || marker == 0xD9) return null // image data: no EXIF before it
            val length = b.u16(i + 2, bigEndian = true)
            if (marker == 0xE1 && length >= 8 && String(b, i + 4, 6, Charsets.ISO_8859_1) == "Exif\u0000\u0000") {
                return tiffOrientation(b, i + 10)
            }
            i += 2 + length
        }
        return null
    }

    private fun tiffOrientation(b: ByteArray, tiff: Int): Int? {
        val bigEndian = when (String(b, tiff, 2, Charsets.ISO_8859_1)) {
            "MM" -> true
            "II" -> false
            else -> return null
        }
        val ifd = tiff + b.u32(tiff + 4, bigEndian)
        val count = b.u16(ifd, bigEndian)
        repeat(count) { n ->
            val entry = ifd + 2 + n * 12
            if (b.u16(entry, bigEndian) == 0x0112) return b.u16(entry + 8, bigEndian)
        }
        return null
    }

    private fun ByteArray.u8(i: Int) = this[i].toInt() and 0xFF
    private fun ByteArray.u16(i: Int, bigEndian: Boolean) =
        if (bigEndian) (u8(i) shl 8) or u8(i + 1) else (u8(i + 1) shl 8) or u8(i)
    private fun ByteArray.u32(i: Int, bigEndian: Boolean) =
        if (bigEndian) (u16(i, true) shl 16) or u16(i + 2, true) else (u16(i + 2, false) shl 16) or u16(i, false)
}
