package com.kevinjones.fitmasala.data.photo

import kotlin.math.roundToInt

/** How a meal photo is sized before upload: the long-edge cap and JPEG quality. */
object PhotoSizing {

    /**
     * 1568px on the long edge is the point past which the major vision APIs stop
     * gaining accuracy and start just costing more — they downscale to roughly
     * this internally. Sending more pixels than this is pure waste.
     */
    const val MAX_EDGE_PX = 1568

    /**
     * 85 is the knee of the quality/size curve for photographic content. Below
     * about 75, compression artefacts start blurring the texture cues the model
     * uses to tell a dry sabzi from one swimming in oil.
     */
    const val JPEG_QUALITY = 85

    /**
     * Largest power-of-two subsample that still leaves the image at or above the
     * target. Powers of two because BitmapFactory rounds down to one anyway, and
     * they are the only values it decodes without an intermediate allocation.
     */
    fun sampleSize(width: Int, height: Int, targetEdge: Int = MAX_EDGE_PX): Int {
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

    /**
     * The final size: unchanged if already within [targetEdge], else the long edge
     * is exactly [targetEdge] and the short one rounds. (Truncating both, as this
     * once did, left 3000px photos at 1567 - a float `1567.99...`.)
     */
    fun scaledSize(width: Int, height: Int, targetEdge: Int = MAX_EDGE_PX): Pair<Int, Int> {
        val longEdge = maxOf(width, height)
        if (longEdge <= targetEdge) return width to height
        val ratio = targetEdge.toDouble() / longEdge
        fun scale(edge: Int) = if (edge == longEdge) targetEdge else (edge * ratio).roundToInt().coerceAtLeast(1)
        return scale(width) to scale(height)
    }
}
