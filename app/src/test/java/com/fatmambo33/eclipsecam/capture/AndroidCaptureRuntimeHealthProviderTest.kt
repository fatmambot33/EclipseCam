package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.device.health.CaptureReadiness
import com.fatmambo33.eclipsecam.device.health.DeviceHealthPolicy
import com.fatmambo33.eclipsecam.device.health.DeviceHealthReason
import com.fatmambo33.eclipsecam.device.health.DeviceHealthSnapshot
import com.fatmambo33.eclipsecam.device.health.ThermalPressure
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidCaptureRuntimeHealthProviderTest {
    @Test
    fun currentReadsFreshSnapshotEveryTime() {
        var reads = 0
        val snapshots = listOf(
            snapshot(battery = 80, thermal = ThermalPressure.NONE, storage = 4_000_000_000L),
            snapshot(battery = 80, thermal = ThermalPressure.SEVERE, storage = 4_000_000_000L),
        )
        val provider = AndroidCaptureRuntimeHealthProvider(
            reader = { snapshots[reads++] },
            policy = DeviceHealthPolicy(),
        )

        val ready = provider.current()
        val blocked = provider.current()

        assertEquals(2, reads)
        assertEquals(CaptureReadiness.READY, ready.readiness)
        assertEquals(CaptureReadiness.BLOCKED, blocked.readiness)
        assertEquals(setOf(DeviceHealthReason.THERMAL_UNSAFE), blocked.reasons)
    }

    @Test
    fun unknownReadingsDegradeInsteadOfAssumingSafeState() {
        val provider = AndroidCaptureRuntimeHealthProvider(
            reader = {
                DeviceHealthSnapshot(
                    batteryPercent = null,
                    charging = null,
                    thermalPressure = ThermalPressure.UNKNOWN,
                    availableStorageBytes = null,
                )
            },
            policy = DeviceHealthPolicy(),
        )

        val decision = provider.current()

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

    private fun snapshot(
        battery: Int,
        thermal: ThermalPressure,
        storage: Long,
    ) = DeviceHealthSnapshot(
        batteryPercent = battery,
        charging = false,
        thermalPressure = thermal,
        availableStorageBytes = storage,
    )
}
