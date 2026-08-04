package com.fatmambo33.eclipsecam.map

data class MapTileSource(
    val id: String,
    val styleUrl: String,
    val attribution: String,
    val supportsOfflineUse: Boolean,
) {
    init {
        require(id.isNotBlank())
        require(styleUrl.startsWith("https://") || styleUrl.startsWith("asset://"))
        require(attribution.contains("OpenStreetMap", ignoreCase = true))
        require(!styleUrl.contains("tile.openstreetmap.org", ignoreCase = true))
    }
}

data class EclipseMapOverlay(
    val centreline: List<GeoPoint>,
    val northernLimit: List<GeoPoint>,
    val southernLimit: List<GeoPoint>,
    val pathUncertaintyKilometers: Double,
) {
    init { require(pathUncertaintyKilometers >= 0.0) }
}

data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)
    }
}

data class EclipseMapConfiguration(
    val tileSource: MapTileSource?,
    val overlay: EclipseMapOverlay,
) {
    val basemapAvailable: Boolean get() = tileSource != null

    companion object {
        const val REQUIRED_ATTRIBUTION = "© OpenStreetMap contributors"
    }
}
