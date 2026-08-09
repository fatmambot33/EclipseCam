package com.fatmambo33.eclipsecam.map

import com.fatmambo33.eclipsecam.astronomy.EclipsePath2026
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EclipseMapSceneTest {
    @Test
    fun referenceSceneKeepsCentrelineLimitsAndUncertaintyDistinct() {
        val overlay = EclipseMapScene2026.overlay

        assertEquals(EclipsePath2026.centerLine.size, overlay.centreline.size)
        assertEquals(EclipsePath2026.northernLimit.size, overlay.northernLimit.size)
        assertEquals(EclipsePath2026.southernLimit.size, overlay.southernLimit.size)
        assertEquals(EclipsePath2026.limbUncertaintyKm, overlay.pathUncertaintyKilometers, 0.0)
        assertTrue(overlay.centreline != overlay.northernLimit)
        assertTrue(overlay.centreline != overlay.southernLimit)
    }

    @Test
    fun accuracyRingIsClosedAndSurroundsObserver() {
        val observer = GeoPoint(latitude = 43.5, longitude = -5.5)
        val ring = EclipseMapScene(
            overlay = EclipseMapScene2026.overlay,
            observer = observer,
            observerAccuracyMeters = 100.0,
        ).accuracyRing

        assertEquals(49, ring.size)
        assertEquals(ring.first(), ring.last())
        assertTrue(ring.any { it.latitude > observer.latitude })
        assertTrue(ring.any { it.latitude < observer.latitude })
        assertTrue(ring.any { it.longitude > observer.longitude })
        assertTrue(ring.any { it.longitude < observer.longitude })
    }

    @Test(expected = IllegalArgumentException::class)
    fun accuracyCannotExistWithoutObserver() {
        EclipseMapScene(
            overlay = EclipseMapScene2026.overlay,
            observerAccuracyMeters = 10.0,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun publicOpenStreetMapTileServerIsRejected() {
        MapTileSource(
            id = "forbidden",
            styleUrl = "https://tile.openstreetmap.org/style.json",
            attribution = EclipseMapConfiguration.REQUIRED_ATTRIBUTION,
            supportsOfflineUse = false,
        )
    }
}
