package com.fatmambo33.eclipsecam.astronomy.localcircumstances

import java.time.Duration
import java.time.Instant
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
    fun `matches NASA published circumstances for five reference cities`() {
        val references = listOf(
            ReferenceCity(
                name = "Reykjavik",
                observer = Observer(64.1466, -21.9426, 15.0),
                visibility = EclipseVisibility.TOTAL,
                expectedContactsUtc = mapOf(
                    EclipseContact.C1 to "2026-08-12T16:47:00Z",
                    EclipseContact.C2 to "2026-08-12T17:48:00Z",
                    EclipseContact.C3 to "2026-08-12T17:49:00Z",
                    EclipseContact.C4 to "2026-08-12T18:47:00Z",
                ),
            ),
            ReferenceCity(
                name = "Leon",
                observer = Observer(42.5987, -5.5671, 837.0),
                visibility = EclipseVisibility.TOTAL,
                expectedContactsUtc = mapOf(
                    EclipseContact.C1 to "2026-08-12T17:32:00Z",
                    EclipseContact.C2 to "2026-08-12T18:28:00Z",
                    EclipseContact.C3 to "2026-08-12T18:30:00Z",
                    EclipseContact.C4 to "2026-08-12T19:22:00Z",
                ),
            ),
            ReferenceCity(
                name = "Valencia",
                observer = Observer(39.4699, -0.3763, 15.0),
                visibility = EclipseVisibility.TOTAL,
                expectedContactsUtc = mapOf(
                    EclipseContact.C1 to "2026-08-12T17:38:00Z",
                    EclipseContact.C2 to "2026-08-12T18:32:00Z",
                    EclipseContact.C3 to "2026-08-12T18:33:00Z",
                ),
            ),
            ReferenceCity(
                name = "Zaragoza",
                observer = Observer(41.6488, -0.8891, 208.0),
                visibility = EclipseVisibility.TOTAL,
                expectedContactsUtc = mapOf(
                    EclipseContact.C1 to "2026-08-12T17:34:00Z",
                    EclipseContact.C2 to "2026-08-12T18:29:00Z",
                    EclipseContact.C3 to "2026-08-12T18:30:00Z",
                ),
            ),
            ReferenceCity(
                name = "Barcelona",
                observer = Observer(41.3874, 2.1686, 12.0),
                visibility = EclipseVisibility.PARTIAL,
                expectedContactsUtc = mapOf(
                    EclipseContact.C1 to "2026-08-12T17:35:00Z",
                ),
            ),
        )

        references.forEach { reference ->
            val result = calculator.calculate(reference.observer)
            assertEquals(reference.name, reference.visibility, result.visibility)
            reference.expectedContactsUtc.forEach { (contact, expectedText) ->
                val actual = result.contacts.getValue(contact).instantUtc
                val expected = Instant.parse(expectedText)
                val difference = kotlin.math.abs(Duration.between(expected, actual).seconds)
                assertTrue(
                    "${reference.name} $contact differs by $difference seconds",
                    difference <= REFERENCE_TOLERANCE_SECONDS,
                )
            }
        }
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

    private data class ReferenceCity(
        val name: String,
        val observer: Observer,
        val visibility: EclipseVisibility,
        val expectedContactsUtc: Map<EclipseContact, String>,
    )

    private companion object {
        // NASA's public city table is rounded to whole minutes. The 90-second
        // tolerance covers that rounding while still catching material solver drift.
        const val REFERENCE_TOLERANCE_SECONDS = 90L
    }
}
