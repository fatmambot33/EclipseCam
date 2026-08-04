package com.fatmambo33.eclipsecam.device.location

import com.fatmambo33.eclipsecam.astronomy.GeoPoint
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserverGuidanceFlowTest {
    private val now = Instant.parse("2026-08-12T17:45:00Z")

    @Test
    fun `available location produces guidance`() = runBlocking {
        val location = ObserverLocation(
            point = GeoPoint(65.0, -25.0),
            accuracyMeters = 8.0,
            altitudeMeters = 12.0,
            provider = "gps",
            capturedAt = now.minusSeconds(5),
        )
        val repository = LocationRepository {
            flowOf(LocationState.Available(LocationPermission.PRECISE, location))
        }

        val state = ObserverGuidanceFlow(repository, now = { now }).observe().first()

        assertTrue(state is ObserverGuidanceState.Ready)
        state as ObserverGuidanceState.Ready
        assertFalse(state.stale)
        assertFalse(state.lowAccuracy)
        assertTrue(state.guidance.distanceKm >= 0.0)
        assertTrue(state.guidance.bearingDegrees in 0.0..<360.0)
    }

    @Test
    fun `old inaccurate location is marked stale and low accuracy`() = runBlocking {
        val location = ObserverLocation(
            point = GeoPoint(48.0, -2.0),
            accuracyMeters = 250.0,
            altitudeMeters = null,
            provider = "network",
            capturedAt = now.minusSeconds(60),
        )
        val repository = LocationRepository {
            flowOf(LocationState.Available(LocationPermission.COARSE, location))
        }

        val state = ObserverGuidanceFlow(repository, now = { now }).observe().first()

        assertTrue(state is ObserverGuidanceState.Ready)
        state as ObserverGuidanceState.Ready
        assertTrue(state.stale)
        assertTrue(state.lowAccuracy)
    }

    @Test
    fun `permission and unavailable states degrade gracefully`() = runBlocking {
        val permissionState = ObserverGuidanceFlow(
            LocationRepository { flowOf(LocationState.PermissionRequired()) },
        ).observe().first()
        val unavailableState = ObserverGuidanceFlow(
            LocationRepository { flowOf(LocationState.Unavailable) },
        ).observe().first()

        assertTrue(permissionState is ObserverGuidanceState.PermissionRequired)
        assertTrue(unavailableState is ObserverGuidanceState.Unavailable)
    }
}
