package com.fatmambo33.eclipsecam.capture

import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import com.google.common.util.concurrent.ListenableFuture

/** Camera2 boundary for changing the repeating-request white-balance lock. */
fun interface Camera2WhiteBalanceLockPort {
    fun setLocked(locked: Boolean): ListenableFuture<Void>
}

/** Production [Camera2WhiteBalanceLockPort] backed by CameraX Camera2 interop. */
@ExperimentalCamera2Interop
class Camera2CameraControlWhiteBalanceLockPort(
    cameraControl: CameraControl,
) : Camera2WhiteBalanceLockPort {
    private val camera2Control = Camera2CameraControl.from(cameraControl)

    override fun setLocked(locked: Boolean): ListenableFuture<Void> {
        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, locked)
            .build()
        return camera2Control.addCaptureRequestOptions(options)
    }
}

/**
 * Applies and releases Camera2 automatic-white-balance locking with confirmed completion.
 *
 * The lock is only requested while AWB remains in automatic mode. CameraX completes the returned
 * future after the repeating capture result reflects the submitted options, so capture cannot
 * continue while white-balance state is merely pending.
 */
class Camera2WhiteBalanceLockControl(
    private val port: Camera2WhiteBalanceLockPort,
    private val awaiter: CameraXControlAwaiter = CameraXControlAwaiter(),
) {
    suspend fun lock(): CameraXControlResult = apply(locked = true)

    suspend fun unlock(): CameraXControlResult = apply(locked = false)

    private suspend fun apply(locked: Boolean): CameraXControlResult = awaiter.await(
        operation = if (locked) "Lock automatic white balance" else "Unlock automatic white balance",
        future = port.setLocked(locked),
    )
}
