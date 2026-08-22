package com.fatmambo33.eclipsecam.camera.preview

import com.fatmambo33.eclipsecam.R
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraPreviewStateTest {
    @Test
    fun reportsPermissionRequirement() {
        assertEquals(
            CameraPreviewState.WaitingForPermission,
            reduceCameraPreviewState(CameraPreviewEvent.PermissionMissing),
        )
    }

    @Test
    fun reportsStartupAndSelectedLens() {
        assertEquals(
            CameraPreviewState.Starting,
            reduceCameraPreviewState(CameraPreviewEvent.StartRequested),
        )
        assertEquals(
            CameraPreviewState.Streaming(PreviewLens.BACK),
            reduceCameraPreviewState(CameraPreviewEvent.Started(PreviewLens.BACK)),
        )
        assertEquals(
            CameraPreviewState.Streaming(PreviewLens.FRONT),
            reduceCameraPreviewState(CameraPreviewEvent.Started(PreviewLens.FRONT)),
        )
    }

    @Test
    fun mapsPreviewFailuresToStableResourceIds() {
        assertEquals(
            R.string.camera_preview_failure_no_camera,
            cameraPreviewFailureMessageRes(CameraPreviewFailure.NO_USABLE_CAMERA),
        )
        assertEquals(
            R.string.camera_preview_failure_start,
            cameraPreviewFailureMessageRes(CameraPreviewFailure.START_FAILED),
        )
    }
}
