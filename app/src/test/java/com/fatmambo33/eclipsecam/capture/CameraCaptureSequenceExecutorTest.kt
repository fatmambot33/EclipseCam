package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.camera.capabilities.CameraOutputSize
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCaptureSequenceExecutorTest {
    @Test
    fun capturesEveryFrameInOrderAndPreservesOutputs() = runBlocking {
        val allocator = RecordingAllocator()
        val backend = RecordingBackend(yieldBeforeResult = true)
        val sequence = sequence()

        val result = CameraCaptureSequenceExecutor(allocator).execute(sequence, backend)

        assertTrue(result is CameraCaptureSequenceResult.Completed)
        assertEquals(listOf(0, 1, 2), backend.capturedOrdinals)
        assertEquals(emptyList<CaptureOutput>(), allocator.released)
        assertEquals(1, backend.closeCount)
    }

    @Test
    fun recoverableFrameFailureStopsSequenceAndReleasesWholeBracket() = runBlocking {
        val allocator = RecordingAllocator()
        val backend = RecordingBackend(
            frameResults = mutableListOf(
                CameraFrameCaptureResult.Captured,
                CameraFrameCaptureResult.RecoverableFailure("Camera temporarily unavailable"),
            ),
        )
        val sequence = sequence()

        val result = CameraCaptureSequenceExecutor(allocator).execute(sequence, backend)

        assertEquals(
            CameraCaptureSequenceResult.RecoverableFailure("Camera temporarily unavailable"),
            result,
        )
        assertEquals(listOf(0, 1), backend.capturedOrdinals)
        assertEquals(sequence.frames.map { it.output }, allocator.released)
        assertEquals(1, backend.closeCount)
    }

    @Test
    fun fatalPreparationFailureDoesNotCaptureAndReleasesOutputs() = runBlocking {
        val allocator = RecordingAllocator()
        val backend = RecordingBackend(
            preparation = CameraSequencePreparationResult.FatalFailure("Unsupported camera state"),
        )
        val sequence = sequence()

        val result = CameraCaptureSequenceExecutor(allocator).execute(sequence, backend)

        assertEquals(
            CameraCaptureSequenceResult.FatalFailure("Unsupported camera state"),
            result,
        )
        assertTrue(backend.capturedOrdinals.isEmpty())
        assertEquals(sequence.frames.map { it.output }, allocator.released)
        assertEquals(1, backend.closeCount)
    }

    @Test
    fun backendExceptionIsFatalAndReleasesOutputs() = runBlocking {
        val allocator = RecordingAllocator()
        val backend = RecordingBackend(throwOnOrdinal = 1)
        val sequence = sequence()

        val result = CameraCaptureSequenceExecutor(allocator).execute(sequence, backend)

        assertEquals(CameraCaptureSequenceResult.FatalFailure("camera exploded"), result)
        assertEquals(sequence.frames.map { it.output }, allocator.released)
        assertEquals(1, backend.closeCount)
    }

    @Test
    fun closeFailureInvalidatesOtherwiseCompleteSequence() = runBlocking {
        val allocator = RecordingAllocator()
        val backend = RecordingBackend(throwOnClose = true)
        val sequence = sequence()

        val result = CameraCaptureSequenceExecutor(allocator).execute(sequence, backend)

        assertEquals(CameraCaptureSequenceResult.FatalFailure("close failed"), result)
        assertEquals(sequence.frames.map { it.output }, allocator.released)
    }

    @Test
    fun cleanupFailurePromotesRecoverableFailureToFatal() = runBlocking {
        val allocator = RecordingAllocator(releaseSucceeds = false)
        val backend = RecordingBackend(
            preparation = CameraSequencePreparationResult.RecoverableFailure("Camera busy"),
        )

        val result = CameraCaptureSequenceExecutor(allocator).execute(sequence(), backend)

        assertEquals(
            CameraCaptureSequenceResult.FatalFailure(
                "Unable to clean incomplete camera sequence outputs.",
            ),
            result,
        )
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
            capturedAtUtc: java.time.Instant,
        ): CaptureOutput = error("Not used")

        override fun release(output: CaptureOutput): Boolean {
            released += output
            return releaseSucceeds
        }
    }

    private class RecordingBackend(
        private val preparation: CameraSequencePreparationResult =
            CameraSequencePreparationResult.Ready,
        private val frameResults: MutableList<CameraFrameCaptureResult> = mutableListOf(),
        private val throwOnOrdinal: Int? = null,
        private val throwOnClose: Boolean = false,
        private val yieldBeforeResult: Boolean = false,
    ) : CameraCaptureSequenceBackend {
        val capturedOrdinals = mutableListOf<Int>()
        var closeCount = 0

        override suspend fun prepare(
            request: CameraCaptureRequest,
        ): CameraSequencePreparationResult {
            if (yieldBeforeResult) yield()
            return preparation
        }

        override suspend fun capture(frame: CameraCaptureFrame): CameraFrameCaptureResult {
            if (yieldBeforeResult) yield()
            capturedOrdinals += frame.ordinal
            if (frame.ordinal == throwOnOrdinal) error("camera exploded")
            return if (frameResults.isEmpty()) {
                CameraFrameCaptureResult.Captured
            } else {
                frameResults.removeAt(0)
            }
        }

        override suspend fun close() {
            if (yieldBeforeResult) yield()
            closeCount += 1
            if (throwOnClose) error("close failed")
        }
    }
}
