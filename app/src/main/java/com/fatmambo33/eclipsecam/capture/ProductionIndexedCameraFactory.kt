package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.camera.capabilities.CameraCapabilities
import java.io.File

/**
 * Composes the transactional, app-private camera pipeline used by one recovered capture session.
 *
 * This factory owns output reservation, request derivation, sequence planning, rollback, and the
 * indexed CameraX bridge. It deliberately requires a concrete backend factory and selected camera;
 * no no-op production backend or implicit camera fallback is installed.
 */
class ProductionIndexedCameraFactory(
    private val outputRootDirectory: File,
    private val selectedCamera: () -> CameraCapabilities,
    private val backendFactory: CameraCaptureSequenceBackendFactory,
) : CaptureIndexedCameraFactory {
    override fun create(
        recovery: CaptureServiceBootstrapResult.Ready,
    ): IndexedCameraCapturePort {
        val sessionId = recovery.coordinator.snapshot().sessionId
        val outputStore = CaptureOutputStore(outputRootDirectory)
        val executor = CameraInstructionSequenceExecutor(
            sessionId = sessionId,
            selectedCamera = selectedCamera,
            requestPolicy = CameraCaptureRequestPolicy(),
            sequencePlanner = CameraCaptureSequencePlanner(outputStore),
            sequenceExecutor = CameraCaptureSequenceExecutor(outputStore),
            backendFactory = backendFactory,
        )
        return IndexedCameraCapturePort(executor::capture)
    }
}
