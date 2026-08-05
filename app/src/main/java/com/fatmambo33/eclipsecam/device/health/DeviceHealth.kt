package com.fatmambo33.eclipsecam.device.health

/** Thermal pressure normalized away from Android framework constants. */
enum class ThermalPressure {
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    EMERGENCY,
    SHUTDOWN,
    UNKNOWN,
}

/** Local phone resources relevant to an eclipse capture session. */
data class DeviceHealthSnapshot(
    val batteryPercent: Int?,
    val charging: Boolean?,
    val thermalPressure: ThermalPressure,
    val availableStorageBytes: Long?,
)

enum class CaptureReadiness {
    READY,
    DEGRADED,
    BLOCKED,
}

enum class DeviceHealthReason {
    BATTERY_UNKNOWN,
    BATTERY_LOW,
    BATTERY_MARGINAL,
    CHARGING_STATE_UNKNOWN,
    THERMAL_UNKNOWN,
    THERMAL_ELEVATED,
    THERMAL_UNSAFE,
    STORAGE_UNKNOWN,
    STORAGE_LOW,
    STORAGE_MARGINAL,
}

data class DeviceHealthDecision(
    val readiness: CaptureReadiness,
    val reasons: Set<DeviceHealthReason>,
)

/**
 * Converts local resource readings into an honest capture readiness decision.
 *
 * Unknown values degrade readiness instead of silently assuming ideal conditions.
 * Any blocking condition wins over degraded or ready conditions.
 */
class DeviceHealthPolicy(
    private val minimumBatteryPercent: Int = 15,
    private val preferredBatteryPercent: Int = 30,
    private val minimumStorageBytes: Long = 1_000_000_000L,
    private val preferredStorageBytes: Long = 3_000_000_000L,
) {
    init {
        require(minimumBatteryPercent in 0..100)
        require(preferredBatteryPercent in minimumBatteryPercent..100)
        require(minimumStorageBytes >= 0L)
        require(preferredStorageBytes >= minimumStorageBytes)
    }

    fun evaluate(snapshot: DeviceHealthSnapshot): DeviceHealthDecision {
        val reasons = linkedSetOf<DeviceHealthReason>()
        var blocked = false

        when (val battery = snapshot.batteryPercent) {
            null -> reasons += DeviceHealthReason.BATTERY_UNKNOWN
            in 0 until minimumBatteryPercent -> {
                if (snapshot.charging != true) {
                    blocked = true
                    reasons += DeviceHealthReason.BATTERY_LOW
                } else {
                    reasons += DeviceHealthReason.BATTERY_MARGINAL
                }
            }
            in minimumBatteryPercent until preferredBatteryPercent -> reasons += DeviceHealthReason.BATTERY_MARGINAL
        }

        if (snapshot.charging == null) reasons += DeviceHealthReason.CHARGING_STATE_UNKNOWN

        when (snapshot.thermalPressure) {
            ThermalPressure.SEVERE,
            ThermalPressure.CRITICAL,
            ThermalPressure.EMERGENCY,
            ThermalPressure.SHUTDOWN,
            -> {
                blocked = true
                reasons += DeviceHealthReason.THERMAL_UNSAFE
            }
            ThermalPressure.MODERATE -> reasons += DeviceHealthReason.THERMAL_ELEVATED
            ThermalPressure.UNKNOWN -> reasons += DeviceHealthReason.THERMAL_UNKNOWN
            ThermalPressure.NONE,
            ThermalPressure.LIGHT,
            -> Unit
        }

        when (val storage = snapshot.availableStorageBytes) {
            null -> reasons += DeviceHealthReason.STORAGE_UNKNOWN
            in 0 until minimumStorageBytes -> {
                blocked = true
                reasons += DeviceHealthReason.STORAGE_LOW
            }
            in minimumStorageBytes until preferredStorageBytes -> reasons += DeviceHealthReason.STORAGE_MARGINAL
        }

        val readiness = when {
            blocked -> CaptureReadiness.BLOCKED
            reasons.isNotEmpty() -> CaptureReadiness.DEGRADED
            else -> CaptureReadiness.READY
        }
        return DeviceHealthDecision(readiness = readiness, reasons = reasons)
    }
}
