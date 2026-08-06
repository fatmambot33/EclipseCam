package com.fatmambo33.eclipsecam.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessCameraProviderCaptureBindingTest {
    @Test
    fun `binding request requires exact non-empty camera and positive size`() {
        CameraXCaptureBindingRequest(cameraId = "0", width = 4032, height = 3024)

        assertFails { CameraXCaptureBindingRequest(cameraId = "", width = 4032, height = 3024) }
        assertFails { CameraXCaptureBindingRequest(cameraId = "0", width = 0, height = 3024) }
        assertFails { CameraXCaptureBindingRequest(cameraId = "0", width = 4032, height = -1) }
    }

    @Test
    fun `exact camera matcher never falls back to another lens`() {
        assertTrue(ExactCameraIdMatcher.matches("2", "2"))
        assertFalse(ExactCameraIdMatcher.matches("2", "0"))
        assertFalse(ExactCameraIdMatcher.matches("", "0"))
    }

    private fun assertFails(block: () -> Unit) {
        val failure = runCatching(block).exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
