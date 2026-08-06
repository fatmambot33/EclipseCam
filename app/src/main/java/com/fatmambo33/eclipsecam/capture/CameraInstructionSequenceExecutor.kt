package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.camera.capabilities.CameraCapabilities

fun interface CameraCaptureSequenceBackendFactory {
    fun create(request: CameraCaptureRequest): CameraCaptureSequenceBackend
}

/**
 * Bridges one phase-sensitive capture instruction into the transactional camera sequence pipeline.
 *
 * The executor derives a conservative request from the selected camera capabilities, reserves every
 * required local JPEG destination, creates a fresh backend for the sequence, and maps the
 * transactional outcome back to the runtime capture contract. Unsupported hardware and planning
 * failures fail closed instead of silently recording a capture that did not happen.
 */
class CameraInstructionSequenceExecutor(
    private val sessionId: String,
    private val selectedCamera: () -> CameraCapabilities,
    private val requestPolicy: CameraCaptureRequestPolicy,
    private val sequencePlanner: CameraCaptureSequencePlanner,
    private val sequenceExecutor: CameraCaptureSequenceExecutor,
    private val backendFactory: CameraCaptureSequenceBackendFactory,
) {
    init {
        require(sessionId.isNotBlank()) { "Session ID must not be blank." }
    }

    suspend fun capture(
        instructionIndex: Int,
        instruction: CaptureInstruction,
    ): CameraCaptureResult {
        require(instructionIndex >= 0) { "Instruction index must be non-negative." }

        val request = when (val result = requestPolicy.create(instruction, selectedCamera())) {
            is CameraCaptureRequestResult.Ready -> result.request
            is CameraCaptureRequestResult.Unsupported ->
                return CameraCaptureResult.FatalError(result.reason)
        }

        val sequence = try {
            sequencePlanner.plan(
                sessionId = sessionId,
                instructionIndex = instructionIndex,
                capturedAtUtc = instruction.instantUtc,
                request = request,
            )
        } catch (error: RuntimeException) {
            return CameraCaptureResult.FatalError(
                error.message ?: "Unable to reserve camera sequence outputs.",
            )
        }

        return when (
            val result = sequenceExecutor.execute(
                sequence = sequence,
                backend = backendFactory.create(request),
            )
        ) {
            is CameraCaptureSequenceResult.Completed -> CameraCaptureResult.Captured
            is CameraCaptureSequenceResult.RecoverableFailure ->
                CameraCaptureResult.RecoverableError(result.reason)
            is CameraCaptureSequenceResult.FatalFailure ->
                CameraCaptureResult.FatalError(result.reason)
        }
    }
}
