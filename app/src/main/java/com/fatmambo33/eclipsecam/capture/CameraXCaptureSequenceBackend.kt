package com.fatmambo33.eclipsecam.capture

/**
 * Testable CameraX boundary for binding controls and executing one JPEG sequence.
 *
 * Production implementations own the bound Camera, CameraControl, Camera2 interop controls, and
 * ImageCapture. Every method completes only after CameraX confirms the requested state.
 */
interface CameraXCaptureControlPort {
    suspend fun bind(cameraId: String, width: Int, height: Int): CameraXControlResult

    suspend fun setContinuousAutoFocus(): CameraXControlResult

    suspend fun setManualInfinityFocus(): CameraXControlResult

    suspend fun meterAndLockWhiteBalance(): CameraXControlResult

    suspend fun setExposureCompensation(steps: Int): CameraXControlResult

    suspend fun setRelativeManualExposure(offsetEv: Int): CameraXControlResult

    suspend fun captureJpeg(frame: CameraCaptureFrame): CameraFrameCaptureResult =
        CameraFrameCaptureResult.FatalFailure("Concrete CameraX ImageCapture is unavailable.")

    suspend fun restore(): CameraXControlResult
}

sealed interface CameraXControlResult {
    data object Applied : CameraXControlResult
    data class RecoverableFailure(val reason: String) : CameraXControlResult
    data class FatalFailure(val reason: String) : CameraXControlResult
}

/** Coordinates a concrete CameraX control port with transactional sequence execution. */
class CameraXCaptureSequenceBackend(
    private val controls: CameraXCaptureControlPort,
    private val jpegCapture: CameraXJpegCapture? = null,
) : CameraCaptureSequenceBackend {
    private var preparedRequest: CameraCaptureRequest? = null

    override suspend fun prepare(request: CameraCaptureRequest): CameraSequencePreparationResult {
        preparedRequest = null
        controlPreparation(controls.bind(request.cameraId, request.outputSize.width, request.outputSize.height))
            ?.let { return it }

        val focusResult = when (request.focusMode) {
            CaptureFocusMode.CONTINUOUS_AUTO -> controls.setContinuousAutoFocus()
            CaptureFocusMode.MANUAL_INFINITY -> controls.setManualInfinityFocus()
        }
        controlPreparation(focusResult)?.let { return it }

        controlPreparation(controls.meterAndLockWhiteBalance())?.let { return it }
        preparedRequest = request
        return CameraSequencePreparationResult.Ready
    }

    override suspend fun capture(frame: CameraCaptureFrame): CameraFrameCaptureResult {
        if (preparedRequest == null) {
            return CameraFrameCaptureResult.FatalFailure("CameraX capture sequence was not prepared.")
        }
        val exposureResult = when (val exposure = frame.exposure) {
            is CaptureExposureStep.Compensation -> controls.setExposureCompensation(exposure.steps)
            is CaptureExposureStep.RelativeManualEv -> controls.setRelativeManualExposure(exposure.offset)
        }
        controlFrame(exposureResult)?.let { return it }
        return jpegCapture?.capture(frame) ?: controls.captureJpeg(frame)
    }

    override suspend fun close() {
        preparedRequest = null
        when (val result = controls.restore()) {
            CameraXControlResult.Applied -> Unit
            is CameraXControlResult.RecoverableFailure -> error(result.reason)
            is CameraXControlResult.FatalFailure -> error(result.reason)
        }
    }

    private fun controlPreparation(result: CameraXControlResult): CameraSequencePreparationResult? =
        when (result) {
            CameraXControlResult.Applied -> null
            is CameraXControlResult.RecoverableFailure -> CameraSequencePreparationResult.RecoverableFailure(result.reason)
            is CameraXControlResult.FatalFailure -> CameraSequencePreparationResult.FatalFailure(result.reason)
        }

    private fun controlFrame(result: CameraXControlResult): CameraFrameCaptureResult? = when (result) {
        CameraXControlResult.Applied -> null
        is CameraXControlResult.RecoverableFailure -> CameraFrameCaptureResult.RecoverableFailure(result.reason)
        is CameraXControlResult.FatalFailure -> CameraFrameCaptureResult.FatalFailure(result.reason)
    }
}
