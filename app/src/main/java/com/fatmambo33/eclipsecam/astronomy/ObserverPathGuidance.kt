package com.fatmambo33.eclipsecam.astronomy

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** User-centred guidance relative to the validated 2026 eclipse centreline. */
data class ObserverPathGuidance(
    val observer: GeoPoint,
    val nearestCenter: GeoPoint,
    val distanceKm: Double,
    val bearingDegrees: Double,
    val referenceUtcMinute: Int,
    val referenceDurationSeconds: Double,
    val pathWidthKm: Double,
    val insideReferencePath: Boolean,
    val boundaryUncertaintyKm: Double,
)

/** Computes practical observer guidance entirely on-device. */
object ObserverPathGuidanceEngine {
    fun calculate(observer: GeoPoint): ObserverPathGuidance {
        require(observer.latitude in -90.0..90.0) { "Latitude must be valid" }
        require(observer.longitude in -180.0..180.0) { "Longitude must be valid" }

        val nearest = EclipsePath2026.points.minBy { point ->
            haversineKm(observer, point.center)
        }
        val distance = haversineKm(observer, nearest.center)
        val halfWidth = nearest.pathWidthKm / 2.0

        return ObserverPathGuidance(
            observer = observer,
            nearestCenter = nearest.center,
            distanceKm = distance,
            bearingDegrees = initialBearingDegrees(observer, nearest.center),
            referenceUtcMinute = nearest.utcMinute,
            referenceDurationSeconds = nearest.centralDurationSeconds,
            pathWidthKm = nearest.pathWidthKm,
            insideReferencePath = distance <= halfWidth,
            boundaryUncertaintyKm = EclipsePath2026.limbUncertaintyKm,
        )
    }

    internal fun haversineKm(a: GeoPoint, b: GeoPoint): Double {
        val earthRadiusKm = 6371.0088
        val lat1 = a.latitude.toRadians()
        val lat2 = b.latitude.toRadians()
        val deltaLat = (b.latitude - a.latitude).toRadians()
        val deltaLon = (b.longitude - a.longitude).toRadians()
        val h = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
        return 2 * earthRadiusKm * atan2(sqrt(h), sqrt(1 - h))
    }

    internal fun initialBearingDegrees(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = from.latitude.toRadians()
        val lat2 = to.latitude.toRadians()
        val deltaLon = (to.longitude - from.longitude).toRadians()
        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        return (atan2(y, x) * 180.0 / PI + 360.0) % 360.0
    }

    private fun Double.toRadians(): Double = this * PI / 180.0
}
