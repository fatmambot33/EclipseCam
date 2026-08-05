package com.fatmambo33.eclipsecam.device.health

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs

/** Reads capture-relevant phone health without network access or telemetry. */
class AndroidDeviceHealthReader(private val context: Context) {
    fun read(): DeviceHealthSnapshot {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryManager
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL,
            -> true
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING,
            -> false
            else -> null
        }
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            mapThermalPressure(powerManager.currentThermalStatus)
        } else {
            ThermalPressure.UNKNOWN
        }
        val storage = runCatching {
            StatFs(context.filesDir.absolutePath).availableBytes
        }.getOrNull()?.takeIf { it >= 0L }

        return DeviceHealthSnapshot(
            batteryPercent = level,
            charging = charging,
            thermalPressure = thermal,
            availableStorageBytes = storage,
        )
    }
}

internal fun mapThermalPressure(status: Int): ThermalPressure = when (status) {
    PowerManager.THERMAL_STATUS_NONE -> ThermalPressure.NONE
    PowerManager.THERMAL_STATUS_LIGHT -> ThermalPressure.LIGHT
    PowerManager.THERMAL_STATUS_MODERATE -> ThermalPressure.MODERATE
    PowerManager.THERMAL_STATUS_SEVERE -> ThermalPressure.SEVERE
    PowerManager.THERMAL_STATUS_CRITICAL -> ThermalPressure.CRITICAL
    PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalPressure.EMERGENCY
    PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalPressure.SHUTDOWN
    else -> ThermalPressure.UNKNOWN
}
