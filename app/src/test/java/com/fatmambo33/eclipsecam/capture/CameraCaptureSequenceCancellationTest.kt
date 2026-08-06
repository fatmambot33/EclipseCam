package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.camera.capabilities.CameraOutputSize
import java.io.File
import java.time.Instant
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class CameraCaptureSequenceCancellationTest {
    @Test
    fun cancellationClosesBackendReleasesBracketAndPropagates() = runBlocking {
        val allocator = RecordingAllocator()
        val backend = CancellingBackend()
        val sequence = sequence()
        val expected = backend.cancellation

        try {
            CameraCaptureSequenceExecutor(allocator).execute(sequence, backend)
            fail("Expected cancellation")
        } catch (actual: CancellationException) {
            assertSame(expected, actual)
        }

        assertEquals(1, backend.closeCount)
        assertEquals(sequence.frames.map { it.output }, allocator.released)
    }

    @Test
    fun cleanupFailuresAreAttachedWithoutReplacingCancellation() = runBlocking {
        val allocator = RecordingAllocator(releaseSucceeds = false)
        val backend = CancellingBackend(throwOnClose = true)

        try {
            CameraCaptureSequenceExecutor(allocator).execute(sequence(), backend)
            fail("Expected cancellation")
        } catch (actual: CancellationException) {
            assertEquals(2, actual.suppressed.size)
            assertEquals("close failed", actual.suppressed[0].message)
            assertEquals(
                "Unable to clean cancelled camera sequence outputs.",
                actual.suppressed[1].message,
            )
        }
    }

    private fun sequence(): CameraCaptureSequence {
        val request = CameraCaptureRequest(
            cameraId = "0",
            outputSize = CameraOutputSize(4000, 3000),
            focusMode = CaptureFocusMode.CONTINUOUS_AUTO,
            whiteBalanceMode = CaptureWhiteBalanceMode.AUTO_LOCK_AFTER_METERING,
            exposureProgram = CaptureExposureProgram.ExposureCompensation(listOf(-2, 0, 2)),
        )
        return CameraCaptureSequence(
            request = request,
            frames = listOf(-2, 0, 2).mapIndexed { ordinal, step ->
                CameraCaptureFrame(
                    ordinal = ordinal,
                    exposure = CaptureExposureStep.Compensation(step),
                    output = CaptureOutput(
                        sessionDirectory = File("session"),
                        imageFile = File("session/frame-$ordinal.jpg"),
                    ),
                )
            },
        )
    }

    private class RecordingAllocator(
        private val releaseSucceeds: Boolean = true,
    ) : CaptureOutputAllocator {
        val released = mutableListOf<CaptureOutput>()

        override fun reserve(
            sessionId: String,
            instructionIndex: Int,
            capturedAtUtc: Instant,
        ): CaptureOutput = error("Not used")

        override fun release(output: CaptureOutput): Boolean {
            released += output
            return releaseSucceeds
        }
    }

    private class CancellingBackend(
        private val throwOnClose: Boolean = false,
    ) : CameraCaptureSequenceBackend {
        val cancellation = CancellationException("capture cancelled")
        var closeCount = 0

        override suspend fun prepare(
            request: CameraCaptureRequest,
        ): CameraSequencePreparationResult = CameraSequencePreparationResult.Ready

        override suspend fun capture(frame: CameraCaptureFrame): CameraFrameCaptureResult {
            throw cancellation
        }

        override suspend fun close() {
            closeCount += 1
            if (throwOnClose) error("close failed")
        }
    }
}
