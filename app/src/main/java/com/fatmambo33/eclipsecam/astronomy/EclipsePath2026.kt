package com.fatmambo33.eclipsecam.astronomy

/** Geographic path point from the NASA/GSFC 2026-08-12 path table. */
data class EclipsePathPoint(
    val utcMinute: Int,
    val north: GeoPoint?,
    val center: GeoPoint,
    val south: GeoPoint,
    val pathWidthKm: Double,
    val centralDurationSeconds: Double,
)

data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * Offline WGS-84 reference geometry for the European part of the totality path.
 *
 * The bold map centreline is formed from [points].centre. Northern and southern
 * limits remain visually secondary. The lunar limb can shift the true limits by
 * roughly 1-3 km, so UI must not imply metre-level boundary precision.
 */
object EclipsePath2026 {
    const val limbUncertaintyKm = 3.0

    val points = listOf(
        point("17:40", "68 43.6N 22 56.9W", "68 14.8N 26 24.6W", "67 43.4N 29 37.9W", 289.0, "02:17.9"),
        point("17:44", "66 37.6N 22 26.2W", "66 11.1N 25 37.8W", "65 42.2N 28 37.5W", 292.0, "02:18.2"),
        point("17:46", "65 35.6N 22 07.2W", "65 10.3N 25 12.3W", "64 42.6N 28 06.4W", 294.0, "02:18.2"),
        point("17:50", "63 33.4N 21 22.9W", "63 10.3N 24 17.2W", "62 45.0N 27 02.0W", 298.0, "02:17.9"),
        point("18:00", "58 33.6N 18 56.6W", "58 16.3N 21 34.4W", "57 56.7N 24 04.6W", 307.0, "02:15.3"),
        point("18:10", "53 32.8N 15 30.2W", "53 22.3N 18 03.4W", "53 09.1N 20 29.1W", 316.0, "02:10.0"),
        point("18:20", "48 12.5N 10 16.0W", "48 12.7N 13 02.9W", "48 08.8N 15 38.3W", 319.0, "02:01.2"),
        point("18:22", "47 02.3N 08 48.1W", "47 06.1N 11 42.9W", "47 05.0N 14 23.8W", 318.0, "01:58.8"),
        point("18:24", "45 48.1N 07 04.6W", "45 56.6N 10 11.4W", "45 59.0N 13 00.5W", 315.0, "01:56.1"),
        point("18:26", "44 27.4N 04 56.9W", "44 42.8N 08 23.9W", "44 49.9N 11 25.2W", 311.0, "01:53.0"),
        point("18:28", "42 54.5N 02 05.1W", "43 22.3N 06 11.3W", "43 36.4N 09 33.1W", 304.0, "01:49.3"),
        point("18:30", "40 39.9N 03 17.7E", "41 49.0N 03 11.1W", "42 15.8N 07 14.2W", 294.0, "01:44.6"),
        point("18:32", null, "39 24.5N 02 57.0E", "40 41.0N 04 02.4W", 270.0, "01:35.8"),
    )

    val centerLine: List<GeoPoint> = points.map { it.center }
    val northernLimit: List<GeoPoint> = points.mapNotNull { it.north }
    val southernLimit: List<GeoPoint> = points.map { it.south }

    private fun point(
        time: String,
        north: String?,
        center: String,
        south: String,
        widthKm: Double,
        duration: String,
    ) = EclipsePathPoint(
        utcMinute = time.substringBefore(':').toInt() * 60 + time.substringAfter(':').toInt(),
        north = north?.let(::parseCoordinate),
        center = parseCoordinate(center),
        south = parseCoordinate(south),
        pathWidthKm = widthKm,
        centralDurationSeconds = parseDuration(duration),
    )

    private fun parseCoordinate(value: String): GeoPoint {
        val parts = value.split(' ')
        val latDegrees = parts[0].toDouble()
        val latMinutesAndHemisphere = parts[1]
        val lonDegrees = parts[2].toDouble()
        val lonMinutesAndHemisphere = parts[3]
        val latitude = signedDegrees(latDegrees, latMinutesAndHemisphere)
        val longitude = signedDegrees(lonDegrees, lonMinutesAndHemisphere)
        return GeoPoint(latitude, longitude)
    }

    private fun signedDegrees(degrees: Double, minutesAndHemisphere: String): Double {
        val hemisphere = minutesAndHemisphere.last()
        val minutes = minutesAndHemisphere.dropLast(1).toDouble()
        val absolute = degrees + minutes / 60.0
        return if (hemisphere == 'S' || hemisphere == 'W') -absolute else absolute
    }

    private fun parseDuration(value: String): Double {
        val minutes = value.substringBefore(':').toDouble()
        val seconds = value.substringAfter(':').toDouble()
        return minutes * 60.0 + seconds
    }
}
