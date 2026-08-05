package com.fatmambo33.eclipsecam.device.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceHealthPolicyTest {
    private val policy = DeviceHealthPolicy()

    @Test
    fun healthyPhoneIsReady() {
        val decision = policy.evaluate(snapshot(battery = 80, charging = false, storage = 8_000_000_000L))

        assertEquals(CaptureReadiness.READY, decision.readiness)
        assertTrue(decision.reasons.isEmpty())
    }

    @Test
    fun lowDischargingBatteryBlocksCapture() {
        val decision = policy.evaluate(snapshot(battery = 14, charging = false))

        assertEquals(CaptureReadiness.BLOCKED, decision.readiness)
        assertTrue(DeviceHealthReason.BATTERY_LOW in decision.reasons)
    }

    @Test
    fun lowChargingBatteryDegradesInsteadOfBlocking() {
        val decision = policy.evaluate(snapshot(battery = 5, charging = true))

        assertEquals(CaptureReadiness.DEGRADED, decision.readiness)
        assertTrue(DeviceHealthReason.BATTERY_MARGINAL in decision.reasons)
    }

    @Test
    fun minimumBatteryBoundaryDoesNotBlock() {
        val decision = policy.evaluate(snapshot(battery = 15, charging = false))

        assertEquals(CaptureReadiness.DEGRADED, decision.readiness)
        assertTrue(DeviceHealthReason.BATTERY_MARGINAL in decision.reasons)
    }

    @Test
    fun severeThermalPressureBlocksCapture() {
        val decision = policy.evaluate(snapshot(thermal = ThermalPressure.SEVERE))

        assertEquals(CaptureReadiness.BLOCKED, decision.readiness)
        assertTrue(DeviceHealthReason.THERMAL_UNSAFE in decision.reasons)
    }

    @Test
    fun moderateThermalPressureDegradesCapture() {
        val decision = policy.evaluate(snapshot(thermal = ThermalPressure.MODERATE))

        assertEquals(CaptureReadiness.DEGRADED, decision.readiness)
        assertTrue(DeviceHealthReason.THERMAL_ELEVATED in decision.reasons)
    }

    @Test
    fun lowStorageBlocksCapture() {
        val decision = policy.evaluate(snapshot(storage = 999_999_999L))

        assertEquals(CaptureReadiness.BLOCKED, decision.readiness)
        assertTrue(DeviceHealthReason.STORAGE_LOW in decision.reasons)
    }

    @Test
    fun minimumStorageBoundaryDegradesCapture() {
        val decision = policy.evaluate(snapshot(storage = 1_000_000_000L))

        assertEquals(CaptureReadiness.DEGRADED, decision.readiness)
        assertTrue(DeviceHealthReason.STORAGE_MARGINAL in decision.reasons)
    }

    @Test
    fun unknownReadingsNeverImplyReady() {
        val decision = policy.evaluate(
            DeviceHealthSnapshot(
                batteryPercent = null,
                charging = null,
                thermalPressure = ThermalPressure.UNKNOWN,
                availableStorageBytes = null,
            ),
        )

        assertEquals(CaptureReadiness.DEGRADED, decision.readiness)
        assertEquals(
            setOf(
                DeviceHealthReason.BATTERY_UNKNOWN,
                DeviceHealthReason.CHARGING_STATE_UNKNOWN,
                DeviceHealthReason.THERMAL_UNKNOWN,
                DeviceHealthReason.STORAGE_UNKNOWN,
            ),
            decision.reasons,
        )
    }

    @Test
    fun blockingConditionWinsOverDegradedConditions() {
        val decision = policy.evaluate(
            snapshot(
                battery = 20,
                charging = false,
                thermal = ThermalPressure.MODERATE,
                storage = 100L,
            ),
        )

        assertEquals(CaptureReadiness.BLOCKED, decision.readiness)
        assertTrue(DeviceHealthReason.BATTERY_MARGINAL in decision.reasons)
        assertTrue(DeviceHealthReason.THERMAL_ELEVATED in decision.reasons)
        assertTrue(DeviceHealthReason.STORAGE_LOW in decision.reasons)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidThresholdsFailFast() {
        DeviceHealthPolicy(minimumBatteryPercent = 40, preferredBatteryPercent = 20)
    }

    private fun snapshot(
        battery: Int? = 80,
        charging: Boolean? = false,
        thermal: ThermalPressure = ThermalPressure.NONE,
        storage: Long? = 8_000_000_000L,
    ) = DeviceHealthSnapshot(
        batteryPercent = battery,
        charging = charging,
        thermalPressure = thermal,
        availableStorageBytes = storage,
    )
}
