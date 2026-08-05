package com.fatmambo33.eclipsecam.camera.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun preservesConcreteFailureReason() {
        val state = reduceCameraPreviewState(CameraPreviewEvent.Failed("Camera is in use"))

        assertEquals(
            CameraPreviewState.Unavailable("Camera is in use"),
            state,
        )
    }

    @Test
    fun substitutesSafeReasonWhenFailureMessageIsMissing() {
        val nullReason = reduceCameraPreviewState(CameraPreviewEvent.Failed(null))
        val blankReason = reduceCameraPreviewState(CameraPreviewEvent.Failed("   "))

        assertTrue(nullReason is CameraPreviewState.Unavailable)
        assertEquals(nullReason, blankReason)
        assertEquals(
            "Camera preview is unavailable on this device.",
            (nullReason as CameraPreviewState.Unavailable).reason,
        )
    }
}
