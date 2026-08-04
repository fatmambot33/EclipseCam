package com.fatmambo33.eclipsecam.device.orientation

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

/** Display rotation used to remap device axes before exposing orientation. */
enum class DisplayRotation {
    ROTATION_0,
    ROTATION_90,
    ROTATION_180,
    ROTATION_270,
}

/** Rotation-vector sample independent of the Android sensor framework. */
data class RotationVectorSample(
    val x: Double,
    val y: Double,
    val z: Double,
    val scalar: Double? = null,
    val accuracy: Int? = null,
)

/**
 * Converts rotation-vector samples into normalized phone orientation.
 *
 * Keeping the numerical mapping outside Android makes display-rotation handling,
 * confidence mapping, and fallback behavior deterministic and unit-testable.
 */
class OrientationSampleProcessor(
    private val stabilityClassifier: StabilityClassifier = StabilityClassifier(),
) {
    fun process(
        sample: RotationVectorSample?,
        displayRotation: DisplayRotation,
        angularSpeedDegreesPerSecond: Double,
    ): OrientationState {
        if (sample == null) {
            return OrientationState(
                confidence = OrientationConfidence.UNAVAILABLE,
                stability = stabilityClassifier.classify(
                    angularSpeedDegreesPerSecond = angularSpeedDegreesPerSecond,
                    sensorAvailable = false,
                ),
                angularSpeedDegreesPerSecond = angularSpeedDegreesPerSecond,
                sensorAvailable = false,
            )
        }

        val matrix = rotationMatrix(sample)
        val remapped = remapForDisplay(matrix, displayRotation)
        val orientation = orientationFromMatrix(remapped)
        return OrientationState(
            orientation = orientation,
            confidence = confidenceFromAccuracy(sample.accuracy),
            stability = stabilityClassifier.classify(angularSpeedDegreesPerSecond),
            angularSpeedDegreesPerSecond = angularSpeedDegreesPerSecond,
            sensorAvailable = true,
        )
    }
}

internal fun confidenceFromAccuracy(accuracy: Int?): OrientationConfidence = when (accuracy) {
    null, 0 -> OrientationConfidence.LOW
    1 -> OrientationConfidence.LOW
    2 -> OrientationConfidence.MEDIUM
    else -> OrientationConfidence.HIGH
}

private fun rotationMatrix(sample: RotationVectorSample): DoubleArray {
    val scalar = sample.scalar ?: sqrt(
        max(0.0, 1.0 - sample.x * sample.x - sample.y * sample.y - sample.z * sample.z),
    )
    val norm = sqrt(
        sample.x * sample.x + sample.y * sample.y + sample.z * sample.z + scalar * scalar,
    )
    require(norm > 0.0) { "Rotation vector must have a non-zero norm" }

    val x = sample.x / norm
    val y = sample.y / norm
    val z = sample.z / norm
    val w = scalar / norm

    return doubleArrayOf(
        1.0 - 2.0 * (y * y + z * z),
        2.0 * (x * y - z * w),
        2.0 * (x * z + y * w),
        2.0 * (x * y + z * w),
        1.0 - 2.0 * (x * x + z * z),
        2.0 * (y * z - x * w),
        2.0 * (x * z - y * w),
        2.0 * (y * z + x * w),
        1.0 - 2.0 * (x * x + y * y),
    )
}

private fun remapForDisplay(matrix: DoubleArray, rotation: DisplayRotation): DoubleArray = when (rotation) {
    DisplayRotation.ROTATION_0 -> matrix
    DisplayRotation.ROTATION_90 -> multiply(matrix, zRotationDegrees(-90.0))
    DisplayRotation.ROTATION_180 -> multiply(matrix, zRotationDegrees(-180.0))
    DisplayRotation.ROTATION_270 -> multiply(matrix, zRotationDegrees(-270.0))
}

private fun orientationFromMatrix(matrix: DoubleArray): PhoneOrientation {
    val azimuth = atan2(matrix[1], matrix[4]).toDegrees()
    val elevation = asin((-matrix[7]).coerceIn(-1.0, 1.0)).toDegrees()
    val roll = atan2(-matrix[6], matrix[8]).toDegrees()
    return PhoneOrientation(
        azimuthDegrees = normalizeAzimuthDegrees(azimuth),
        elevationDegrees = normalizeSignedDegrees(elevation),
        rollDegrees = normalizeSignedDegrees(roll),
    )
}

private fun zRotationDegrees(degrees: Double): DoubleArray {
    val radians = Math.toRadians(degrees)
    val cosine = kotlin.math.cos(radians)
    val sine = kotlin.math.sin(radians)
    return doubleArrayOf(
        cosine, -sine, 0.0,
        sine, cosine, 0.0,
        0.0, 0.0, 1.0,
    )
}

private fun multiply(left: DoubleArray, right: DoubleArray): DoubleArray {
    require(left.size == 9 && right.size == 9)
    return DoubleArray(9) { index ->
        val row = index / 3
        val column = index % 3
        (0..2).sumOf { offset -> left[row * 3 + offset] * right[offset * 3 + column] }
    }
}

private fun Double.toDegrees(): Double = Math.toDegrees(this)
