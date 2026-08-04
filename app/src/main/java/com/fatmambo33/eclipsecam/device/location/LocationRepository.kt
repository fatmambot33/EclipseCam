package com.fatmambo33.eclipsecam.device.location

import kotlinx.coroutines.flow.Flow

/** Local-only source of observer location updates. */
fun interface LocationRepository {
    fun observe(): Flow<LocationState>
}
