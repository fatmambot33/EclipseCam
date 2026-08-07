package com.fatmambo33.eclipsecam.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fatmambo33.eclipsecam.capture.CaptureInstruction
import com.fatmambo33.eclipsecam.capture.CapturePhase
import com.fatmambo33.eclipsecam.capture.CapturePlan
import com.fatmambo33.eclipsecam.capture.CaptureSessionCheckpoint
import com.fatmambo33.eclipsecam.capture.CaptureSessionStatus
import com.fatmambo33.eclipsecam.capture.ExposureStrategy
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidMontageImageRendererInstrumentationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root: File
        get() = File(context.filesDir, "montage-instrumentation")

    @Before
    fun cleanFixture() {
        root.deleteRecursively()
        root.mkdirs()
    }

    @After
    fun removeFixture() {
        root.deleteRecursively()
    }

    @Test
    fun rendersStableFiveSlotJpegWithExplicitMissingPanelsAndGalleryRediscovery() = runBlocking {
        val start = Instant.parse("2026-08-12T17:00:00Z")
        val plan = CapturePlan(
            startsAtUtc = start,
            endsAtUtc = start.plusSeconds(2),
            instructions = listOf(
                CaptureInstruction(start, CapturePhase.PARTIAL, ExposureStrategy.FILTERED_PARTIAL),
                CaptureInstruction(start.plusSeconds(1), CapturePhase.CONTACT_BURST, ExposureStrategy.CONTACT_BRACKET),
                CaptureInstruction(start.plusSeconds(2), CapturePhase.TOTALITY, ExposureStrategy.TOTALITY_BRACKET),
            ),
        )
        FileLocalCaptureSessionJournal(root).record(
            plan,
            CaptureSessionCheckpoint(
                sessionId = "interrupted-montage",
                planStartsAtUtc = plan.startsAtUtc,
                planEndsAtUtc = plan.endsAtUtc,
                nextInstructionIndex = 3,
                capturedCount = 3,
                skippedCount = 0,
                status = CaptureSessionStatus.PAUSED,
                updatedAtUtc = start.plusSeconds(3),
            ),
        )
        val directory = File(root, "interrupted-montage")
        val partial = File(directory, "000000_partial.jpg")
        val contact = File(directory, "000001_contact.jpg")
        val totality = File(directory, "000002_totality.jpg")
        writeJpeg(partial, Color.RED)
        writeJpeg(contact, Color.GREEN)
        writeJpeg(totality, Color.BLUE)
        val originals = mapOf(
            partial to partial.readBytes(),
            contact to contact.readBytes(),
            totality to totality.readBytes(),
        )
        val session = LocalSessionIndex(root).listSessions().single()
        val generator = LocalMontageGenerator(
            renderer = AndroidMontageImageRenderer(),
            frameProbe = AndroidJpegMontageFrameProbe(),
        )

        val result = generator.render(session)

        assertTrue(result is MontageRenderResult.Completed)
        result as MontageRenderResult.Completed
        assertEquals(3, result.selectedFrameCount)
        assertEquals(listOf(MontageSlot.CONTACT_LATE, MontageSlot.PARTIAL_LATE), result.missingSlots)
        assertTrue(result.output.isFile)
        assertTrue(result.output.length() > 0L)
        assertFalse(File(directory, "generated/montage.rendering.jpg").exists())

        val bitmap = BitmapFactory.decodeFile(result.output.absolutePath)
        requireNotNull(bitmap)
        try {
            assertEquals(1800, bitmap.width)
            assertEquals(800, bitmap.height)
            assertDominant(bitmap.getPixel(228, 390), Color.RED)
            assertDominant(bitmap.getPixel(564, 390), Color.GREEN)
            assertDominant(bitmap.getPixel(900, 390), Color.BLUE)
            assertNear(bitmap.getPixel(1236, 390), Color.rgb(31, 41, 55), tolerance = 12)
            assertNear(bitmap.getPixel(1572, 390), Color.rgb(31, 41, 55), tolerance = 12)
        } finally {
            bitmap.recycle()
        }

        originals.forEach { (file, bytes) -> assertArrayEquals(bytes, file.readBytes()) }
        val reindexed = LocalSessionIndex(root).listSessions().single()
        assertTrue(reindexed.generatedAssets.any { it.kind == LocalSessionAssetKind.MONTAGE })
    }

    private fun writeJpeg(file: File, color: Int) {
        val bitmap = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
        }
        bitmap.recycle()
    }

    private fun assertDominant(actual: Int, expected: Int) {
        val actualChannels = listOf(Color.red(actual), Color.green(actual), Color.blue(actual))
        val expectedDominant = when (expected) {
            Color.RED -> 0
            Color.GREEN -> 1
            Color.BLUE -> 2
            else -> error("Unsupported expected color")
        }
        assertTrue(actualChannels[expectedDominant] > 180)
        actualChannels.filterIndexed { index, _ -> index != expectedDominant }.forEach { channel ->
            assertTrue(channel < 80)
        }
    }

    private fun assertNear(actual: Int, expected: Int, tolerance: Int) {
        assertTrue(kotlin.math.abs(Color.red(actual) - Color.red(expected)) <= tolerance)
        assertTrue(kotlin.math.abs(Color.green(actual) - Color.green(expected)) <= tolerance)
        assertTrue(kotlin.math.abs(Color.blue(actual) - Color.blue(expected)) <= tolerance)
    }
}
