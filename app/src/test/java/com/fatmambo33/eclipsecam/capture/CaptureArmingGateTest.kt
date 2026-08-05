package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.ar.FrameFit
import com.fatmambo33.eclipsecam.device.health.CaptureReadiness
import com.fatmambo33.eclipsecam.device.health.DeviceHealthDecision
import com.fatmambo33.eclipsecam.device.health.DeviceHealthReason
import com.fatmambo33.eclipsecam.device.orientation.OrientationConfidence
import com.fatmambo33.eclipsecam.device.orientation.OrientationState
import com.fatmambo33.eclipsecam.device.orientation.PhoneOrientation
import com.fatmambo33.eclipsecam.device.orientation.StabilityState
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureArmingGateTest {
    @Test
    fun readyInputsCanArm() {
        val decision = CaptureArmingGate.evaluate(input())

        assertTrue(decision.canArm)
        assertTrue(decision.blockingReasons.isEmpty())
    }

    @Test
    fun unstablePhoneAndMissingFilterBlockArming() {
        val decision = CaptureArmingGate.evaluate(
            input(
                orientation = orientation(stability = StabilityState.MOVING),
                filterAcknowledged = false,
            ),
        )

        assertFalse(decision.canArm)
        assertTrue(ArmingBlockReason.PHONE_NOT_STABLE in decision.blockingReasons)
        assertTrue(ArmingBlockReason.SOLAR_FILTER_NOT_ACKNOWLEDGED in decision.blockingReasons)
    }

    @Test
    fun clippedTrajectoryAndBlockedHealthBlockArming() {
        val decision = CaptureArmingGate.evaluate(
            input(
                frameFit = FrameFit.CLIPPED,
                health = DeviceHealthDecision(
                    CaptureReadiness.BLOCKED,
                    setOf(DeviceHealthReason.THERMAL_UNSAFE),
                ),
            ),
        )

        assertFalse(decision.canArm)
        assertTrue(ArmingBlockReason.TRAJECTORY_NOT_IN_FRAME in decision.blockingReasons)
        assertTrue(ArmingBlockReason.DEVICE_HEALTH_BLOCKED in decision.blockingReasons)
    }

    @Test
    fun degradedHealthAndMediumOrientationWarnButAllowArming() {
        val decision = CaptureArmingGate.evaluate(
            input(
                orientation = orientation(confidence = OrientationConfidence.MEDIUM),
                health = DeviceHealthDecision(
                    CaptureReadiness.DEGRADED,
                    setOf(DeviceHealthReason.STORAGE_MARGINAL),
                ),
            ),
        )

        assertTrue(decision.canArm)
        assertTrue(ArmingWarning.DEVICE_HEALTH_DEGRADED in decision.warnings)
        assertTrue(ArmingWarning.ORIENTATION_MEDIUM_CONFIDENCE in decision.warnings)
    }

    @Test
    fun unavailablePlanAndOrientationBlockArming() {
        val decision = CaptureArmingGate.evaluate(
            input(
                plan = CapturePlanResult.Unavailable("missing contacts"),
                orientation = OrientationState(),
            ),
        )

        assertFalse(decision.canArm)
        assertTrue(ArmingBlockReason.PLAN_UNAVAILABLE in decision.blockingReasons)
        assertTrue(ArmingBlockReason.ORIENTATION_UNAVAILABLE in decision.blockingReasons)
    }

    private fun input(
        plan: CapturePlanResult = readyPlan(),
        orientation: OrientationState = orientation(),
        frameFit: FrameFit = FrameFit.FITS,
        filterAcknowledged: Boolean = true,
        health: DeviceHealthDecision = DeviceHealthDecision(CaptureReadiness.READY, emptySet()),
    ) = CaptureArmingInput(plan, orientation, frameFit, filterAcknowledged, health)

    private fun orientation(
        confidence: OrientationConfidence = OrientationConfidence.HIGH,
        stability: StabilityState = StabilityState.STABLE,
    ) = OrientationState(
        orientation = PhoneOrientation(180.0, 20.0, 0.0),
        confidence = confidence,
        stability = stability,
        sensorAvailable = true,
    )

    private fun readyPlan(): CapturePlanResult.Ready {
        val instant = Instant.parse("2026-08-12T17:45:00Z")
        return CapturePlanResult.Ready(
            CapturePlan(
                startsAtUtc = instant,
                endsAtUtc = instant,
                instructions = listOf(
                    CaptureInstruction(instant, CapturePhase.CONTACT_BURST, ExposureStrategy.CONTACT_BRACKET),
                ),
            ),
        )
    }
}
