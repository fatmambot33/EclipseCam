package com.fatmambo33.eclipsecam.capture

import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner

/** Validated physical-camera and JPEG-output request. */
data class CameraXOutputBindingRequest(
    val cameraId: String,
    val width: Int,
    val height: Int,
) {
    init {
        require(cameraId.isNotBlank()) { "Camera id must not be blank." }
        require(width > 0 && height > 0) { "Capture dimensions must be positive." }
    }
}

/** Immutable result of binding the camera used by an automatic capture session. */
data class CameraXOutputBinding(
    val camera: Camera,
    val imageCapture: ImageCapture,
)

/**
 * Binds the requested physical camera and JPEG output in one CameraX transaction.
 *
 * The previous use cases are removed before binding so preview and capture never point at different
 * cameras. A failed request leaves no partially bound automatic-capture pipeline.
 */
@OptIn(ExperimentalCamera2Interop::class)
class AndroidCameraXOutputBinder(
    private val provider: ProcessCameraProvider,
    private val lifecycleOwner: LifecycleOwner,
    private val previewSurfaceProvider: Preview.SurfaceProvider? = null,
) {
    fun bind(request: CameraXOutputBindingRequest): CameraXOutputBinding {
        val selector = CameraSelector.Builder()
            .addCameraFilter { cameraInfos ->
                cameraInfos.filter { cameraInfo ->
                    Camera2CameraInfo.from(cameraInfo).cameraId == request.cameraId
                }
            }
            .build()
        check(provider.hasCamera(selector)) {
            "Requested camera is unavailable: ${request.cameraId}"
        }

        val preview = previewSurfaceProvider?.let { surfaceProvider ->
            Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }
        }
        @Suppress("DEPRECATION")
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetResolution(Size(request.width, request.height))
            .build()

        provider.unbindAll()
        val camera = try {
            if (preview == null) {
                provider.bindToLifecycle(lifecycleOwner, selector, imageCapture)
            } else {
                provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            }
        } catch (error: RuntimeException) {
            provider.unbindAll()
            throw error
        }
        return CameraXOutputBinding(camera = camera, imageCapture = imageCapture)
    }

    fun unbind() {
        provider.unbindAll()
    }
}
