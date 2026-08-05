package com.fatmambo33.eclipsecam.camera.capabilities

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build

/** Stable, Android-independent camera output dimensions. */
data class CameraOutputSize(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "Camera output dimensions must be positive" }
    }

    val pixelCount: Long = width.toLong() * height
}

data class CameraCapabilities(
    val cameraId: String,
    val facing: LensFacing,
    val sensorOrientationDegrees: Int,
    val minimumZoomRatio: Float,
    val maximumZoomRatio: Float,
    val jpegSizes: List<CameraOutputSize>,
    val rawSupported: Boolean,
    val manualSensorSupported: Boolean,
    val manualFocusSupported: Boolean,
    val exposureCompensationRange: IntRange?,
)

enum class LensFacing { FRONT, BACK, EXTERNAL, UNKNOWN }

/** Framework-neutral snapshot used by the deterministic mapper and JVM tests. */
internal data class CameraHardwareSnapshot(
    val cameraId: String,
    val lensFacingValue: Int?,
    val sensorOrientationDegrees: Int?,
    val minimumZoomRatio: Float?,
    val maximumZoomRatio: Float?,
    val maximumDigitalZoom: Float?,
    val jpegSizes: List<CameraOutputSize>,
    val rawSupported: Boolean,
    val manualSensorSupported: Boolean,
    val minimumFocusDistanceDiopters: Float?,
    val exposureCompensationLower: Int?,
    val exposureCompensationUpper: Int?,
)

internal fun mapCameraCapabilities(snapshot: CameraHardwareSnapshot): CameraCapabilities {
    val minimumZoom = snapshot.minimumZoomRatio?.takeIf { it.isFinite() && it > 0f } ?: 1f
    val maximumZoom = listOfNotNull(
        snapshot.maximumZoomRatio?.takeIf { it.isFinite() && it >= minimumZoom },
        snapshot.maximumDigitalZoom?.takeIf { it.isFinite() && it >= minimumZoom },
    ).maxOrNull() ?: minimumZoom
    val exposureRange = if (
        snapshot.exposureCompensationLower != null &&
        snapshot.exposureCompensationUpper != null &&
        snapshot.exposureCompensationLower <= snapshot.exposureCompensationUpper
    ) {
        snapshot.exposureCompensationLower..snapshot.exposureCompensationUpper
    } else {
        null
    }

    return CameraCapabilities(
        cameraId = snapshot.cameraId,
        facing = mapLensFacing(snapshot.lensFacingValue),
        sensorOrientationDegrees = normalizeSensorOrientation(snapshot.sensorOrientationDegrees),
        minimumZoomRatio = minimumZoom,
        maximumZoomRatio = maximumZoom,
        jpegSizes = snapshot.jpegSizes.distinct().sortedByDescending(CameraOutputSize::pixelCount),
        rawSupported = snapshot.rawSupported,
        manualSensorSupported = snapshot.manualSensorSupported,
        manualFocusSupported = (snapshot.minimumFocusDistanceDiopters ?: 0f) > 0f,
        exposureCompensationRange = exposureRange,
    )
}

class CameraCapabilityInventory(context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun readAll(): List<CameraCapabilities> = cameraManager.cameraIdList.map { cameraId ->
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val flags = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toSet().orEmpty()
        val zoomRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        } else {
            null
        }
        val streamMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val compensation = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        mapCameraCapabilities(
            CameraHardwareSnapshot(
                cameraId = cameraId,
                lensFacingValue = characteristics.get(CameraCharacteristics.LENS_FACING),
                sensorOrientationDegrees = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION),
                minimumZoomRatio = zoomRange?.lower,
                maximumZoomRatio = zoomRange?.upper,
                maximumDigitalZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM),
                jpegSizes = streamMap?.getOutputSizes(ImageFormat.JPEG)?.map {
                    CameraOutputSize(it.width, it.height)
                }.orEmpty(),
                rawSupported = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW in flags,
                manualSensorSupported = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in flags,
                minimumFocusDistanceDiopters = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE),
                exposureCompensationLower = compensation?.lower,
                exposureCompensationUpper = compensation?.upper,
            ),
        )
    }
}

internal fun mapLensFacing(value: Int?): LensFacing = when (value) {
    CameraCharacteristics.LENS_FACING_FRONT -> LensFacing.FRONT
    CameraCharacteristics.LENS_FACING_BACK -> LensFacing.BACK
    CameraCharacteristics.LENS_FACING_EXTERNAL -> LensFacing.EXTERNAL
    else -> LensFacing.UNKNOWN
}

internal fun normalizeSensorOrientation(value: Int?): Int = when (value) {
    0, 90, 180, 270 -> value
    else -> 0
}
