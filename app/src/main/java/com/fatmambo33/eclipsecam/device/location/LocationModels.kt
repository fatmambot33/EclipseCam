package com.fatmambo33.eclipsecam.device.location

import com.fatmambo33.eclipsecam.astronomy.GeoPoint
import java.time.Duration
import java.time.Instant

/** Permission state relevant to on-device observer positioning. */
enum class LocationPermission {
    DENIED,
    COARSE,
    PRECISE,
}

/** One immutable location sample. */
data class ObserverLocation(
    val point: GeoPoint,
    val accuracyMeters: Double,
    val altitudeMeters: Double?,
    val provider: String,
    val capturedAt: Instant,
) {
    init {
        require(accuracyMeters >= 0.0) { "Accuracy must not be negative" }
        require(provider.isNotBlank()) { "Provider must not be blank" }
    }

    /** Age of this sample at [now]. */
    fun age(now: Instant): Duration = Duration.between(capturedAt, now).coerceAtLeast(Duration.ZERO)

    /** Whether the sample is older than the accepted freshness window. */
    fun isStale(now: Instant, staleAfter: Duration = DEFAULT_STALE_AFTER): Boolean =
        age(now) > staleAfter

    companion object {
        val DEFAULT_STALE_AFTER: Duration = Duration.ofSeconds(30)
    }
}

/** Complete state exposed by a location repository. */
sealed interface LocationState {
    data class PermissionRequired(val permission: LocationPermission = LocationPermission.DENIED) : LocationState

    data object Unavailable : LocationState

    data object Acquiring : LocationState

    data class Available(
        val permission: LocationPermission,
        val location: ObserverLocation,
    ) : LocationState

    data class Error(val message: String) : LocationState
}
