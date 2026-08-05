package com.fatmambo33.eclipsecam.capture

import java.nio.file.Files
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureOutputStoreTest {
    private val capturedAt = Instant.parse("2026-08-12T17:45:51.123Z")

    @Test
    fun reservesPrivateSessionDestinationWithStableUtcName() {
        val root = Files.createTempDirectory("capture-output").toFile()
        val output = CaptureOutputStore(root).reserve("session-1", 7, capturedAt)

        assertEquals(File(root, "session-1"), output.sessionDirectory)
        assertEquals("000007_20260812T174551.123Z.jpg", output.imageFile.name)
        assertTrue(output.imageFile.isFile)
    }

    @Test
    fun sanitizesSessionIdentifierWithoutAddingLocationMetadata() {
        val root = Files.createTempDirectory("capture-output").toFile()
        val output = CaptureOutputStore(root).reserve("eclipse / madrid", 0, capturedAt)

        assertEquals("eclipse___madrid", output.sessionDirectory.name)
        assertFalse(output.imageFile.name.contains("madrid"))
    }

    @Test
    fun collisionsReceiveDeterministicSuffixes() {
        val root = Files.createTempDirectory("capture-output").toFile()
        val store = CaptureOutputStore(root)

        val first = store.reserve("session", 3, capturedAt)
        val second = store.reserve("session", 3, capturedAt)

        assertEquals("000003_20260812T174551.123Z.jpg", first.imageFile.name)
        assertEquals("000003_20260812T174551.123Z_1.jpg", second.imageFile.name)
    }

    @Test
    fun releaseRemovesFailedCapturePlaceholder() {
        val root = Files.createTempDirectory("capture-output").toFile()
        val store = CaptureOutputStore(root)
        val output = store.reserve("session", 1, capturedAt)

        assertTrue(store.release(output))
        assertFalse(output.imageFile.exists())
        assertTrue(store.release(output))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeInstructionIndex() {
        CaptureOutputStore(Files.createTempDirectory("capture-output").toFile())
            .reserve("session", -1, capturedAt)
    }
}
