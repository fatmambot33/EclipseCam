package com.fatmambo33.eclipsecam.astronomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BesselianElementsTest {
    @Test
    fun evaluatesReferenceEpoch() {
        val instant = java.time.Instant.parse("2026-08-12T17:58:48.6Z")
        val value = Eclipse2026Aug12.elements.evaluate(instant)

        assertEquals(0.0, value.tHours, 1e-8)
        assertEquals(0.475593, value.x, 1e-9)
        assertEquals(0.771161, value.y, 1e-9)
        assertEquals(14.79667, value.declinationDegrees, 1e-9)
        assertEquals(-0.008142, value.umbralRadius, 1e-9)
    }

    @Test
    fun greatestEclipseFallsInsideValidityWindow() {
        val value = Eclipse2026Aug12.elements.evaluate(Eclipse2026Aug12.greatestEclipseUtc)
        assertTrue(value.tHours in -0.3..0.0)
        assertTrue(value.hourAngleDegrees in 0.0..<360.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTimesOutsidePublishedValidityWindow() {
        Eclipse2026Aug12.elements.evaluate(java.time.Instant.parse("2026-08-12T10:00:00Z"))
    }
}
