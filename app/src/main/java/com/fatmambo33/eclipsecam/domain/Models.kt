package com.fatmambo33.eclipsecam.domain

import java.time.Duration
import java.time.Instant

data class GeoPoint(val latitude: Double, val longitude: Double, val altitudeMeters: Double = 0.0)

data class PathSample(
    val instant: Instant,
    val northLimit: GeoPoint,
    val center: GeoPoint,
    val southLimit: GeoPoint,
    val widthKm: Double,
)

data class HorizontalCoordinates(val azimuthDeg: Double, val elevationDeg: Double)
data class DeviceAttitude(val azimuthDeg: Double, val pitchDeg: Double, val rollDeg: Double)

data class AimError(val horizontalDeg: Double, val verticalDeg: Double) {
    val aligned: Boolean get() = kotlin.math.abs(horizontalDeg) < 1.5 && kotlin.math.abs(verticalDeg) < 1.5
}

data class ObserverState(
    val point: GeoPoint,
    val horizontalAccuracyMeters: Double,
    val verticalAccuracyMeters: Double?,
    val speedMetersPerSecond: Double,
    val bearingDegrees: Double?,
    val timestampUtc: Instant,
    val source: LocationSource,
)

enum class LocationSource { GNSS, NETWORK, PASSIVE, MANUAL }
enum class EclipsePhase { BEFORE, PARTIAL, TOTAL, AFTER }

data class ContactTimes(
    val c1: Instant?,
    val c2: Instant?,
    val maximum: Instant?,
    val c3: Instant?,
    val c4: Instant?,
)

data class PersonalEclipseState(
    val observer: ObserverState,
    val instant: Instant,
    val phase: EclipsePhase,
    val magnitude: Double,
    val obscuration: Double,
    val sun: HorizontalCoordinates,
    val centerlineDistanceKm: Double,
    val signedCrossTrackKm: Double,
    val estimatedTotalityDuration: Duration?,
    val nextContactName: String?,
    val nextContactInstant: Instant?,
    val shadow: PathSample,
    val contacts: ContactTimes,
    val confidence: Confidence,
)

data class Confidence(
    val astronomy: Level,
    val location: Level,
    val heading: Level,
    val notes: List<String>,
) {
    enum class Level { HIGH, MEDIUM, LOW }
}
