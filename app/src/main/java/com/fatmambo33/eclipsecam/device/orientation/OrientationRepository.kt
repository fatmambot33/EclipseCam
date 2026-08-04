package com.fatmambo33.eclipsecam.device.orientation

import kotlinx.coroutines.flow.Flow

/** Local-only stream of phone orientation and stability state. */
interface OrientationRepository {
    fun observe(): Flow<OrientationState>
}
