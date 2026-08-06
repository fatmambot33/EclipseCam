package com.fatmambo33.eclipsecam.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCaptureFailurePolicyTest {
    @Test
    fun transientCameraFailuresRemainRecoverable() {
        listOf(
            CameraCaptureFailureKind.CAMERA_CLOSED,
            CameraCaptureFailureKind.CAPTURE_FAILED,
        ).forEach { kind ->
            assertTrue(
                CameraCaptureFailurePolicy.classify(kind, null) is
                    CameraFrameCaptureResult.RecoverableFailure,
            )
        }
    }

    @Test
    fun outputAndConfigurationFailuresAreFatal() {
        listOf(
            CameraCaptureFailureKind.FILE_IO,
            CameraCaptureFailureKind.INVALID_CAMERA,
            CameraCaptureFailureKind.UNKNOWN,
        ).forEach { kind ->
            assertTrue(
                CameraCaptureFailurePolicy.classify(kind, null) is
                    CameraFrameCaptureResult.FatalFailure,
            )
        }
    }

    @Test
    fun nonBlankBackendMessageIsPreserved() {
        val result = CameraCaptureFailurePolicy.classify(
            CameraCaptureFailureKind.FILE_IO,
            "Disk became read-only",
        ) as CameraFrameCaptureResult.FatalFailure

        assertEquals("Disk became read-only", result.reason)
    }

    @Test
    fun blankBackendMessageUsesStableFallback() {
        val result = CameraCaptureFailurePolicy.classify(
            CameraCaptureFailureKind.CAMERA_CLOSED,
            "  ",
        ) as CameraFrameCaptureResult.RecoverableFailure

        assertEquals("Camera closed before the frame completed.", result.reason)
    }
}
