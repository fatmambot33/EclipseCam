package com.fatmambo33.eclipsecam.capture

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraXCaptureFailureAdapterTest {
    @Test
    fun transientCameraXErrorsRemainRecoverable() {
        listOf(
            ImageCapture.ERROR_CAMERA_CLOSED,
            ImageCapture.ERROR_CAPTURE_FAILED,
        ).forEach { errorCode ->
            val result = CameraXCaptureFailureAdapter.classify(
                ImageCaptureException(errorCode, "retryable", null),
            )

            assertEquals(
                CameraFrameCaptureResult.RecoverableFailure("retryable"),
                result,
            )
        }
    }

    @Test
    fun unsafeCameraXErrorsAreFatal() {
        listOf(
            ImageCapture.ERROR_FILE_IO,
            ImageCapture.ERROR_INVALID_CAMERA,
            ImageCapture.ERROR_UNKNOWN,
        ).forEach { errorCode ->
            val result = CameraXCaptureFailureAdapter.classify(
                ImageCaptureException(errorCode, "fatal", null),
            )

            assertEquals(CameraFrameCaptureResult.FatalFailure("fatal"), result)
        }
    }

    @Test
    fun unknownFutureCameraXErrorFailsClosed() {
        val result = CameraXCaptureFailureAdapter.classify(
            ImageCaptureException(Int.MAX_VALUE, "new CameraX error", null),
        )

        assertEquals(
            CameraFrameCaptureResult.FatalFailure("new CameraX error"),
            result,
        )
    }

    @Test
    fun blankCameraXMessageUsesStableFallback() {
        val result = CameraXCaptureFailureAdapter.classify(
            ImageCaptureException(ImageCapture.ERROR_FILE_IO, "   ", null),
        )

        assertTrue(result is CameraFrameCaptureResult.FatalFailure)
        assertEquals(
            "Unable to write the captured frame.",
            (result as CameraFrameCaptureResult.FatalFailure).reason,
        )
    }
}
