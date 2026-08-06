package com.fatmambo33.eclipsecam.capture

import androidx.camera.core.CameraControl
import com.google.common.util.concurrent.ListenableFuture

/** CameraX boundary for applying exposure-compensation indexes. */
fun interface CameraXExposureCompensationPort {
    fun setExposureCompensationIndex(index: Int): ListenableFuture<Int>
}

/** Production [CameraXExposureCompensationPort] backed by CameraX [CameraControl]. */
class CameraXCameraControlExposureCompensationPort(
    private val cameraControl: CameraControl,
) : CameraXExposureCompensationPort {
    override fun setExposureCompensationIndex(index: Int): ListenableFuture<Int> =
        cameraControl.setExposureCompensationIndex(index)
}

/**
 * Applies one exposure-compensation step and waits until CameraX confirms the new index.
 *
 * The adapter deliberately returns the shared control result contract so a rejected or cancelled
 * operation cannot be mistaken for an applied exposure state by the capture sequence.
 */
class CameraXExposureCompensationControl(
    private val port: CameraXExposureCompensationPort,
    private val awaiter: CameraXControlAwaiter = CameraXControlAwaiter(),
) {
    suspend fun apply(steps: Int): CameraXControlResult = awaiter.await(
        operation = "Set exposure compensation to $steps",
        future = port.setExposureCompensationIndex(steps),
    )
}
