package com.fatmambo33.eclipsecam.map

import com.fatmambo33.eclipsecam.astronomy.EclipsePath2026
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Provider-independent local geometry rendered above any optional basemap. */
data class EclipseMapScene(
    val overlay: EclipseMapOverlay,
    val observer: GeoPoint? = null,
    val observerAccuracyMeters: Double? = null,
) {
    init {
        require(observerAccuracyMeters == null || observerAccuracyMeters >= 0.0)
        require(observer != null || observerAccuracyMeters == null)
    }

    val accuracyRing: List<GeoPoint> = if (observer != null && observerAccuracyMeters != null) {
        accuracyCircle(observer, observerAccuracyMeters)
    } else {
        emptyList()
    }
}

/** Stable 12 August 2026 path geometry converted to the map package contract. */
object EclipseMapScene2026 {
    val overlay = EclipseMapOverlay(
        centreline = EclipsePath2026.centerLine.map { GeoPoint(it.latitude, it.longitude) },
        northernLimit = EclipsePath2026.northernLimit.map { GeoPoint(it.latitude, it.longitude) },
        southernLimit = EclipsePath2026.southernLimit.map { GeoPoint(it.latitude, it.longitude) },
        pathUncertaintyKilometers = EclipsePath2026.limbUncertaintyKm,
    )
}

/**
 * Builds a closed WGS-84 approximation of a GPS accuracy circle.
 *
 * This is presentation geometry only. It deliberately represents the reported
 * Android horizontal accuracy instead of implying metre-level eclipse-boundary precision.
 */
internal fun accuracyCircle(
    center: GeoPoint,
    radiusMeters: Double,
    segments: Int = 48,
): List<GeoPoint> {
    require(radiusMeters >= 0.0)
    require(segments >= 8)
    if (radiusMeters == 0.0) return listOf(center, center)

    val earthRadiusMeters = 6_371_008.8
    val latitudeRadians = center.latitude * PI / 180.0
    val latitudeDegreesPerMeter = 180.0 / (PI * earthRadiusMeters)
    val longitudeDegreesPerMeter = latitudeDegreesPerMeter / cos(latitudeRadians).coerceAtLeast(1e-6)

    val points = (0 until segments).map { index ->
        val angle = 2.0 * PI * index / segments
        GeoPoint(
            latitude = center.latitude + sin(angle) * radiusMeters * latitudeDegreesPerMeter,
            longitude = center.longitude + cos(angle) * radiusMeters * longitudeDegreesPerMeter,
        )
    }
    return points + points.first()
}
