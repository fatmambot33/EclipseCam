package com.fatmambo33.eclipsecam.astronomy

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class BesselianElementsAuthoritativeTest {
    @Test
    fun `matches current NASA GSFC published coefficients`() {
        val elements = Eclipse2026Aug12.elements

        assertEquals(18.0, elements.referenceTdtHour, 0.0)
        assertEquals(75.4, elements.deltaTSeconds, 0.0)
        assertPolynomial(elements.x, 0.4755140, 0.5189249, -0.0000773)
        assertPolynomial(elements.y, 0.7711830, -0.2301680, -0.0001246)
        assertPolynomial(elements.declinationDegrees, 14.7966700, -0.0120650, -0.0000030)
        assertPolynomial(elements.penumbralRadius, 0.5379550, 0.0000939, -0.0000121)
        assertPolynomial(elements.umbralRadius, -0.0081420, 0.0000935, -0.0000121)
        assertPolynomial(elements.hourAngleDegrees, 88.747787, 15.003090, 0.0)
        assertEquals(0.0046141, elements.tanF1, 0.0)
        assertEquals(0.0045911, elements.tanF2, 0.0)
    }

    @Test
    fun `publishes source identity and greatest eclipse instant`() {
        assertEquals(
            "NASA-GSFC-SEdata-20260812-2023-10-30",
            Eclipse2026Aug12.SOURCE_DATASET,
        )
        assertEquals(
            Instant.parse("2026-08-12T17:45:51Z"),
            Eclipse2026Aug12.greatestEclipseUtc,
        )
    }

    @Test
    fun `evaluates reference epoch using delta T`() {
        val referenceUtc = Instant.parse("2026-08-12T17:58:44.600Z")
        val evaluated = Eclipse2026Aug12.elements.evaluate(referenceUtc)

        assertEquals(0.0, evaluated.tHours, 1e-9)
        assertEquals(0.4755140, evaluated.x, 1e-12)
        assertEquals(0.7711830, evaluated.y, 1e-12)
        assertEquals(14.7966700, evaluated.declinationDegrees, 1e-12)
        assertEquals(0.5379550, evaluated.penumbralRadius, 1e-12)
        assertEquals(-0.0081420, evaluated.umbralRadius, 1e-12)
        assertEquals(88.747787, evaluated.hourAngleDegrees, 1e-12)
    }

    private fun assertPolynomial(
        actual: Polynomial,
        c0: Double,
        c1: Double,
        c2: Double,
    ) {
        assertEquals(c0, actual.c0, 0.0)
        assertEquals(c1, actual.c1, 0.0)
        assertEquals(c2, actual.c2, 0.0)
        assertEquals(0.0, actual.c3, 0.0)
    }
}
