package com.fatmambo33.eclipsecam.device.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.fatmambo33.eclipsecam.astronomy.GeoPoint
import java.time.Instant
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Android [LocationManager]-backed repository. Location data never leaves the device. */
class AndroidLocationRepository(
    context: Context,
    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager,
    private val now: () -> Instant = Instant::now,
) : LocationRepository {
    private val appContext = context.applicationContext

    override fun observe(): Flow<LocationState> = callbackFlow {
        val permission = permissionState()
        if (permission == LocationPermission.DENIED) {
            trySend(LocationState.PermissionRequired())
            close()
            return@callbackFlow
        }

        val providers = enabledProviders(permission)
        if (providers.isEmpty()) {
            trySend(LocationState.Unavailable)
            close()
            return@callbackFlow
        }

        trySend(LocationState.Acquiring)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(
                    LocationState.Available(
                        permission = permission,
                        location = location.toObserverLocation(),
                    ),
                )
            }

            override fun onProviderDisabled(provider: String) {
                if (enabledProviders(permission).isEmpty()) trySend(LocationState.Unavailable)
            }

            override fun onProviderEnabled(provider: String) = Unit
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        try {
            providers.forEach { provider ->
                @Suppress("MissingPermission")
                locationManager.requestLocationUpdates(provider, 1_000L, 0f, listener, Looper.getMainLooper())
                @Suppress("MissingPermission")
                locationManager.getLastKnownLocation(provider)?.let(listener::onLocationChanged)
            }
        } catch (securityException: SecurityException) {
            trySend(LocationState.PermissionRequired())
            close(securityException)
        } catch (exception: RuntimeException) {
            trySend(LocationState.Error(exception.message ?: "Unable to start location updates"))
            close(exception)
        }

        awaitClose { locationManager.removeUpdates(listener) }
    }

    private fun permissionState(): LocationPermission = when {
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED -> LocationPermission.PRECISE
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED -> LocationPermission.COARSE
        else -> LocationPermission.DENIED
    }

    private fun enabledProviders(permission: LocationPermission): List<String> {
        val candidates = buildList {
            if (permission == LocationPermission.PRECISE) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        return candidates.filter { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        }
    }

    private fun Location.toObserverLocation(): ObserverLocation = ObserverLocation(
        point = GeoPoint(latitude = latitude, longitude = longitude),
        accuracyMeters = if (hasAccuracy()) accuracy.toDouble() else Double.POSITIVE_INFINITY,
        altitudeMeters = if (hasAltitude()) altitude else null,
        provider = provider ?: "unknown",
        capturedAt = if (time > 0L) Instant.ofEpochMilli(time) else now(),
    )
}
