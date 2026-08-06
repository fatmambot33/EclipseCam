package com.fatmambo33.eclipsecam.capture

import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import com.google.common.util.concurrent.ListenableFuture

/** Camera2 boundary for applying and restoring manual infinity focus. */
fun interface Camera2ManualInfinityFocusPort {
    fun setManualInfinity(enabled: Boolean): ListenableFuture<Void>
}

/** Production [Camera2ManualInfinityFocusPort] backed by CameraX Camera2 interop. */
@ExperimentalCamera2Interop
class Camera2CameraControlManualInfinityFocusPort(
    cameraControl: CameraControl,
) : Camera2ManualInfinityFocusPort {
    private val camera2Control = Camera2CameraControl.from(cameraControl)

    override fun setManualInfinity(enabled: Boolean): ListenableFuture<Void> {
        val options = CaptureRequestOptions.Builder().apply {
            if (enabled) {
                setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, INFINITY_DIOPTERS)
            } else {
                setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                )
            }
        }.build()
        return camera2Control.addCaptureRequestOptions(options)
    }

    private companion object {
        const val INFINITY_DIOPTERS = 0f
    }
}

/**
 * Applies and restores infinity focus only after CameraX confirms the repeating request.
 *
 * Camera2 defines infinity as zero diopters. Enabling this control disables autofocus before
 * setting the lens distance. Restoring the control returns the camera to continuous-picture AF.
 */
class Camera2ManualInfinityFocusControl(
    private val port: Camera2ManualInfinityFocusPort,
    private val awaiter: CameraXControlAwaiter = CameraXControlAwaiter(),
) {
    suspend fun apply(): CameraXControlResult = awaiter.await(
        operation = "Apply manual infinity focus",
        future = port.setManualInfinity(enabled = true),
    )

    suspend fun restoreContinuousAutoFocus(): CameraXControlResult = awaiter.await(
        operation = "Restore continuous autofocus after manual infinity focus",
        future = port.setManualInfinity(enabled = false),
    )
}
