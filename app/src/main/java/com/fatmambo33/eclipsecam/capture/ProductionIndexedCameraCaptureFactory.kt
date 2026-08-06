package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.camera.capabilities.CameraCapabilities

/**
 * Composes the framework-neutral production capture pipeline around one shared output allocator.
 *
 * The same allocator is used for reservation, transactional execution, and failure cleanup. Camera
 * capability lookup and backend construction are supplied by Android composition code; exceptions
 * from unavailable production dependencies are converted into fatal capture results so the
 * foreground runtime fails closed rather than reporting a capture that never happened.
 */
class ProductionIndexedCameraCaptureFactory(
    private val selectedCamera: () -> CameraCapabilities,
    private val outputAllocator: CaptureOutputAllocator,
    private val backendFactory: CameraCaptureSequenceBackendFactory,
    private val requestPolicy: CameraCaptureRequestPolicy = CameraCaptureRequestPolicy(),
) {
    fun create(sessionId: String): IndexedCameraCapturePort {
        require(sessionId.isNotBlank()) { "Session ID must not be blank." }

        val executor = CameraInstructionSequenceExecutor(
            sessionId = sessionId,
            selectedCamera = selectedCamera,
            requestPolicy = requestPolicy,
            sequencePlanner = CameraCaptureSequencePlanner(outputAllocator),
            sequenceExecutor = CameraCaptureSequenceExecutor(outputAllocator),
            backendFactory = backendFactory,
        )
        return IndexedCameraCapturePort { instructionIndex, instruction ->
            try {
                executor.capture(instructionIndex, instruction)
            } catch (error: RuntimeException) {
                CameraCaptureResult.FatalError(
                    error.message ?: "Production camera capture dependencies are unavailable.",
                )
            }
        }
    }
}
