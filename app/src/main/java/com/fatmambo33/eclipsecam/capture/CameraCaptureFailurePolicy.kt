package com.fatmambo33.eclipsecam.capture

enum class CameraCaptureFailureKind {
    CAMERA_CLOSED,
    FILE_IO,
    CAPTURE_FAILED,
    INVALID_CAMERA,
    UNKNOWN,
}

/** Maps Android camera failures to the transactional capture engine's stable result contract. */
object CameraCaptureFailurePolicy {
    fun classify(
        kind: CameraCaptureFailureKind,
        message: String?,
    ): CameraFrameCaptureResult {
        val reason = message?.takeIf(String::isNotBlank) ?: defaultReason(kind)
        return when (kind) {
            CameraCaptureFailureKind.CAMERA_CLOSED,
            CameraCaptureFailureKind.CAPTURE_FAILED ->
                CameraFrameCaptureResult.RecoverableFailure(reason)

            CameraCaptureFailureKind.FILE_IO,
            CameraCaptureFailureKind.INVALID_CAMERA,
            CameraCaptureFailureKind.UNKNOWN ->
                CameraFrameCaptureResult.FatalFailure(reason)
        }
    }

    private fun defaultReason(kind: CameraCaptureFailureKind): String = when (kind) {
        CameraCaptureFailureKind.CAMERA_CLOSED -> "Camera closed before the frame completed."
        CameraCaptureFailureKind.FILE_IO -> "Unable to write the captured frame."
        CameraCaptureFailureKind.CAPTURE_FAILED -> "Camera could not capture the frame."
        CameraCaptureFailureKind.INVALID_CAMERA -> "Selected camera is no longer available."
        CameraCaptureFailureKind.UNKNOWN -> "Unexpected camera capture failure."
    }
}
