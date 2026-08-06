package com.fatmambo33.eclipsecam.capture

import androidx.camera.core.CameraControl
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.FocusMeteringResult
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import com.google.common.util.concurrent.ListenableFuture

/** Normalized focus and metering point for an automatic capture sequence. */
data class CameraXFocusMeteringRequest(
    val normalizedX: Float = 0.5f,
    val normalizedY: Float = 0.5f,
) {
    init {
        require(normalizedX in 0f..1f) { "Focus X must be normalized to 0..1." }
        require(normalizedY in 0f..1f) { "Focus Y must be normalized to 0..1." }
    }
}

/** CameraX boundary for starting and cancelling focus/metering operations. */
interface CameraXFocusMeteringPort {
    fun start(request: CameraXFocusMeteringRequest): ListenableFuture<FocusMeteringResult>

    fun cancel(): ListenableFuture<Void>
}

/** Production focus/metering port backed by CameraX [CameraControl]. */
class CameraXCameraControlFocusMeteringPort(
    private val cameraControl: CameraControl,
) : CameraXFocusMeteringPort {
    override fun start(
        request: CameraXFocusMeteringRequest,
    ): ListenableFuture<FocusMeteringResult> {
        val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
        val point = factory.createPoint(request.normalizedX, request.normalizedY)
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or
                FocusMeteringAction.FLAG_AE or
                FocusMeteringAction.FLAG_AWB,
        ).disableAutoCancel().build()
        return cameraControl.startFocusAndMetering(action)
    }

    override fun cancel(): ListenableFuture<Void> = cameraControl.cancelFocusAndMetering()
}

/**
 * Applies centre-weighted focus, exposure, and white-balance metering and awaits CameraX.
 *
 * Cancelling metering restores the camera's continuous automatic behavior. Both operations use the
 * shared fail-closed CameraX result contract and propagate coroutine cancellation to CameraX.
 */
class CameraXFocusMeteringControl(
    private val port: CameraXFocusMeteringPort,
    private val awaiter: CameraXControlAwaiter = CameraXControlAwaiter(),
) {
    suspend fun start(
        request: CameraXFocusMeteringRequest = CameraXFocusMeteringRequest(),
    ): CameraXControlResult = awaiter.await(
        operation = "Start focus and metering at ${request.normalizedX},${request.normalizedY}",
        future = port.start(request),
    )

    suspend fun restoreContinuousAutoFocus(): CameraXControlResult = awaiter.await(
        operation = "Restore continuous autofocus",
        future = port.cancel(),
    )
}
