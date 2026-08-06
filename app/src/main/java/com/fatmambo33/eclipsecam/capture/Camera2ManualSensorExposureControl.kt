package com.fatmambo33.eclipsecam.capture

import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import com.google.common.util.concurrent.ListenableFuture

/** Validated manual sensor exposure request. */
data class Camera2ManualSensorExposureRequest(
    val exposureTimeNanos: Long,
    val sensitivityIso: Int,
) {
    init {
        require(exposureTimeNanos > 0L) { "Exposure time must be positive." }
        require(sensitivityIso > 0) { "ISO sensitivity must be positive." }
    }
}

/** Camera2 boundary for applying manual sensor exposure and restoring auto exposure. */
interface Camera2ManualSensorExposurePort {
    fun apply(request: Camera2ManualSensorExposureRequest): ListenableFuture<Void>

    fun restoreAutoExposure(): ListenableFuture<Void>
}

/** Production [Camera2ManualSensorExposurePort] backed by CameraX Camera2 interop. */
@ExperimentalCamera2Interop
class Camera2CameraControlManualSensorExposurePort(
    cameraControl: CameraControl,
) : Camera2ManualSensorExposurePort {
    private val camera2Control = Camera2CameraControl.from(cameraControl)

    override fun apply(request: Camera2ManualSensorExposureRequest): ListenableFuture<Void> {
        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, request.exposureTimeNanos)
            .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, request.sensitivityIso)
            .build()
        return camera2Control.addCaptureRequestOptions(options)
    }

    override fun restoreAutoExposure(): ListenableFuture<Void> {
        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            .build()
        return camera2Control.addCaptureRequestOptions(options)
    }
}

/**
 * Applies manual shutter time and ISO only after CameraX confirms the repeating request.
 *
 * Unsupported or rejected requests fail closed through [CameraXControlResult]. Cleanup explicitly
 * restores automatic exposure and both operations propagate coroutine cancellation to CameraX.
 */
class Camera2ManualSensorExposureControl(
    private val port: Camera2ManualSensorExposurePort,
    private val awaiter: CameraXControlAwaiter = CameraXControlAwaiter(),
) {
    suspend fun apply(request: Camera2ManualSensorExposureRequest): CameraXControlResult =
        awaiter.await(
            operation = "Apply manual exposure ${request.exposureTimeNanos}ns ISO ${request.sensitivityIso}",
            future = port.apply(request),
        )

    suspend fun restoreAutoExposure(): CameraXControlResult = awaiter.await(
        operation = "Restore automatic exposure",
        future = port.restoreAutoExposure(),
    )
}
