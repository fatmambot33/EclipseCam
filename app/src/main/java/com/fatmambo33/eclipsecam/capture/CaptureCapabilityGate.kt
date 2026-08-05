package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.camera.capabilities.CameraCapabilities
import com.fatmambo33.eclipsecam.camera.capabilities.LensFacing

enum class CaptureCapabilityBlocker {
    NO_BACK_CAMERA,
    NO_JPEG_OUTPUT,
    BRACKETING_UNSUPPORTED,
}

data class CaptureCapabilityDecision(
    val camera: CameraCapabilities?,
    val blockers: Set<CaptureCapabilityBlocker>,
) {
    val supported: Boolean = camera != null && blockers.isEmpty()
}

/**
 * Selects a camera that can execute the complete plan without promising unsupported controls.
 *
 * JPEG output is mandatory for every plan. Contact and totality bracket instructions require
 * either manual-sensor support or exposure compensation spanning at least -2..2 EV steps.
 */
class CaptureCapabilityGate {
    fun evaluate(
        plan: CapturePlan,
        cameras: List<CameraCapabilities>,
    ): CaptureCapabilityDecision {
        val backCameras = cameras.filter { it.facing == LensFacing.BACK }
        if (backCameras.isEmpty()) {
            return CaptureCapabilityDecision(null, setOf(CaptureCapabilityBlocker.NO_BACK_CAMERA))
        }

        val requiresBracketing = plan.instructions.any {
            it.exposureStrategy == ExposureStrategy.CONTACT_BRACKET ||
                it.exposureStrategy == ExposureStrategy.TOTALITY_BRACKET
        }
        val evaluated = backCameras.map { camera ->
            camera to buildSet {
                if (camera.jpegSizes.isEmpty()) add(CaptureCapabilityBlocker.NO_JPEG_OUTPUT)
                if (requiresBracketing && !camera.supportsConservativeBracketing()) {
                    add(CaptureCapabilityBlocker.BRACKETING_UNSUPPORTED)
                }
            }
        }
        val supported = evaluated
            .filter { it.second.isEmpty() }
            .maxByOrNull { (camera, _) -> camera.jpegSizes.maxOfOrNull { it.pixelCount } ?: 0L }
        if (supported != null) {
            return CaptureCapabilityDecision(supported.first, emptySet())
        }

        return CaptureCapabilityDecision(
            camera = null,
            blockers = evaluated.flatMapTo(linkedSetOf()) { it.second },
        )
    }

    private fun CameraCapabilities.supportsConservativeBracketing(): Boolean {
        if (manualSensorSupported) return true
        val range = exposureCompensationRange ?: return false
        return range.first <= -2 && range.last >= 2
    }
}
