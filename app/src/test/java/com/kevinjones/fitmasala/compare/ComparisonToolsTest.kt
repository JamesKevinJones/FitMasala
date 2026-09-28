package com.kevinjones.fitmasala.compare

import com.kevinjones.fitmasala.data.local.entity.PortionUnit
import com.kevinjones.fitmasala.data.photo.PhotoSizing
import com.kevinjones.fitmasala.data.remote.LlmResult
import com.kevinjones.fitmasala.data.remote.dto.MacrosDto
import com.kevinjones.fitmasala.data.remote.dto.PhotoEstimateDto
import com.kevinjones.fitmasala.data.remote.dto.PhotoItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/** The comparison run's tooling (#23), tested without a network or a key. */
class ComparisonToolsTest {

    @get:Rule val temp = TemporaryFolder()

    init { System.setProperty("java.awt.headless", "true") }

    // --- Photos ----------------------------------------------------------------

    private fun jpeg(width: Int, height: Int, orientation: Int? = null): File {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        // Left half red, right half blue, so a rotation is visible in the pixels.
        val g = image.createGraphics()
        g.color = Color.RED; g.fillRect(0, 0, width / 2, height)
        g.color = Color.BLUE; g.fillRect(width / 2, 0, width - width / 2, height)
        g.dispose()
        val plain = ByteArrayOutputStream().also { ImageIO.write(image, "jpeg", it) }.toByteArray()
        val bytes = if (orientation == null) plain else withExifOrientation(plain, orientation)
        return temp.newFile().apply { writeBytes(bytes) }
    }

