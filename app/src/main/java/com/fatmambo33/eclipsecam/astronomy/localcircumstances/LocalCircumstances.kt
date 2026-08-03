package com.fatmambo33.eclipsecam.astronomy.localcircumstances

import java.time.Instant

/** Geographic observer used for local eclipse calculations. */
data class Observer(
    val latitudeDegrees: Double,
    val longitudeDegrees: Double,
    val elevationMeters: Double = 0.0,
) {
    init {
        require(latitudeDegrees in -90.0..90.0) { "Latitude must be between -90 and 90 degrees" }
        require(longitudeDegrees in -180.0..180.0) { "Longitude must be between -180 and 180 degrees" }
        require(elevationMeters >= -500.0) { "Observer elevation is outside the supported range" }
    }
}

enum class EclipseVisibility {
    NONE,
    PARTIAL,
    TOTAL,
}

enum class EclipseContact {
    C1,
    C2,
    MAXIMUM,
    C3,
    C4,
}

data class ContactCircumstance(
    val contact: EclipseContact,
    val instantUtc: Instant,
    val sunAltitudeDegrees: Double,
    val sunAzimuthDegrees: Double,
)

data class ModelUncertainty(
    val timingSeconds: Double,
    val pathKilometers: Double,
    val notes: String,
)

data class LocalEclipseCircumstances(
    val observer: Observer,
    val visibility: EclipseVisibility,
    val contacts: Map<EclipseContact, ContactCircumstance>,
    val magnitude: Double,
    val obscuration: Double,
    val totalityDurationSeconds: Double?,
    val uncertainty: ModelUncertainty,
    val modelValid: Boolean,
)

/**
 * Calculates eclipse circumstances without network access.
 *
 * Implementations must return scientifically validated values or fail explicitly;
 * they must never silently substitute approximate path-table interpolation for
 * contact circumstances.
 */
fun interface LocalCircumstancesCalculator {
    fun calculate(observer: Observer): LocalEclipseCircumstances
}
