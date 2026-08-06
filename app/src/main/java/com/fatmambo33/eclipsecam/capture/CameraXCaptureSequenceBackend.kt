package com.fatmambo33.eclipsecam.capture

/**
 * Testable CameraX control boundary for preparing and restoring one capture sequence.
 *
 * Production implementations own CameraX binding and camera-control calls. Every method completes
 * only after CameraX confirms the requested state, allowing the sequence backend to remain fully
 * suspending and deterministic.
 */
interface CameraXCaptureControlPort {
    suspend fun bind(cameraId: String, width: Int, height: Int): CameraXControlResult

    suspend fun setContinuousAutoFocus(): CameraXControlResult

    suspend fun setManualInfinityFocus(): CameraXControlResult

    suspend fun meterAndLockWhiteBalance(): CameraXControlResult

    suspend fun setExposureCompensation(steps: Int): CameraXControlResult

    suspend fun setRelativeManualExposure(offsetEv: Int): CameraXControlResult

    suspend fun restore(): CameraXControlResult
}

sealed interface CameraXControlResult {
    data object Applied : CameraXControlResult
    data class RecoverableFailure(val reason: String) : CameraXControlResult
    data class FatalFailure(val reason: String) : CameraXControlResult
}

/**
 * Concrete sequence backend that coordinates CameraX controls with transactional JPEG capture.
 *
 * Preparation binds the requested camera/output, applies the selected focus policy, then meters and
 * locks white balance. Each frame applies its exposure state before awaiting the JPEG callback.
 * Temporary controls are restored from [close], which is always invoked by
 * [CameraCaptureSequenceExecutor].
 */
class CameraXCaptureSequenceBackend(
    private val controls: CameraXCaptureControlPort,
    private val jpegCapture: CameraXJpegCapture,
) : CameraCaptureSequenceBackend {
    private var preparedRequest: CameraCaptureRequest? = null

    override suspend fun prepare(request: CameraCaptureRequest): CameraSequencePreparationResult {
        preparedRequest = null

        controlPreparation(
            controls.bind(
                cameraId = request.cameraId,
                width = request.outputSize.width,
                height = request.outputSize.height,
            ),
        )?.let { return it }

        val focusResult = when (request.focusMode) {
            CaptureFocusMode.CONTINUOUS_AUTO -> controls.setContinuousAutoFocus()
            CaptureFocusMode.MANUAL_INFINITY -> controls.setManualInfinityFocus()
        }
        controlPreparation(focusResult)?.let { return it }

        when (request.whiteBalanceMode) {
            CaptureWhiteBalanceMode.AUTO_LOCK_AFTER_METERING ->
                controlPreparation(controls.meterAndLockWhiteBalance())?.let { return it }
        }

        preparedRequest = request
        return CameraSequencePreparationResult.Ready
    }

    override suspend fun capture(frame: CameraCaptureFrame): CameraFrameCaptureResult {
        if (preparedRequest == null) {
            return CameraFrameCaptureResult.FatalFailure(
                "CameraX capture sequence was not prepared.",
            )
        }

        val exposureResult = when (val exposure = frame.exposure) {
            is CaptureExposureStep.Compensation ->
                controls.setExposureCompensation(exposure.steps)
            is CaptureExposureStep.RelativeManualEv ->
                controls.setRelativeManualExposure(exposure.offset)
        }
        controlFrame(exposureResult)?.let { return it }

        return jpegCapture.capture(frame)
    }

    override suspend fun close() {
        preparedRequest = null
        when (val result = controls.restore()) {
            CameraXControlResult.Applied -> Unit
            is CameraXControlResult.RecoverableFailure -> error(result.reason)
            is CameraXControlResult.FatalFailure -> error(result.reason)
        }
    }

    private fun controlPreparation(
        result: CameraXControlResult,
    ): CameraSequencePreparationResult? = when (result) {
        CameraXControlResult.Applied -> null
        is CameraXControlResult.RecoverableFailure ->
            CameraSequencePreparationResult.RecoverableFailure(result.reason)
        is CameraXControlResult.FatalFailure ->
            CameraSequencePreparationResult.FatalFailure(result.reason)
    }

    private fun controlFrame(result: CameraXControlResult): CameraFrameCaptureResult? = when (result) {
        CameraXControlResult.Applied -> null
        is CameraXControlResult.RecoverableFailure ->
            CameraFrameCaptureResult.RecoverableFailure(result.reason)
        is CameraXControlResult.FatalFailure ->
            CameraFrameCaptureResult.FatalFailure(result.reason)
    }
}
