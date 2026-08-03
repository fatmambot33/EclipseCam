package com.fatmambo33.eclipsecam.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapArchitectureTest {
    private val overlay = EclipseMapOverlay(
        centreline = listOf(GeoPoint(43.0, -4.0)),
        northernLimit = emptyList(),
        southernLimit = emptyList(),
        pathUncertaintyKilometers = 3.0,
    )

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPublicOpenStreetMapTileServer() {
        MapTileSource(
            id = "forbidden",
            styleUrl = "https://tile.openstreetmap.org/style.json",
            attribution = EclipseMapConfiguration.REQUIRED_ATTRIBUTION,
            supportsOfflineUse = false,
        )
    }

    @Test
    fun supportsOverlayOnlyAirplaneMode() {
        val configuration = EclipseMapConfiguration(tileSource = null, overlay = overlay)
        assertFalse(configuration.basemapAvailable)
        assertTrue(configuration.overlay.centreline.isNotEmpty())
    }

    @Test
    fun acceptsConfiguredOsmDerivedProvider() {
        val source = MapTileSource(
            id = "provider",
            styleUrl = "https://maps.example.org/style.json",
            attribution = EclipseMapConfiguration.REQUIRED_ATTRIBUTION,
            supportsOfflineUse = true,
        )
        assertTrue(EclipseMapConfiguration(source, overlay).basemapAvailable)
    }
}