    /** Inserts a minimal big-endian EXIF APP1 segment carrying one orientation tag. */
    private fun withExifOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
        val tiff = byteArrayOf(
            'M'.code.toByte(), 'M'.code.toByte(), 0, 42, 0, 0, 0, 8, // header, IFD0 at 8
            0, 1, // one entry
            0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, orientation.toByte(), 0, 0, // orientation, SHORT, 1
            0, 0, 0, 0, // no next IFD
        )
        val payload = "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + tiff
        val length = payload.size + 2
        val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
        return jpeg.copyOfRange(0, 2) + app1 + jpeg.copyOfRange(2, jpeg.size)
    }

    @Test fun aBigPhotoIsSizedLikeTheAppSizesIt() {
        val prepared = JvmPhotoPreprocessor.prepare(jpeg(4000, 3000))
        assertEquals(PhotoSizing.MAX_EDGE_PX, prepared.widthPx)
        assertEquals(1176, prepared.heightPx)
        assertEquals(4000 to 3000, prepared.originalWidthPx to prepared.originalHeightPx)
        val decoded = ImageIO.read(ByteArrayInputStream(prepared.jpegBytes))
        assertEquals(prepared.widthPx to prepared.heightPx, decoded.width to decoded.height)
    }

    @Test fun aSmallPhotoKeepsItsSize() {
        val prepared = JvmPhotoPreprocessor.prepare(jpeg(800, 600))
        assertEquals(800 to 600, prepared.widthPx to prepared.heightPx)
    }

    @Test fun aSidewaysPhoneshotIsTurnedUpright() {
        val file = jpeg(400, 300, orientation = ExifOrientation.ROTATE_90)
        assertEquals(ExifOrientation.ROTATE_90, ExifOrientation.read(file.readBytes()))
        val prepared = JvmPhotoPreprocessor.prepare(file)
        assertEquals(300 to 400, prepared.widthPx to prepared.heightPx)
        // Rotated 90 degrees clockwise, the red left half ends up on top.
        val image = ImageIO.read(ByteArrayInputStream(prepared.jpegBytes))
        val top = Color(image.getRGB(150, 50))
        val bottom = Color(image.getRGB(150, 350))
        assertTrue("top should be red, was $top", top.red > 200 && top.blue < 60)
        assertTrue("bottom should be blue, was $bottom", bottom.blue > 200 && bottom.red < 60)
    }

    @Test fun noExifMeansNormal() {
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.read(jpeg(10, 10).readBytes()))
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.read(byteArrayOf(1, 2, 3)))
    }

    @Test fun sharedSizingMatchesTheOldPreprocessorMaths() {
        assertEquals(2, PhotoSizing.sampleSize(4000, 3000))
        assertEquals(1, PhotoSizing.sampleSize(3000, 2000))
        assertEquals(4, PhotoSizing.sampleSize(8000, 6000))
        assertEquals(1568 to 1045, PhotoSizing.scaledSize(3000, 2000))
    }

    // --- Known values ----------------------------------------------------------

    private val table = """
        photo,calories,protein_g,carbs_g,fat_g,meal_type,portions
        thali.jpg,850,28,110,30,lunch,"Dal=1 KATORI; Roti=2 ROTI; Rice=1 katori"
        chai.jpg,120,,,,,
    """.trimIndent()

    @Test fun theKnownTableReadsQuotedPortionsAndBlankMacros() {
        val (thali, chai) = KnownTable.parse(table)
        assertEquals(850.0, thali.calories, 0.0)
        assertEquals("lunch", thali.mealType)
        assertEquals(
            listOf(
                KnownPortion("Dal", 1.0, PortionUnit.KATORI),
                KnownPortion("Roti", 2.0, PortionUnit.ROTI),
                KnownPortion("Rice", 1.0, PortionUnit.KATORI),
            ),
            thali.portions,
        )
        assertNull("blank is unknown, not zero", chai.proteinG)
        assertEquals(emptyList<KnownPortion>(), chai.portions)
    }

    @Test(expected = IllegalStateException::class)
    fun anUnknownUnitIsAnErrorNotAGuess() {
        KnownTable.parsePortions("Dal=1 BOWL")
    }

    // --- Rows and summaries ----------------------------------------------------

    private fun item(name: String, kcal: Double, unit: String) = PhotoItemDto(
        name = name, region = "PUNJABI", portionEstimate = "", portionQuantity = 1.0, portionUnit = unit,
        portionBasis = "", macros = MacrosDto(kcal, 0.0, 0.0, 0.0, 0.0), confidence = "medium", uncertaintyNote = "",
    )

    private fun estimate(vararg items: PhotoItemDto, statedTotal: Double = 999.0) = LlmResult.Success(
        value = PhotoEstimateDto(true, items.toList(), MacrosDto(statedTotal, 0.0, 0.0, 0.0, 0.0), "medium"),
        rawJson = "{}", model = "m", inputTokens = 2000, outputTokens = 1000,
    )

    private val thali = KnownTable.parse(table).first()

    @Test fun theEstimateIsTheSumOfDishesNotTheStatedTotal() {
        val row = Row.of(thali, "Claude", estimate(item("Dal tadka", 400.0, "KATORI"), item("Roti", 535.0, "ROTI")), Price(5.0, 25.0))
        assertEquals(935.0, row.estimated!!.calories, 0.0)
        assertEquals(10.0, row.calorieErrorPct!!, 1e-9)
        // "Dal tadka" contains "Dal"; Rice was missed, and a miss is a miss.
        assertEquals(2 to 3, row.unitsMatched to row.unitsKnown)
        assertEquals(0.035, row.costUsd!!, 1e-9)
    }

    @Test fun aWrongUnitDoesNotMatch() {
        val row = Row.of(thali, "Gemini", estimate(item("Dal", 850.0, "GRAMS")), null)
        assertEquals(0, row.unitsMatched)
        assertNull("unpriced", row.costUsd)
    }

    @Test fun summariesUseMedianAndCountFailuresAsPhotos() {
        fun row(kcal: Double) = Row.of(thali, "Claude", estimate(item("Dal", kcal, "KATORI")), Price(5.0, 25.0))
        val failed = Row.of(thali, "Claude", LlmResult.Failure.Truncated, Price(5.0, 25.0))
        // Errors: +10%, -30%, +50% -> absolute 10, 30, 50.
        val summary = Summary.of("Claude", listOf(row(935.0), row(595.0), row(1275.0), failed))
        assertEquals(4, summary.photos)
        assertEquals(3, summary.answered)
        assertEquals(30.0, summary.medianAbsCalorieErrorPct!!, 1e-9)
        assertEquals(30.0, summary.meanAbsCalorieErrorPct!!, 1e-9)
        assertEquals(1.0 / 3, summary.within20PctShare!!, 1e-9)
        assertEquals(0.035 * 3 / 4, summary.costPerPhotoUsd!!, 1e-9)
    }

    @Test fun theRuleAllowsFivePointsAndNoMore() {
        fun s(median: Double?) = Summary("x", 1, 1, median, null, null, null, null)
        assertEquals(true, geminiMeetsTheRule(claude = s(18.0), gemini = s(23.0)))
        assertEquals(false, geminiMeetsTheRule(claude = s(18.0), gemini = s(23.1)))
        assertEquals(null, geminiMeetsTheRule(claude = s(18.0), gemini = s(null)))
    }

    @Test fun theReportHasEveryRowAndTheVerdict() {
        val rows = listOf(
            Row.of(thali, "Claude", estimate(item("Dal", 935.0, "KATORI")), Price(5.0, 25.0)),
            Row.of(thali, "Gemini", LlmResult.Failure.Refused("SAFETY", "declined"), null),
        )
        val report = ReportWriter.markdown(rows, listOf(Summary.of("Claude", rows.take(1))), verdict = false)
        assertTrue(report.contains("| thali.jpg | Claude | m | 850 | 935 | +10% |"))
        assertTrue(report.contains("Refused: declined"))
        assertTrue(report.contains("Claude stays the default"))
        assertFalse(report.contains("null"))
    }
}
