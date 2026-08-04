package com.fatmambo33.eclipsecam.device.orientation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlin.math.sqrt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Android sensor-backed orientation repository.
 *
 * Sensor data remains in-process and is exposed only while the returned flow is collected.
 */
class AndroidOrientationRepository(
    context: Context,
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager,
    private val displayRotation: () -> DisplayRotation = {
        @Suppress("DEPRECATION")
        val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay.rotation
        rotation.toDisplayRotation()
    },
    private val processor: OrientationSampleProcessor = OrientationSampleProcessor(),
) : OrientationRepository {

    override fun observe(): Flow<OrientationState> = callbackFlow {
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        if (rotationSensor == null) {
            trySend(
                processor.process(
                    sample = null,
                    displayRotation = displayRotation(),
                    angularSpeedDegreesPerSecond = 0.0,
                ),
            )
            close()
            return@callbackFlow
        }

        var angularSpeedDegreesPerSecond = 0.0
        var rotationAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_GYROSCOPE -> {
                        angularSpeedDegreesPerSecond = angularSpeedDegreesPerSecond(event.values)
                    }
                    Sensor.TYPE_ROTATION_VECTOR,
                    Sensor.TYPE_GAME_ROTATION_VECTOR,
                    -> {
                        val values = event.values
                        if (values.size < 3) return
                        trySend(
                            processor.process(
                                sample = RotationVectorSample(
                                    x = values[0].toDouble(),
                                    y = values[1].toDouble(),
                                    z = values[2].toDouble(),
                                    scalar = values.getOrNull(3)?.toDouble(),
                                    accuracy = rotationAccuracy,
                                ),
                                displayRotation = displayRotation(),
                                angularSpeedDegreesPerSecond = angularSpeedDegreesPerSecond,
                            ),
                        )
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                if (sensor.type == rotationSensor.type) rotationAccuracy = accuracy
            }
        }

        val rotationRegistered = sensorManager.registerListener(
            listener,
            rotationSensor,
            SensorManager.SENSOR_DELAY_GAME,
        )
        if (!rotationRegistered) {
            trySend(
                processor.process(
                    sample = null,
                    displayRotation = displayRotation(),
                    angularSpeedDegreesPerSecond = 0.0,
                ),
            )
            close()
            return@callbackFlow
        }

        gyroscope?.let { sensor ->
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }

        awaitClose { sensorManager.unregisterListener(listener) }
    }
}

internal fun angularSpeedDegreesPerSecond(values: FloatArray): Double {
    require(values.size >= 3) { "Gyroscope sample must contain three axes" }
    val radiansPerSecond = sqrt(
        values[0].toDouble() * values[0] +
            values[1].toDouble() * values[1] +
            values[2].toDouble() * values[2],
    )
    return Math.toDegrees(radiansPerSecond)
}

internal fun Int.toDisplayRotation(): DisplayRotation = when (this) {
    Surface.ROTATION_90 -> DisplayRotation.ROTATION_90
    Surface.ROTATION_180 -> DisplayRotation.ROTATION_180
    Surface.ROTATION_270 -> DisplayRotation.ROTATION_270
    else -> DisplayRotation.ROTATION_0
}
