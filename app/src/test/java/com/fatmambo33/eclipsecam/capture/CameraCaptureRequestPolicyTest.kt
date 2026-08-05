package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.camera.capabilities.CameraCapabilities
import com.fatmambo33.eclipsecam.camera.capabilities.CameraOutputSize
import com.fatmambo33.eclipsecam.camera.capabilities.LensFacing
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCaptureRequestPolicyTest {
    private val policy = CameraCaptureRequestPolicy()
    private val instant = Instant.parse("2026-08-12T17:45:51Z")

    @Test
    fun filteredPartialUsesLargestJpegAndSingleMeteredExposure() {
        val result = policy.create(
            instruction(ExposureStrategy.FILTERED_PARTIAL),
            camera(
                jpegSizes = listOf(CameraOutputSize(1920, 1080), CameraOutputSize(4032, 3024)),
                manualFocusSupported = true,
                exposureCompensationRange = -3..3,
            ),
        ) as CameraCaptureRequestResult.Ready

        assertEquals(CameraOutputSize(4032, 3024), result.request.outputSize)
        assertEquals(CaptureFocusMode.MANUAL_INFINITY, result.request.focusMode)
        assertEquals(
            CaptureExposureProgram.ExposureCompensation(listOf(0)),
            result.request.exposureProgram,
        )
        assertEquals(
            CaptureWhiteBalanceMode.AUTO_LOCK_AFTER_METERING,
            result.request.whiteBalanceMode,
        )
    }

    @Test
    fun contactAndTotalityUseSymmetricExposureCompensationBracket() {
        listOf(ExposureStrategy.CONTACT_BRACKET, ExposureStrategy.TOTALITY_BRACKET).forEach { strategy ->
            val result = policy.create(
                instruction(strategy),
                camera(exposureCompensationRange = -4..4),
            ) as CameraCaptureRequestResult.Ready

            assertEquals(
                CaptureExposureProgram.ExposureCompensation(listOf(-2, 0, 2)),
                result.request.exposureProgram,
            )
            assertEquals(CaptureFocusMode.CONTINUOUS_AUTO, result.request.focusMode)
        }
    }

    @Test
    fun manualSensorProvidesMeteredBracketFallback() {
        val result = policy.create(
            instruction(ExposureStrategy.TOTALITY_BRACKET),
            camera(
                manualSensorSupported = true,
                exposureCompensationRange = -1..1,
            ),
        ) as CameraCaptureRequestResult.Ready

        assertEquals(
            CaptureExposureProgram.MeteredManualBracket(listOf(-2, 0, 2)),
            result.request.exposureProgram,
        )
    }

    @Test
    fun bracketIsRejectedWhenNoSupportedControlExists() {
        val result = policy.create(
            instruction(ExposureStrategy.CONTACT_BRACKET),
            camera(exposureCompensationRange = -1..1),
        )

        assertTrue(result is CameraCaptureRequestResult.Unsupported)
    }

    @Test
    fun missingJpegOutputIsRejectedForEveryStrategy() {
        ExposureStrategy.entries.forEach { strategy ->
            val result = policy.create(
                instruction(strategy),
                camera(jpegSizes = emptyList(), manualSensorSupported = true),
            )

            assertTrue(result is CameraCaptureRequestResult.Unsupported)
        }
    }

    private fun instruction(strategy: ExposureStrategy) = CaptureInstruction(
        instantUtc = instant,
        phase = when (strategy) {
            ExposureStrategy.FILTERED_PARTIAL -> CapturePhase.PARTIAL
            ExposureStrategy.CONTACT_BRACKET -> CapturePhase.CONTACT_BURST
            ExposureStrategy.TOTALITY_BRACKET -> CapturePhase.TOTALITY
        },
        exposureStrategy = strategy,
    )

    private fun camera(
        jpegSizes: List<CameraOutputSize> = listOf(CameraOutputSize(4032, 3024)),
        manualSensorSupported: Boolean = false,
        manualFocusSupported: Boolean = false,
        exposureCompensationRange: IntRange? = null,
    ) = CameraCapabilities(
        cameraId = "0",
        facing = LensFacing.BACK,
        sensorOrientationDegrees = 90,
        minimumZoomRatio = 1f,
        maximumZoomRatio = 8f,
        jpegSizes = jpegSizes,
        rawSupported = false,
        manualSensorSupported = manualSensorSupported,
        manualFocusSupported = manualFocusSupported,
        exposureCompensationRange = exposureCompensationRange,
    )
}
