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

/** Explicit outcome of the bounded numerical search. */
enum class SolverStatus {
    CONVERGED,
    NO_ECLIPSE,
    FAILED,
}

/** Public contract for the bounded root and minimum searches. */
data class SolverDiagnostics(
    val status: SolverStatus,
    val rootToleranceMillis: Long,
    val maximumRootIterations: Int,
    val maximumMinimumIterations: Int,
)

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
) {
    /**
     * Numerical outcome exposed to callers instead of leaving convergence implicit.
     *
     * A geometrically valid search with no external contacts is not a numerical
     * failure; it is reported as [SolverStatus.NO_ECLIPSE].
     */
    val solverDiagnostics: SolverDiagnostics
        get() = SolverDiagnostics(
            status = when {
                !modelValid -> SolverStatus.FAILED
                visibility == EclipseVisibility.NONE -> SolverStatus.NO_ECLIPSE
                else -> SolverStatus.CONVERGED
            },
            rootToleranceMillis = ROOT_TOLERANCE_MILLIS,
            maximumRootIterations = MAXIMUM_ROOT_ITERATIONS,
            maximumMinimumIterations = MAXIMUM_MINIMUM_ITERATIONS,
        )

    private companion object {
        const val ROOT_TOLERANCE_MILLIS = 10L
        const val MAXIMUM_ROOT_ITERATIONS = 50
        const val MAXIMUM_MINIMUM_ITERATIONS = 80
    }
}

/**
 * Calculates eclipse circumstances without network access.
 *
 * [instantUtc] identifies the eclipse event and must lie inside the published
 * Besselian-element validity window. Implementations must return scientifically
 * validated values or fail explicitly; they must never silently substitute
 * approximate path-table interpolation for contact circumstances.
 */
fun interface LocalCircumstancesCalculator {
    fun calculate(instantUtc: Instant, observer: Observer): LocalEclipseCircumstances
}
