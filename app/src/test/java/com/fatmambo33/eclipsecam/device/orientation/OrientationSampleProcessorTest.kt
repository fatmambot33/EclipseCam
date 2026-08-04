package com.fatmambo33.eclipsecam.device.orientation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrientationSampleProcessorTest {
    @Test
    fun identityRotationProducesNormalizedZeroOrientation() {
        val state = OrientationSampleProcessor().process(
            sample = RotationVectorSample(x = 0.0, y = 0.0, z = 0.0, scalar = 1.0, accuracy = 3),
            displayRotation = DisplayRotation.ROTATION_0,
            angularSpeedDegreesPerSecond = 0.0,
        )

        assertTrue(state.sensorAvailable)
        assertEquals(OrientationConfidence.HIGH, state.confidence)
        assertEquals(0.0, state.orientation!!.azimuthDegrees, 1e-9)
        assertEquals(0.0, state.orientation.elevationDegrees, 1e-9)
        assertEquals(0.0, state.orientation.rollDegrees, 1e-9)
    }

    @Test
    fun displayRotationIsReflectedInAzimuth() {
        val processor = OrientationSampleProcessor()
        val sample = RotationVectorSample(x = 0.0, y = 0.0, z = 0.0, scalar = 1.0, accuracy = 2)

        val portrait = processor.process(sample, DisplayRotation.ROTATION_0, 2.0)
        val landscape = processor.process(sample, DisplayRotation.ROTATION_90, 2.0)

        assertEquals(0.0, portrait.orientation!!.azimuthDegrees, 1e-9)
        assertEquals(90.0, landscape.orientation!!.azimuthDegrees, 1e-9)
        assertEquals(OrientationConfidence.MEDIUM, landscape.confidence)
    }

    @Test
    fun absentSensorProducesExplicitUnavailableState() {
        val state = OrientationSampleProcessor().process(
            sample = null,
            displayRotation = DisplayRotation.ROTATION_0,
            angularSpeedDegreesPerSecond = 0.0,
        )

        assertFalse(state.sensorAvailable)
        assertNull(state.orientation)
        assertEquals(OrientationConfidence.UNAVAILABLE, state.confidence)
        assertEquals(StabilityState.UNAVAILABLE, state.stability)
    }

    @Test
    fun rotationVectorIsNormalizedBeforeConversion() {
        val state = OrientationSampleProcessor().process(
            sample = RotationVectorSample(x = 0.0, y = 0.0, z = 2.0, scalar = 2.0, accuracy = 1),
            displayRotation = DisplayRotation.ROTATION_0,
            angularSpeedDegreesPerSecond = 2.0,
        )

        assertEquals(270.0, state.orientation!!.azimuthDegrees, 1e-9)
        assertEquals(OrientationConfidence.LOW, state.confidence)
        assertEquals(StabilityState.MOVING, state.stability)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroNormRotationVectorIsRejected() {
        OrientationSampleProcessor().process(
            sample = RotationVectorSample(x = 0.0, y = 0.0, z = 0.0, scalar = 0.0),
            displayRotation = DisplayRotation.ROTATION_0,
            angularSpeedDegreesPerSecond = 0.0,
        )
    }
}
