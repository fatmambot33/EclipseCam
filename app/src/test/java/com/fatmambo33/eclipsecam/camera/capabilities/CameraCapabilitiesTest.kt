package com.fatmambo33.eclipsecam.camera.capabilities

import android.hardware.camera2.CameraCharacteristics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCapabilitiesTest {
    @Test
    fun mapsAndSortsCompleteCapabilitySnapshot() {
        val result = mapCameraCapabilities(
            CameraHardwareSnapshot(
                cameraId = "rear-wide",
                lensFacingValue = CameraCharacteristics.LENS_FACING_BACK,
                sensorOrientationDegrees = 90,
                minimumZoomRatio = 0.5f,
                maximumZoomRatio = 8f,
                maximumDigitalZoom = 6f,
                jpegSizes = listOf(
                    CameraOutputSize(1920, 1080),
                    CameraOutputSize(4032, 3024),
                    CameraOutputSize(1920, 1080),
                ),
                rawSupported = true,
                manualSensorSupported = true,
                minimumFocusDistanceDiopters = 10f,
                exposureCompensationLower = -4,
                exposureCompensationUpper = 4,
            ),
        )

        assertEquals(LensFacing.BACK, result.facing)
        assertEquals(90, result.sensorOrientationDegrees)
        assertEquals(0.5f, result.minimumZoomRatio)
        assertEquals(8f, result.maximumZoomRatio)
        assertEquals(listOf(CameraOutputSize(4032, 3024), CameraOutputSize(1920, 1080)), result.jpegSizes)
        assertTrue(result.rawSupported)
        assertTrue(result.manualSensorSupported)
        assertTrue(result.manualFocusSupported)
        assertEquals(-4..4, result.exposureCompensationRange)
    }

    @Test
    fun exposesMissingAndInvalidFeaturesHonestly() {
        val result = mapCameraCapabilities(
            CameraHardwareSnapshot(
                cameraId = "unknown",
                lensFacingValue = null,
                sensorOrientationDegrees = 45,
                minimumZoomRatio = Float.NaN,
                maximumZoomRatio = 0f,
                maximumDigitalZoom = null,
                jpegSizes = emptyList(),
                rawSupported = false,
                manualSensorSupported = false,
                minimumFocusDistanceDiopters = 0f,
                exposureCompensationLower = 3,
                exposureCompensationUpper = -3,
            ),
        )

        assertEquals(LensFacing.UNKNOWN, result.facing)
        assertEquals(0, result.sensorOrientationDegrees)
        assertEquals(1f, result.minimumZoomRatio)
        assertEquals(1f, result.maximumZoomRatio)
        assertTrue(result.jpegSizes.isEmpty())
        assertFalse(result.rawSupported)
        assertFalse(result.manualSensorSupported)
        assertFalse(result.manualFocusSupported)
        assertNull(result.exposureCompensationRange)
    }

    @Test
    fun fallsBackToDigitalZoomAndNormalizesAllLensFacings() {
        val result = mapCameraCapabilities(
            CameraHardwareSnapshot(
                cameraId = "external",
                lensFacingValue = CameraCharacteristics.LENS_FACING_EXTERNAL,
                sensorOrientationDegrees = 270,
                minimumZoomRatio = null,
                maximumZoomRatio = null,
                maximumDigitalZoom = 4f,
                jpegSizes = listOf(CameraOutputSize(640, 480)),
                rawSupported = false,
                manualSensorSupported = false,
                minimumFocusDistanceDiopters = null,
                exposureCompensationLower = 0,
                exposureCompensationUpper = 0,
            ),
        )

        assertEquals(LensFacing.EXTERNAL, result.facing)
        assertEquals(270, result.sensorOrientationDegrees)
        assertEquals(1f, result.minimumZoomRatio)
        assertEquals(4f, result.maximumZoomRatio)
        assertEquals(0..0, result.exposureCompensationRange)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidOutputDimensions() {
        CameraOutputSize(0, 1080)
    }
}
