package com.fatmambo33.eclipsecam.device.orientation

import kotlin.math.abs

/** Confidence in the current orientation estimate. */
enum class OrientationConfidence {
    UNAVAILABLE,
    LOW,
    MEDIUM,
    HIGH,
}

/** Motion classification used by alignment and capture arming. */
enum class StabilityState {
    UNAVAILABLE,
    MOVING,
    SETTLING,
    STABLE,
}

/** Normalized phone orientation in degrees. */
data class PhoneOrientation(
    val azimuthDegrees: Double,
    val elevationDegrees: Double,
    val rollDegrees: Double,
) {
    init {
        require(azimuthDegrees in 0.0..<360.0)
        require(elevationDegrees in -180.0..180.0)
        require(rollDegrees in -180.0..180.0)
    }
}

/** Complete local orientation state. */
data class OrientationState(
    val orientation: PhoneOrientation? = null,
    val confidence: OrientationConfidence = OrientationConfidence.UNAVAILABLE,
    val stability: StabilityState = StabilityState.UNAVAILABLE,
    val angularSpeedDegreesPerSecond: Double = 0.0,
    val sensorAvailable: Boolean = false,
)

/** Configurable thresholds for tripod stability classification. */
data class StabilityThresholds(
    val stableAngularSpeedDegreesPerSecond: Double = 0.35,
    val movingAngularSpeedDegreesPerSecond: Double = 1.5,
    val stableSamplesRequired: Int = 20,
) {
    init {
        require(stableAngularSpeedDegreesPerSecond >= 0.0)
        require(movingAngularSpeedDegreesPerSecond > stableAngularSpeedDegreesPerSecond)
        require(stableSamplesRequired > 0)
    }
}

/** Stateful classifier that avoids declaring a phone stable after one quiet sample. */
class StabilityClassifier(
    private val thresholds: StabilityThresholds = StabilityThresholds(),
) {
    private var quietSamples = 0

    fun classify(angularSpeedDegreesPerSecond: Double, sensorAvailable: Boolean = true): StabilityState {
        if (!sensorAvailable) {
            quietSamples = 0
            return StabilityState.UNAVAILABLE
        }
        val speed = abs(angularSpeedDegreesPerSecond)
        return when {
            speed >= thresholds.movingAngularSpeedDegreesPerSecond -> {
                quietSamples = 0
                StabilityState.MOVING
            }
            speed <= thresholds.stableAngularSpeedDegreesPerSecond -> {
                quietSamples += 1
                if (quietSamples >= thresholds.stableSamplesRequired) StabilityState.STABLE else StabilityState.SETTLING
            }
            else -> {
                quietSamples = 0
                StabilityState.SETTLING
            }
        }
    }
}

fun normalizeAzimuthDegrees(value: Double): Double {
    val normalized = value % 360.0
    return if (normalized < 0.0) normalized + 360.0 else normalized
}

fun normalizeSignedDegrees(value: Double): Double {
    val normalized = normalizeAzimuthDegrees(value)
    return if (normalized > 180.0) normalized - 360.0 else normalized
}
