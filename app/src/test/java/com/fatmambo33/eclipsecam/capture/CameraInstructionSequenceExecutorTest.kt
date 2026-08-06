package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.camera.capabilities.CameraCapabilities
import com.fatmambo33.eclipsecam.camera.capabilities.CameraOutputSize
import com.fatmambo33.eclipsecam.camera.capabilities.LensFacing
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraInstructionSequenceExecutorTest {
    @Test
    fun `executes bracket and preserves completed outputs`() = runBlocking {
        val root = Files.createTempDirectory("capture-instruction").toFile()
        val allocator = CaptureOutputStore(root)
        val backend = RecordingBackend()
        val executor = createExecutor(allocator, capableCamera(), backend)

        val result = executor.capture(
            instructionIndex = 4,
            instruction = instruction(ExposureStrategy.CONTACT_BRACKET),
        )

        assertEquals(CameraCaptureResult.Captured, result)
        assertEquals(listOf(-2, 0, 2), backend.exposures)
        assertEquals(3, root.walkTopDown().count { it.isFile })
    }

    @Test
    fun `fails closed before reserving outputs when camera cannot bracket`() = runBlocking {
        val root = Files.createTempDirectory("capture-unsupported").toFile()
        val allocator = CaptureOutputStore(root)
        val executor = createExecutor(
            allocator,
            capableCamera().copy(
                manualSensorSupported = false,
                exposureCompensationRange = -1..1,
            ),
            RecordingBackend(),
        )

        val result = executor.capture(
            instructionIndex = 0,
            instruction = instruction(ExposureStrategy.TOTALITY_BRACKET),
        )

        assertTrue(result is CameraCaptureResult.FatalError)
        assertFalse(root.walkTopDown().any { it.isFile })
    }

    @Test
    fun `maps recoverable backend failure and removes whole bracket`() = runBlocking {
        val root = Files.createTempDirectory("capture-recoverable").toFile()
        val allocator = CaptureOutputStore(root)
        val backend = RecordingBackend(failAtOrdinal = 1)
        val executor = createExecutor(allocator, capableCamera(), backend)

        val result = executor.capture(
            instructionIndex = 2,
            instruction = instruction(ExposureStrategy.CONTACT_BRACKET),
        )

        assertEquals(CameraCaptureResult.RecoverableError("camera busy"), result)
        assertFalse(root.walkTopDown().any { it.isFile })
        assertTrue(backend.closed)
    }

    private fun createExecutor(
        allocator: CaptureOutputAllocator,
        camera: CameraCapabilities,
        backend: RecordingBackend,
    ) = CameraInstructionSequenceExecutor(
        sessionId = "session-1",
        selectedCamera = { camera },
        requestPolicy = CameraCaptureRequestPolicy(),
        sequencePlanner = CameraCaptureSequencePlanner(allocator),
        sequenceExecutor = CameraCaptureSequenceExecutor(allocator),
        backendFactory = CameraCaptureSequenceBackendFactory { backend },
    )

    private fun instruction(strategy: ExposureStrategy) = CaptureInstruction(
        instantUtc = Instant.parse("2026-08-12T18:00:00Z"),
        phase = CapturePhase.CONTACT_BURST,
        exposureStrategy = strategy,
    )

    private fun capableCamera() = CameraCapabilities(
        cameraId = "0",
        facing = LensFacing.BACK,
        sensorOrientationDegrees = 90,
        minimumZoomRatio = 1f,
        maximumZoomRatio = 8f,
        jpegSizes = listOf(CameraOutputSize(4000, 3000)),
        rawSupported = true,
        manualSensorSupported = true,
        manualFocusSupported = true,
        exposureCompensationRange = -3..3,
    )

    private class RecordingBackend(
        private val failAtOrdinal: Int? = null,
    ) : CameraCaptureSequenceBackend {
        val exposures = mutableListOf<Int>()
        var closed = false

        override suspend fun prepare(request: CameraCaptureRequest) =
            CameraSequencePreparationResult.Ready

        override suspend fun capture(frame: CameraCaptureFrame): CameraFrameCaptureResult {
            if (frame.ordinal == failAtOrdinal) {
                return CameraFrameCaptureResult.RecoverableFailure("camera busy")
            }
            exposures += when (val exposure = frame.exposure) {
                is CaptureExposureStep.Compensation -> exposure.steps
                is CaptureExposureStep.RelativeManualEv -> exposure.offset
            }
            return CameraFrameCaptureResult.Captured
        }

        override suspend fun close() {
            closed = true
        }
    }
}
