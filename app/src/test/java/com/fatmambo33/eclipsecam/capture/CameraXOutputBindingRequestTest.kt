package com.fatmambo33.eclipsecam.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CameraXOutputBindingRequestTest {
    @Test
    fun acceptsPhysicalCameraAndPositiveOutputSize() {
        val request = CameraXOutputBindingRequest(cameraId = "0", width = 4080, height = 3072)

        assertEquals("0", request.cameraId)
        assertEquals(4080, request.width)
        assertEquals(3072, request.height)
    }

    @Test
    fun rejectsBlankCameraId() {
        assertThrows(IllegalArgumentException::class.java) {
            CameraXOutputBindingRequest(cameraId = " ", width = 1920, height = 1080)
        }
    }

    @Test
    fun rejectsNonPositiveDimensions() {
        assertThrows(IllegalArgumentException::class.java) {
            CameraXOutputBindingRequest(cameraId = "0", width = 0, height = 1080)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CameraXOutputBindingRequest(cameraId = "0", width = 1920, height = -1)
        }
    }
}
