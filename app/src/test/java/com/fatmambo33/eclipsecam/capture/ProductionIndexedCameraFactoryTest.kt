package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.camera.capabilities.CameraCapabilities
import com.fatmambo33.eclipsecam.camera.capabilities.CameraOutputSize
import com.fatmambo33.eclipsecam.camera.capabilities.LensFacing
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionIndexedCameraFactoryTest {
    @Test
    fun composesAppPrivateTransactionalCapturePipeline() = runBlocking {
        val root = Files.createTempDirectory("camera-graph").toFile()
        val recovery = recovery()
        var preparedCameraId: String? = null
        val factory = ProductionIndexedCameraFactory(
            outputRootDirectory = root,
            selectedCamera = { compatibleCamera() },
            backendFactory = CameraCaptureSequenceBackendFactory {
                object : CameraCaptureSequenceBackend {
                    override suspend fun prepare(
                        request: CameraCaptureRequest,
                    ): CameraSequencePreparationResult {
                        preparedCameraId = request.cameraId
                        return CameraSequencePreparationResult.Ready
                    }

                    override suspend fun capture(
                        frame: CameraCaptureFrame,
                    ): CameraFrameCaptureResult {
                        frame.output.imageFile.writeBytes(byteArrayOf(1, 2, 3))
                        return CameraFrameCaptureResult.Captured
                    }

                    override suspend fun close() = Unit
                }
            },
        )

        val result = factory.create(recovery).capture(0, recovery.plan.instructions.single())

        assertEquals(CameraCaptureResult.Captured, result)
        assertEquals("rear-main", preparedCameraId)
        val outputs = root.resolve("session").listFiles().orEmpty()
        assertEquals(1, outputs.size)
        assertTrue(outputs.single().length() > 0)
    }

    @Test
    fun backendFailureRemovesEveryReservedOutput() = runBlocking {
        val root = Files.createTempDirectory("camera-graph-failure").toFile()
        val recovery = recovery()
        val factory = ProductionIndexedCameraFactory(
            outputRootDirectory = root,
            selectedCamera = { compatibleCamera() },
            backendFactory = CameraCaptureSequenceBackendFactory {
                object : CameraCaptureSequenceBackend {
                    override suspend fun prepare(
                        request: CameraCaptureRequest,
                    ) = CameraSequencePreparationResult.Ready

                    override suspend fun capture(
                        frame: CameraCaptureFrame,
                    ) = CameraFrameCaptureResult.RecoverableFailure("camera busy")

                    override suspend fun close() = Unit
                }
            },
        )

        val result = factory.create(recovery).capture(0, recovery.plan.instructions.single())

        assertEquals(CameraCaptureResult.RecoverableError("camera busy"), result)
        assertTrue(root.resolve("session").listFiles().orEmpty().isEmpty())
    }

    private fun recovery(): CaptureServiceBootstrapResult.Ready {
        val instant = Instant.parse("2026-08-12T17:00:00Z")
        val plan = CapturePlan(
            startsAtUtc = instant,
            endsAtUtc = instant,
            instructions = listOf(
                CaptureInstruction(
                    instantUtc = instant,
                    phase = CapturePhase.PARTIAL,
                    exposureStrategy = ExposureStrategy.FILTERED_PARTIAL,
                ),
            ),
        )
        val coordinator = CaptureSessionCoordinator.arm(
            sessionId = "session",
            plan = plan,
            nowUtc = instant.minusSeconds(60),
            checkpointStore = MemoryCheckpointStore(),
        )
        return CaptureServiceBootstrapResult.Ready(plan, coordinator, CaptureServiceState.PAUSED)
    }

    private fun compatibleCamera() = CameraCapabilities(
        cameraId = "rear-main",
        facing = LensFacing.BACK,
        sensorOrientationDegrees = 90,
        minimumZoomRatio = 1f,
        maximumZoomRatio = 8f,
        jpegSizes = listOf(CameraOutputSize(4032, 3024)),
        rawSupported = true,
        manualSensorSupported = true,
        manualFocusSupported = true,
        exposureCompensationRange = -4..4,
    )

    private class MemoryCheckpointStore : CaptureCheckpointStore {
        private var checkpoint: CaptureSessionCheckpoint? = null
        override fun write(checkpoint: CaptureSessionCheckpoint) { this.checkpoint = checkpoint }
        override fun read(): CheckpointReadResult = checkpoint
            ?.let(CheckpointReadResult::Loaded)
            ?: CheckpointReadResult.Missing
        override fun clear(): Boolean { checkpoint = null; return true }
    }
}
