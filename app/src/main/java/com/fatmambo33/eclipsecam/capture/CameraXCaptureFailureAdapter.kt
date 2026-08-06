package com.fatmambo33.eclipsecam.capture

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException

/** Converts CameraX callback failures into the stable transactional capture result contract. */
object CameraXCaptureFailureAdapter {
    fun classify(exception: ImageCaptureException): CameraFrameCaptureResult =
        CameraCaptureFailurePolicy.classify(
            kind = exception.toFailureKind(),
            message = exception.message,
        )

    private fun ImageCaptureException.toFailureKind(): CameraCaptureFailureKind =
        when (imageCaptureError) {
            ImageCapture.ERROR_CAMERA_CLOSED -> CameraCaptureFailureKind.CAMERA_CLOSED
            ImageCapture.ERROR_FILE_IO -> CameraCaptureFailureKind.FILE_IO
            ImageCapture.ERROR_CAPTURE_FAILED -> CameraCaptureFailureKind.CAPTURE_FAILED
            ImageCapture.ERROR_INVALID_CAMERA -> CameraCaptureFailureKind.INVALID_CAMERA
            ImageCapture.ERROR_UNKNOWN -> CameraCaptureFailureKind.UNKNOWN
            else -> CameraCaptureFailureKind.UNKNOWN
        }
}
