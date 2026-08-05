package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.ar.FrameFit
import com.fatmambo33.eclipsecam.device.health.CaptureReadiness
import com.fatmambo33.eclipsecam.device.health.DeviceHealthDecision
import com.fatmambo33.eclipsecam.device.orientation.OrientationConfidence
import com.fatmambo33.eclipsecam.device.orientation.OrientationState
import com.fatmambo33.eclipsecam.device.orientation.StabilityState

enum class ArmingBlockReason {
    PLAN_UNAVAILABLE,
    ORIENTATION_UNAVAILABLE,
    ORIENTATION_UNRELIABLE,
    PHONE_NOT_STABLE,
    TRAJECTORY_NOT_IN_FRAME,
    SOLAR_FILTER_NOT_ACKNOWLEDGED,
    DEVICE_HEALTH_BLOCKED,
}

enum class ArmingWarning {
    DEVICE_HEALTH_DEGRADED,
    ORIENTATION_MEDIUM_CONFIDENCE,
}

data class CaptureArmingInput(
    val plan: CapturePlanResult,
    val orientation: OrientationState,
    val frameFit: FrameFit,
    val solarFilterAcknowledged: Boolean,
    val deviceHealth: DeviceHealthDecision,
)

data class CaptureArmingDecision(
    val canArm: Boolean,
    val blockingReasons: Set<ArmingBlockReason>,
    val warnings: Set<ArmingWarning>,
)

/** Conservative gate used before a foreground capture session may start. */
object CaptureArmingGate {
    fun evaluate(input: CaptureArmingInput): CaptureArmingDecision {
        val blocks = linkedSetOf<ArmingBlockReason>()
        val warnings = linkedSetOf<ArmingWarning>()

        if (input.plan !is CapturePlanResult.Ready) blocks += ArmingBlockReason.PLAN_UNAVAILABLE
        when (input.orientation.confidence) {
            OrientationConfidence.UNAVAILABLE -> blocks += ArmingBlockReason.ORIENTATION_UNAVAILABLE
            OrientationConfidence.LOW -> blocks += ArmingBlockReason.ORIENTATION_UNRELIABLE
            OrientationConfidence.MEDIUM -> warnings += ArmingWarning.ORIENTATION_MEDIUM_CONFIDENCE
            OrientationConfidence.HIGH -> Unit
        }
        if (input.orientation.stability != StabilityState.STABLE) {
            blocks += ArmingBlockReason.PHONE_NOT_STABLE
        }
        if (input.frameFit != FrameFit.FITS) {
            blocks += ArmingBlockReason.TRAJECTORY_NOT_IN_FRAME
        }
        if (!input.solarFilterAcknowledged) {
            blocks += ArmingBlockReason.SOLAR_FILTER_NOT_ACKNOWLEDGED
        }
        when (input.deviceHealth.readiness) {
            CaptureReadiness.BLOCKED -> blocks += ArmingBlockReason.DEVICE_HEALTH_BLOCKED
            CaptureReadiness.DEGRADED -> warnings += ArmingWarning.DEVICE_HEALTH_DEGRADED
            CaptureReadiness.READY -> Unit
        }

        return CaptureArmingDecision(
            canArm = blocks.isEmpty(),
            blockingReasons = blocks,
            warnings = warnings,
        )
    }
}
