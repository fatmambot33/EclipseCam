package com.fatmambo33.eclipsecam.map

/** Map source configuration kept independent from the renderer. */
data class MapTileSource(
    val id: String,
    val styleUrl: String,
    val attribution: String,
    val supportsOfflineUse: Boolean,
) {
    init {
        require(id.isNotBlank()) { "Tile source id must not be blank" }
        require(styleUrl.startsWith("https://") || styleUrl.startsWith("asset://")) {
            "Tile source must use HTTPS or a packaged asset"
        }
        require(attribution.contains("OpenStreetMap", ignoreCase = true)) {
            "OpenStreetMap-derived sources must expose OpenStreetMap attribution"
        }
        require(!styleUrl.contains("tile.openstreetmap.org", ignoreCase = true)) {
            "The public OpenStreetMap tile server must not be used as an app backend"
        }
    }
}

/** Eclipse-specific geometry rendered locally above the basemap. */
data class EclipseMapOverlay(
    val centreline: List<GeoPoint>,
    val northernLimit: List<GeoPoint>,
    val southernLimit: List<GeoPoint>,
    val pathUncertaintyKilometers: Double,
) {
    init {
        require(pathUncertaintyKilometers >= 0.0)
    }
}

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)
    }
}

/** Runtime map configuration for online and offline operation. */
data class EclipseMapConfiguration(
    val tileSource: MapTileSource?,
    val overlay: EclipseMapOverlay,
) {
    val basemapAvailable: Boolean get() = tileSource != null

    companion object {
        const val REQUIRED_ATTRIBUTION = "© OpenStreetMap contributors"
    }
}
