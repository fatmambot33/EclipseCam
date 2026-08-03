package com.fatmambo33.eclipsecam.astronomy.localcircumstances

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BesselianLocalCircumstancesCalculatorTest {
    private val calculator = BesselianLocalCircumstancesCalculator()

    @Test
    fun `centerline reference point produces ordered total-eclipse contacts`() {
        val result = calculator.calculate(
            Observer(latitudeDegrees = 43.3717, longitudeDegrees = -6.1883, elevationMeters = 100.0),
        )

        assertEquals(EclipseVisibility.TOTAL, result.visibility)
        val c1 = result.contacts.getValue(EclipseContact.C1).instantUtc
        val c2 = result.contacts.getValue(EclipseContact.C2).instantUtc
        val maximum = result.contacts.getValue(EclipseContact.MAXIMUM).instantUtc
        val c3 = result.contacts.getValue(EclipseContact.C3).instantUtc
        val c4 = result.contacts.getValue(EclipseContact.C4).instantUtc
        assertTrue(c1 < c2)
        assertTrue(c2 < maximum)
        assertTrue(maximum < c3)
        assertTrue(c3 < c4)
        assertTrue(result.magnitude >= 1.0)
        assertEquals(1.0, result.obscuration, 1e-6)
        assertNotNull(result.totalityDurationSeconds)
        assertTrue(result.totalityDurationSeconds!! > 60.0)
        assertTrue(result.totalityDurationSeconds!! < 180.0)
    }

    @Test
    fun `observer far from eclipse returns no eclipse in model window`() {
        val result = calculator.calculate(
            Observer(latitudeDegrees = -33.8688, longitudeDegrees = 151.2093),
        )

        assertEquals(EclipseVisibility.NONE, result.visibility)
        assertTrue(result.contacts.isEmpty())
        assertEquals(0.0, result.magnitude, 0.0)
        assertEquals(0.0, result.obscuration, 0.0)
    }

    @Test
    fun `all reported contact coordinates are finite and normalized`() {
        val result = calculator.calculate(
            Observer(latitudeDegrees = 41.65, longitudeDegrees = -3.7, elevationMeters = 850.0),
        )

        assertTrue(result.visibility != EclipseVisibility.NONE)
        result.contacts.values.forEach { contact ->
            assertTrue(contact.sunAltitudeDegrees.isFinite())
            assertTrue(contact.sunAltitudeDegrees in -90.0..90.0)
            assertTrue(contact.sunAzimuthDegrees.isFinite())
            assertTrue(contact.sunAzimuthDegrees in 0.0..360.0)
        }
        assertTrue(result.magnitude.isFinite())
        assertTrue(result.obscuration in 0.0..1.0)
    }
}
