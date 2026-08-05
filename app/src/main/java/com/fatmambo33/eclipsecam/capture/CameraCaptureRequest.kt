package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.camera.capabilities.CameraCapabilities
import com.fatmambo33.eclipsecam.camera.capabilities.CameraOutputSize

enum class CaptureFocusMode {
    CONTINUOUS_AUTO,
    MANUAL_INFINITY,
}

enum class CaptureWhiteBalanceMode {
    AUTO_LOCK_AFTER_METERING,
}

sealed interface CaptureExposureProgram {
    data class ExposureCompensation(val steps: List<Int>) : CaptureExposureProgram {
        init {
            require(steps.isNotEmpty())
            require(steps == steps.distinct())
        }
    }

    data class MeteredManualBracket(val relativeEvOffsets: List<Int>) : CaptureExposureProgram {
        init {
            require(relativeEvOffsets.isNotEmpty())
            require(relativeEvOffsets == relativeEvOffsets.distinct())
            require(0 in relativeEvOffsets)
        }
    }
}

data class CameraCaptureRequest(
    val cameraId: String,
    val outputSize: CameraOutputSize,
    val focusMode: CaptureFocusMode,
    val whiteBalanceMode: CaptureWhiteBalanceMode,
    val exposureProgram: CaptureExposureProgram,
)

sealed interface CameraCaptureRequestResult {
    data class Ready(val request: CameraCaptureRequest) : CameraCaptureRequestResult
    data class Unsupported(val reason: String) : CameraCaptureRequestResult
}

/**
 * Converts a phase-sensitive instruction into a conservative framework-neutral camera request.
 *
 * The policy always selects the largest advertised JPEG output. Filtered partial phases use one
 * metered exposure. Contact and totality phases use a symmetric -2/0/+2 EV bracket when exposure
 * compensation supports it, otherwise they require manual-sensor support and defer the concrete
 * shutter/ISO values to a metered CameraX executor. Manual focus is represented as infinity only
 * when the capability inventory proves it is supported; all other cameras retain continuous AF.
 */
class CameraCaptureRequestPolicy {
    fun create(
        instruction: CaptureInstruction,
        camera: CameraCapabilities,
    ): CameraCaptureRequestResult {
        val outputSize = camera.jpegSizes.maxByOrNull(CameraOutputSize::pixelCount)
            ?: return CameraCaptureRequestResult.Unsupported("Selected camera has no JPEG output.")

        val exposureProgram = when (instruction.exposureStrategy) {
            ExposureStrategy.FILTERED_PARTIAL -> CaptureExposureProgram.ExposureCompensation(listOf(0))
            ExposureStrategy.CONTACT_BRACKET,
            ExposureStrategy.TOTALITY_BRACKET -> bracketProgram(camera)
                ?: return CameraCaptureRequestResult.Unsupported(
                    "Selected camera cannot execute the required exposure bracket.",
                )
        }

        return CameraCaptureRequestResult.Ready(
            CameraCaptureRequest(
                cameraId = camera.cameraId,
                outputSize = outputSize,
                focusMode = if (camera.manualFocusSupported) {
                    CaptureFocusMode.MANUAL_INFINITY
                } else {
                    CaptureFocusMode.CONTINUOUS_AUTO
                },
                whiteBalanceMode = CaptureWhiteBalanceMode.AUTO_LOCK_AFTER_METERING,
                exposureProgram = exposureProgram,
            ),
        )
    }

    private fun bracketProgram(camera: CameraCapabilities): CaptureExposureProgram? {
        val range = camera.exposureCompensationRange
        if (range != null && -2 in range && 0 in range && 2 in range) {
            return CaptureExposureProgram.ExposureCompensation(listOf(-2, 0, 2))
        }
        if (camera.manualSensorSupported) {
            return CaptureExposureProgram.MeteredManualBracket(listOf(-2, 0, 2))
        }
        return null
    }
}
