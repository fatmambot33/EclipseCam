package com.fatmambo33.eclipsecam.device.orientation

import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationModelsTest {
    @Test
    fun `azimuth normalization wraps into zero to 360`() {
        assertEquals(350.0, normalizeAzimuthDegrees(-10.0), 0.0001)
        assertEquals(10.0, normalizeAzimuthDegrees(370.0), 0.0001)
    }

    @Test
    fun `signed normalization wraps around 180`() {
        assertEquals(-170.0, normalizeSignedDegrees(190.0), 0.0001)
        assertEquals(170.0, normalizeSignedDegrees(-190.0), 0.0001)
    }

    @Test
    fun `classifier requires sustained quiet samples before stable`() {
        val classifier = StabilityClassifier(
            StabilityThresholds(
                stableAngularSpeedDegreesPerSecond = 0.5,
                movingAngularSpeedDegreesPerSecond = 2.0,
                stableSamplesRequired = 3,
            ),
        )

        assertEquals(StabilityState.SETTLING, classifier.classify(0.1))
        assertEquals(StabilityState.SETTLING, classifier.classify(0.1))
        assertEquals(StabilityState.STABLE, classifier.classify(0.1))
    }

    @Test
    fun `movement resets quiet sample history`() {
        val classifier = StabilityClassifier(
            StabilityThresholds(stableSamplesRequired = 2),
        )

        assertEquals(StabilityState.SETTLING, classifier.classify(0.0))
        assertEquals(StabilityState.MOVING, classifier.classify(3.0))
        assertEquals(StabilityState.SETTLING, classifier.classify(0.0))
    }

    @Test
    fun `missing gyro reports unavailable`() {
        val classifier = StabilityClassifier()
        assertEquals(
            StabilityState.UNAVAILABLE,
            classifier.classify(0.0, sensorAvailable = false),
        )
    }
}
