package com.fatmambo33.eclipsecam.camera.capabilities

import android.hardware.camera2.CameraCharacteristics
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraCapabilitiesTest {
    @Test
    fun mapsKnownLensFacingValues() {
        assertEquals(LensFacing.FRONT, mapLensFacing(CameraCharacteristics.LENS_FACING_FRONT))
        assertEquals(LensFacing.BACK, mapLensFacing(CameraCharacteristics.LENS_FACING_BACK))
        assertEquals(LensFacing.EXTERNAL, mapLensFacing(CameraCharacteristics.LENS_FACING_EXTERNAL))
    }

    @Test
    fun mapsMissingOrUnknownLensFacingToUnknown() {
        assertEquals(LensFacing.UNKNOWN, mapLensFacing(null))
        assertEquals(LensFacing.UNKNOWN, mapLensFacing(Int.MAX_VALUE))
    }
}
