package com.fatmambo33.eclipsecam.device.location

import com.fatmambo33.eclipsecam.astronomy.ObserverPathGuidance
import com.fatmambo33.eclipsecam.astronomy.ObserverPathGuidanceEngine
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Presentation-ready state combining GPS quality and centreline guidance. */
sealed interface ObserverGuidanceState {
    data object PermissionRequired : ObserverGuidanceState
    data object Unavailable : ObserverGuidanceState
    data object Acquiring : ObserverGuidanceState
    data class Ready(
        val location: ObserverLocation,
        val guidance: ObserverPathGuidance,
        val stale: Boolean,
        val lowAccuracy: Boolean,
    ) : ObserverGuidanceState
    data class Error(val message: String) : ObserverGuidanceState
}

/** Converts local location updates into continuously refreshed eclipse guidance. */
class ObserverGuidanceFlow(
    private val repository: LocationRepository,
    private val now: () -> Instant = Instant::now,
    private val staleAfter: Duration = ObserverLocation.DEFAULT_STALE_AFTER,
    private val lowAccuracyThresholdMeters: Double = 100.0,
) {
    init {
        require(!staleAfter.isNegative && !staleAfter.isZero) { "Stale threshold must be positive" }
        require(lowAccuracyThresholdMeters > 0.0) { "Accuracy threshold must be positive" }
    }

    fun observe(): Flow<ObserverGuidanceState> = repository.observe().map { state ->
        when (state) {
            is LocationState.PermissionRequired -> ObserverGuidanceState.PermissionRequired
            LocationState.Unavailable -> ObserverGuidanceState.Unavailable
            LocationState.Acquiring -> ObserverGuidanceState.Acquiring
            is LocationState.Error -> ObserverGuidanceState.Error(state.message)
            is LocationState.Available -> ObserverGuidanceState.Ready(
                location = state.location,
                guidance = ObserverPathGuidanceEngine.calculate(state.location.point),
                stale = state.location.isStale(now(), staleAfter),
                lowAccuracy = state.location.accuracyMeters > lowAccuracyThresholdMeters,
            )
        }
    }
}
