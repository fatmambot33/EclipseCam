package com.fatmambo33.eclipsecam.device.orientation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Local sensor-fusion adapter for phone pointing and tripod stability.
 *
 * The rotation-vector sensor is preferred because Android fuses accelerometer,
 * gyroscope, and magnetometer data. A game-rotation vector fallback still
 * provides tilt and roll, but azimuth confidence is deliberately reduced.
 */
class AndroidOrientationTracker(
    context: Context,
    private val thresholds: StabilityThresholds = StabilityThresholds(),
) : SensorEventListener, AutoCloseable {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val absoluteRotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val relativeRotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val selectedRotationSensor = absoluteRotationSensor ?: relativeRotationSensor
    private val stabilityClassifier = StabilityClassifier(thresholds)
    private val mutableState = MutableStateFlow(
        OrientationState(sensorAvailable = selectedRotationSensor != null),
    )

    val state: StateFlow<OrientationState> = mutableState.asStateFlow()

    private var latestAngularSpeed = 0.0
    private var running = false

    fun start() {
        if (running) return
        running = true
        selectedRotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        if (selectedRotationSensor == null) {
            mutableState.value = OrientationState(sensorAvailable = false)
        }
    }

    fun stop() {
        if (!running) return
        sensorManager.unregisterListener(this)
        running = false
        latestAngularSpeed = 0.0
        mutableState.value = mutableState.value.copy(
            stability = StabilityState.UNAVAILABLE,
            angularSpeedDegreesPerSecond = 0.0,
        )
    }

    override fun close() = stop()

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> updateAngularSpeed(event.values)
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_GAME_ROTATION_VECTOR -> updateOrientation(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val confidence = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> OrientationConfidence.HIGH
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> OrientationConfidence.MEDIUM
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> OrientationConfidence.LOW
            else -> OrientationConfidence.LOW
        }
        mutableState.value = mutableState.value.copy(confidence = confidence)
    }

    private fun updateAngularSpeed(values: FloatArray) {
        val radiansPerSecond = sqrt(
            values[0].toDouble() * values[0] +
                values[1].toDouble() * values[1] +
                values[2].toDouble() * values[2],
        )
        latestAngularSpeed = radiansPerSecond * 180.0 / PI
    }

    private fun updateOrientation(event: SensorEvent) {
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val displayAdjusted = remapForDisplayRotation(rotationMatrix, currentDisplayRotation())
        val angles = FloatArray(3)
        SensorManager.getOrientation(displayAdjusted, angles)

        val orientation = PhoneOrientation(
            azimuthDegrees = normalizeAzimuthDegrees(Math.toDegrees(angles[0].toDouble())),
            elevationDegrees = normalizeSignedDegrees(-Math.toDegrees(angles[1].toDouble())),
            rollDegrees = normalizeSignedDegrees(Math.toDegrees(angles[2].toDouble())),
        )
        val stability = stabilityClassifier.classify(
            angularSpeedDegreesPerSecond = latestAngularSpeed,
            sensorAvailable = gyroscope != null,
        )
        val confidence = when {
            event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR -> OrientationConfidence.LOW
            mutableState.value.confidence == OrientationConfidence.UNAVAILABLE -> OrientationConfidence.MEDIUM
            else -> mutableState.value.confidence
        }
        mutableState.value = OrientationState(
            orientation = orientation,
            confidence = confidence,
            stability = stability,
            angularSpeedDegreesPerSecond = latestAngularSpeed,
            sensorAvailable = true,
        )
    }

    @Suppress("DEPRECATION")
    private fun currentDisplayRotation(): Int = windowManager.defaultDisplay.rotation

    private fun remapForDisplayRotation(matrix: FloatArray, rotation: Int): FloatArray {
        val output = FloatArray(9)
        val (xAxis, yAxis) = when (rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(matrix, xAxis, yAxis, output)
        return output
    }
}
