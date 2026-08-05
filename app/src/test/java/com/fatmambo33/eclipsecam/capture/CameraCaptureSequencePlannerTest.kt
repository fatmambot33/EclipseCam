package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.camera.capabilities.CameraOutputSize
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCaptureSequencePlannerTest {
    private val capturedAt = Instant.parse("2026-08-12T17:45:51Z")

    @Test
    fun compensationBracketPreservesExposureOrderAndUniqueOutputs() {
        val allocator = FakeAllocator()
        val sequence = CameraCaptureSequencePlanner(allocator).plan(
            sessionId = "session",
            instructionIndex = 12,
            capturedAtUtc = capturedAt,
            request = request(CaptureExposureProgram.ExposureCompensation(listOf(-2, 0, 2))),
        )

        assertEquals(listOf(0, 1, 2), sequence.frames.map(CameraCaptureFrame::ordinal))
        assertEquals(
            listOf(-2, 0, 2),
            sequence.frames.map { (it.exposure as CaptureExposureStep.Compensation).steps },
        )
        assertEquals(3, sequence.frames.map { it.output.imageFile }.distinct().size)
    }

    @Test
    fun manualBracketProducesRelativeEvFrames() {
        val sequence = CameraCaptureSequencePlanner(FakeAllocator()).plan(
            "session",
            0,
            capturedAt,
            request(CaptureExposureProgram.MeteredManualBracket(listOf(-2, 0, 2))),
        )

        assertEquals(
            listOf(-2, 0, 2),
            sequence.frames.map { (it.exposure as CaptureExposureStep.RelativeManualEv).offset },
        )
    }

    @Test
    fun partialReservationFailureRollsBackEveryReservedOutput() {
        val allocator = FakeAllocator(failAtReservation = 2)

        runCatching {
            CameraCaptureSequencePlanner(allocator).plan(
                "session",
                4,
                capturedAt,
                request(CaptureExposureProgram.ExposureCompensation(listOf(-2, 0, 2))),
            )
        }

        assertEquals(2, allocator.released.size)
        assertTrue(allocator.reserved.take(2).all(allocator.released::contains))
    }

    private fun request(program: CaptureExposureProgram) = CameraCaptureRequest(
        cameraId = "0",
        outputSize = CameraOutputSize(4000, 3000),
        focusMode = CaptureFocusMode.CONTINUOUS_AUTO,
        whiteBalanceMode = CaptureWhiteBalanceMode.AUTO_LOCK_AFTER_METERING,
        exposureProgram = program,
    )

    private class FakeAllocator(
        private val failAtReservation: Int? = null,
    ) : CaptureOutputAllocator {
        val reserved = mutableListOf<CaptureOutput>()
        val released = mutableListOf<CaptureOutput>()

        override fun reserve(
            sessionId: String,
            instructionIndex: Int,
            capturedAtUtc: Instant,
        ): CaptureOutput {
            if (reserved.size == failAtReservation) error("No output available")
            val output = CaptureOutput(File(sessionId), File(sessionId, "${reserved.size}.jpg"))
            reserved += output
            return output
        }

        override fun release(output: CaptureOutput): Boolean {
            released += output
            return true
        }
    }
}
