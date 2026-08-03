package com.fatmambo33.eclipsecam.camera.capabilities

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Range
import android.util.Size

/** Honest capability snapshot for one physical or logical camera. */
data class CameraCapabilities(
    val cameraId: String,
    val facing: LensFacing,
    val sensorOrientationDegrees: Int,
    val minimumZoomRatio: Float,
    val maximumZoomRatio: Float,
    val jpegSizes: List<Size>,
    val rawSupported: Boolean,
    val manualSensorSupported: Boolean,
    val manualFocusSupported: Boolean,
    val exposureCompensationRange: IntRange?,
)

enum class LensFacing { FRONT, BACK, EXTERNAL, UNKNOWN }

/** Reads camera2 metadata locally without opening a camera session. */
class CameraCapabilityInventory(context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun readAll(): List<CameraCapabilities> = cameraManager.cameraIdList.map { cameraId ->
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        mapCapabilities(cameraId, characteristics)
    }

    private fun mapCapabilities(
        cameraId: String,
        characteristics: CameraCharacteristics,
    ): CameraCapabilities {
        val capabilityFlags = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.toSet().orEmpty()
        val zoomRange = readZoomRatioRange(characteristics)
        val streamMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val compensation = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val minimumFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        val maximumDigitalZoom = characteristics
            .get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f

        return CameraCapabilities(
            cameraId = cameraId,
            facing = mapLensFacing(characteristics.get(CameraCharacteristics.LENS_FACING)),
            sensorOrientationDegrees = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0,
            minimumZoomRatio = zoomRange?.lower ?: 1f,
            maximumZoomRatio = zoomRange?.upper ?: maximumDigitalZoom,
            jpegSizes = streamMap?.getOutputSizes(ImageFormat.JPEG)?.sortedByDescending {
                it.width.toLong() * it.height
            }.orEmpty(),
            rawSupported = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW in capabilityFlags,
            manualSensorSupported = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in capabilityFlags,
            manualFocusSupported = minimumFocusDistance > 0f,
            exposureCompensationRange = compensation?.let { it.lower..it.upper },
        )
    }

    private fun readZoomRatioRange(characteristics: CameraCharacteristics): Range<Float>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        } else {
            null
        }

    internal fun mapLensFacing(value: Int?): LensFacing = when (value) {
        CameraCharacteristics.LENS_FACING_FRONT -> LensFacing.FRONT
        CameraCharacteristics.LENS_FACING_BACK -> LensFacing.BACK
        CameraCharacteristics.LENS_FACING_EXTERNAL -> LensFacing.EXTERNAL
        else -> LensFacing.UNKNOWN
    }
}
