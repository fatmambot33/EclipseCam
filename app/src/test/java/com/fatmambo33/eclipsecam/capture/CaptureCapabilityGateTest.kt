package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.camera.capabilities.CameraCapabilities
import com.fatmambo33.eclipsecam.camera.capabilities.CameraOutputSize
import com.fatmambo33.eclipsecam.camera.capabilities.LensFacing
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureCapabilityGateTest {
    private val start = Instant.parse("2026-08-12T17:00:00Z")
    private val partialPlan = CapturePlan(
        start,
        start,
        listOf(CaptureInstruction(start, CapturePhase.PARTIAL, ExposureStrategy.FILTERED_PARTIAL)),
    )
    private val bracketPlan = CapturePlan(
        start,
        start,
        listOf(CaptureInstruction(start, CapturePhase.TOTALITY, ExposureStrategy.TOTALITY_BRACKET)),
    )

    @Test
    fun rejectsPlanWhenNoBackCameraExists() {
        val decision = CaptureCapabilityGate().evaluate(partialPlan, listOf(camera(facing = LensFacing.FRONT)))

        assertFalse(decision.supported)
        assertEquals(setOf(CaptureCapabilityBlocker.NO_BACK_CAMERA), decision.blockers)
    }

    @Test
    fun rejectsCameraWithoutJpegOutput() {
        val decision = CaptureCapabilityGate().evaluate(partialPlan, listOf(camera(jpegSizes = emptyList())))

        assertFalse(decision.supported)
        assertTrue(CaptureCapabilityBlocker.NO_JPEG_OUTPUT in decision.blockers)
    }

    @Test
    fun partialPlanDoesNotRequireManualControls() {
        val decision = CaptureCapabilityGate().evaluate(partialPlan, listOf(camera()))

        assertTrue(decision.supported)
        assertEquals("0", decision.camera?.cameraId)
    }

    @Test
    fun bracketPlanRequiresManualSensorOrWideCompensation() {
        val unsupported = CaptureCapabilityGate().evaluate(bracketPlan, listOf(camera()))
        val compensated = CaptureCapabilityGate().evaluate(
            bracketPlan,
            listOf(camera(exposureCompensationRange = -3..3)),
        )
        val manual = CaptureCapabilityGate().evaluate(
            bracketPlan,
            listOf(camera(manualSensorSupported = true)),
        )

        assertFalse(unsupported.supported)
        assertTrue(CaptureCapabilityBlocker.BRACKETING_UNSUPPORTED in unsupported.blockers)
        assertTrue(compensated.supported)
        assertTrue(manual.supported)
    }

    @Test
    fun selectsHighestResolutionCompatibleBackCamera() {
        val decision = CaptureCapabilityGate().evaluate(
            bracketPlan,
            listOf(
                camera(cameraId = "wide", manualSensorSupported = true, jpegSizes = listOf(CameraOutputSize(4000, 3000))),
                camera(cameraId = "tele", exposureCompensationRange = -2..2, jpegSizes = listOf(CameraOutputSize(8000, 6000))),
                camera(cameraId = "front", facing = LensFacing.FRONT, manualSensorSupported = true),
            ),
        )

        assertTrue(decision.supported)
        assertEquals("tele", decision.camera?.cameraId)
    }

    private fun camera(
        cameraId: String = "0",
        facing: LensFacing = LensFacing.BACK,
        jpegSizes: List<CameraOutputSize> = listOf(CameraOutputSize(4000, 3000)),
        manualSensorSupported: Boolean = false,
        exposureCompensationRange: IntRange? = null,
    ) = CameraCapabilities(
        cameraId = cameraId,
        facing = facing,
        sensorOrientationDegrees = 90,
        minimumZoomRatio = 1f,
        maximumZoomRatio = 4f,
        jpegSizes = jpegSizes,
        rawSupported = false,
        manualSensorSupported = manualSensorSupported,
        manualFocusSupported = false,
        exposureCompensationRange = exposureCompensationRange,
    )
}
