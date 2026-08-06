package com.fatmambo33.eclipsecam.capture

sealed interface CameraSequencePreparationResult {
    data object Ready : CameraSequencePreparationResult
    data class RecoverableFailure(val reason: String) : CameraSequencePreparationResult
    data class FatalFailure(val reason: String) : CameraSequencePreparationResult
}

sealed interface CameraFrameCaptureResult {
    data object Captured : CameraFrameCaptureResult
    data class RecoverableFailure(val reason: String) : CameraFrameCaptureResult
    data class FatalFailure(val reason: String) : CameraFrameCaptureResult
}

sealed interface CameraCaptureSequenceResult {
    data class Completed(val outputs: List<CaptureOutput>) : CameraCaptureSequenceResult
    data class RecoverableFailure(val reason: String) : CameraCaptureSequenceResult
    data class FatalFailure(val reason: String) : CameraCaptureSequenceResult
}

/**
 * Framework-neutral boundary implemented by the Android CameraX adapter.
 *
 * CameraX preparation and image capture complete through asynchronous callbacks, so each operation
 * is suspending. Implementations must resume only after the requested camera state or JPEG write is
 * complete. [close] must restore or release temporary controls and is always invoked.
 */
interface CameraCaptureSequenceBackend {
    suspend fun prepare(request: CameraCaptureRequest): CameraSequencePreparationResult
    suspend fun capture(frame: CameraCaptureFrame): CameraFrameCaptureResult
    suspend fun close()
}

/**
 * Executes one pre-reserved capture sequence as an all-or-nothing local-media transaction.
 *
 * A successful sequence preserves every output for later indexing. Any preparation failure,
 * incomplete frame, backend exception or close failure releases every reserved output so Gallery
 * cannot discover a partial exposure bracket. Recoverable camera failures remain recoverable only
 * when output cleanup succeeds; cleanup uncertainty is always fatal.
 */
class CameraCaptureSequenceExecutor(
    private val outputAllocator: CaptureOutputAllocator,
) {
    suspend fun execute(
        sequence: CameraCaptureSequence,
        backend: CameraCaptureSequenceBackend,
    ): CameraCaptureSequenceResult {
        var result: CameraCaptureSequenceResult = try {
            when (val preparation = backend.prepare(sequence.request)) {
                CameraSequencePreparationResult.Ready -> captureFrames(sequence, backend)
                is CameraSequencePreparationResult.RecoverableFailure ->
                    CameraCaptureSequenceResult.RecoverableFailure(preparation.reason)
                is CameraSequencePreparationResult.FatalFailure ->
                    CameraCaptureSequenceResult.FatalFailure(preparation.reason)
            }
        } catch (error: RuntimeException) {
            CameraCaptureSequenceResult.FatalFailure(
                error.message ?: "Camera sequence execution failed.",
            )
        }

        try {
            backend.close()
        } catch (error: RuntimeException) {
            result = CameraCaptureSequenceResult.FatalFailure(
                error.message ?: "Camera sequence cleanup failed.",
            )
        }

        return if (result is CameraCaptureSequenceResult.Completed) {
            result
        } else {
            cleanupFailedSequence(sequence, result)
        }
    }

    private suspend fun captureFrames(
        sequence: CameraCaptureSequence,
        backend: CameraCaptureSequenceBackend,
    ): CameraCaptureSequenceResult {
        for (frame in sequence.frames) {
            when (val captured = backend.capture(frame)) {
                CameraFrameCaptureResult.Captured -> Unit
                is CameraFrameCaptureResult.RecoverableFailure ->
                    return CameraCaptureSequenceResult.RecoverableFailure(captured.reason)
                is CameraFrameCaptureResult.FatalFailure ->
                    return CameraCaptureSequenceResult.FatalFailure(captured.reason)
            }
        }
        return CameraCaptureSequenceResult.Completed(sequence.frames.map { it.output })
    }

    private fun cleanupFailedSequence(
        sequence: CameraCaptureSequence,
        result: CameraCaptureSequenceResult,
    ): CameraCaptureSequenceResult {
        val cleanupSucceeded = sequence.frames
            .map { outputAllocator.release(it.output) }
            .all { it }
        return if (cleanupSucceeded) {
            result
        } else {
            CameraCaptureSequenceResult.FatalFailure(
                "Unable to clean incomplete camera sequence outputs.",
            )
        }
    }
}
