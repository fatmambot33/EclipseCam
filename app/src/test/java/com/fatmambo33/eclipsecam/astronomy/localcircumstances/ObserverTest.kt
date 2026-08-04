package com.fatmambo33.eclipsecam.astronomy.localcircumstances

import org.junit.Assert.assertEquals
import org.junit.Test

class ObserverTest {
    @Test
    fun `accepts valid observer coordinates`() {
        val observer = Observer(
            latitudeDegrees = 43.263,
            longitudeDegrees = -2.935,
            elevationMeters = 25.0,
        )

        assertEquals(43.263, observer.latitudeDegrees, 0.0)
        assertEquals(-2.935, observer.longitudeDegrees, 0.0)
        assertEquals(25.0, observer.elevationMeters, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid latitude`() {
        Observer(latitudeDegrees = 90.1, longitudeDegrees = 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects invalid longitude`() {
        Observer(latitudeDegrees = 0.0, longitudeDegrees = 180.1)
    }
}
