package com.fatmambo33.eclipsecam.capture

import android.content.Context
import com.fatmambo33.eclipsecam.device.health.AndroidDeviceHealthReader
import com.fatmambo33.eclipsecam.device.health.DeviceHealthDecision
import com.fatmambo33.eclipsecam.device.health.DeviceHealthPolicy
import com.fatmambo33.eclipsecam.device.health.DeviceHealthSnapshot

/** Reads and evaluates fresh local device health before every foreground-runtime tick. */
class AndroidCaptureRuntimeHealthProvider private constructor(
    private val readSnapshot: () -> DeviceHealthSnapshot,
    private val policy: DeviceHealthPolicy,
) : CaptureRuntimeHealthProvider {
    constructor(
        context: Context,
        policy: DeviceHealthPolicy = DeviceHealthPolicy(),
    ) : this(
        readSnapshot = AndroidDeviceHealthReader(context.applicationContext)::read,
        policy = policy,
    )

    internal constructor(
        reader: () -> DeviceHealthSnapshot,
        policy: DeviceHealthPolicy,
        testOnly: Unit = Unit,
    ) : this(readSnapshot = reader, policy = policy)

    override fun current(): DeviceHealthDecision = policy.evaluate(readSnapshot())
}
