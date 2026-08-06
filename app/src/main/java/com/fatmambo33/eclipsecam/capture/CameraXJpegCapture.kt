package com.fatmambo33.eclipsecam.capture

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Callback boundary that keeps CameraX JPEG completion testable without Android hardware. */
interface CameraXJpegCapturePort {
    fun capture(outputFile: File, callback: Callback)

    interface Callback {
        fun onSaved()

        fun onError(errorCode: Int, message: String?)
    }
}

/** Production [CameraXJpegCapturePort] backed by CameraX [ImageCapture]. */
class AndroidCameraXJpegCapturePort(
    private val imageCapture: ImageCapture,
    private val callbackExecutor: Executor,
) : CameraXJpegCapturePort {
    override fun capture(outputFile: File, callback: CameraXJpegCapturePort.Callback) {
        val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        imageCapture.takePicture(
            options,
            callbackExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    callback.onSaved()
                }

                override fun onError(exception: ImageCaptureException) {
                    callback.onError(exception.imageCaptureError, exception.message)
                }
            },
        )
    }
}

/**
 * Awaits one CameraX JPEG write and converts its callback into the capture transaction contract.
 *
 * CameraX does not expose cancellation for an in-flight `takePicture` request. Cancellation therefore
 * stops waiting immediately, ignores any late callback, and lets [CameraCaptureSequenceExecutor]
 * perform non-cancellable backend and reserved-output cleanup.
 */
class CameraXJpegCapture(
    private val port: CameraXJpegCapturePort,
) {
    suspend fun capture(frame: CameraCaptureFrame): CameraFrameCaptureResult =
        suspendCancellableCoroutine { continuation ->
            port.capture(
                frame.output.imageFile,
                object : CameraXJpegCapturePort.Callback {
                    override fun onSaved() {
                        if (continuation.isActive) {
                            continuation.resume(CameraFrameCaptureResult.Captured)
                        }
                    }

                    override fun onError(errorCode: Int, message: String?) {
                        if (continuation.isActive) {
                            continuation.resume(CameraXCaptureFailureAdapter.classify(errorCode, message))
                        }
                    }
                },
            )
        }
}
