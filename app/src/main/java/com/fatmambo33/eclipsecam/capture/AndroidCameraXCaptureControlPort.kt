package com.fatmambo33.eclipsecam.capture

import android.content.Context
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor

/**
 * Production CameraX control graph for one capture sequence.
 *
 * Binding creates the exact CameraX camera and ImageCapture use case, then wires CameraControl,
 * Camera2 interop controls, and JPEG callbacks from those bound objects. No object is fabricated and
 * no alternate camera is selected when the validated camera is unavailable.
 */
@ExperimentalCamera2Interop
class AndroidCameraXCaptureControlPort(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    private val callbackExecutor: Executor = ContextCompat.getMainExecutor(context),
) : CameraXCaptureControlPort {
    private val binding = ProcessCameraProviderCaptureBindingPort(
        context = context.applicationContext,
        lifecycleOwner = lifecycleOwner,
        callbackExecutor = callbackExecutor,
    )

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var focusMetering: CameraXFocusMeteringControl? = null
    private var manualFocus: Camera2ManualInfinityFocusControl? = null
    private var whiteBalance: Camera2WhiteBalanceLockControl? = null
    private var exposureCompensation: CameraXExposureCompensationControl? = null
    private var jpegCapture: CameraXJpegCapture? = null

    override suspend fun bind(cameraId: String, width: Int, height: Int): CameraXControlResult {
        clearReferences()
        return when (
            val result = binding.bind(CameraXCaptureBindingRequest(cameraId, width, height))
        ) {
            is CameraXCaptureBindingResult.Unavailable ->
                CameraXControlResult.FatalFailure(result.reason)
            is CameraXCaptureBindingResult.Ready -> {
                val bound = result.binding
                val cameraControl = bound.camera.cameraControl
                camera = bound.camera
                imageCapture = bound.imageCapture
                focusMetering = CameraXFocusMeteringControl(
                    CameraXCameraControlFocusMeteringPort(cameraControl),
                )
                manualFocus = Camera2ManualInfinityFocusControl(
                    Camera2CameraControlManualInfinityFocusPort(cameraControl),
                )
                whiteBalance = Camera2WhiteBalanceLockControl(
                    Camera2CameraControlWhiteBalanceLockPort(cameraControl),
                )
                exposureCompensation = CameraXExposureCompensationControl(
                    CameraXCameraControlExposureCompensationPort(cameraControl),
                )
                jpegCapture = CameraXJpegCapture(
                    AndroidCameraXJpegCapturePort(bound.imageCapture, callbackExecutor),
                )
                CameraXControlResult.Applied
            }
        }
    }

    override suspend fun setContinuousAutoFocus(): CameraXControlResult =
        required("CameraX focus control") { it.start() }

    override suspend fun setManualInfinityFocus(): CameraXControlResult =
        requiredManualFocus { it.apply() }

    override suspend fun meterAndLockWhiteBalance(): CameraXControlResult {
        val metered = required("CameraX focus/metering control") { it.start() }
        if (metered != CameraXControlResult.Applied) return metered
        return requiredWhiteBalance { it.lock() }
    }

    override suspend fun setExposureCompensation(steps: Int): CameraXControlResult =
        requiredExposure { it.apply(steps) }

    override suspend fun setRelativeManualExposure(offsetEv: Int): CameraXControlResult =
        requiredExposure { it.apply(offsetEv) }

    override suspend fun captureJpeg(frame: CameraCaptureFrame): CameraFrameCaptureResult =
        jpegCapture?.capture(frame)
            ?: CameraFrameCaptureResult.FatalFailure("Bound CameraX ImageCapture is unavailable.")

    override suspend fun restore(): CameraXControlResult {
        val results = listOf(
            whiteBalance?.unlock(),
            exposureCompensation?.apply(0),
            manualFocus?.restoreContinuousAutoFocus(),
            focusMetering?.restoreContinuousAutoFocus(),
        )
        binding.unbind()
        clearReferences()
        return results.filterNotNull().firstOrNull { it != CameraXControlResult.Applied }
            ?: CameraXControlResult.Applied
    }

    private suspend fun required(
        name: String,
        operation: suspend (CameraXFocusMeteringControl) -> CameraXControlResult,
    ): CameraXControlResult = focusMetering?.let { operation(it) }
        ?: CameraXControlResult.FatalFailure("$name is not bound.")

    private suspend fun requiredManualFocus(
        operation: suspend (Camera2ManualInfinityFocusControl) -> CameraXControlResult,
    ): CameraXControlResult = manualFocus?.let { operation(it) }
        ?: CameraXControlResult.FatalFailure("Camera2 manual focus control is not bound.")

    private suspend fun requiredWhiteBalance(
        operation: suspend (Camera2WhiteBalanceLockControl) -> CameraXControlResult,
    ): CameraXControlResult = whiteBalance?.let { operation(it) }
        ?: CameraXControlResult.FatalFailure("Camera2 white-balance control is not bound.")

    private suspend fun requiredExposure(
        operation: suspend (CameraXExposureCompensationControl) -> CameraXControlResult,
    ): CameraXControlResult = exposureCompensation?.let { operation(it) }
        ?: CameraXControlResult.FatalFailure("CameraX exposure control is not bound.")

    private fun clearReferences() {
        camera = null
        imageCapture = null
        focusMetering = null
        manualFocus = null
        whiteBalance = null
        exposureCompensation = null
        jpegCapture = null
    }
}
