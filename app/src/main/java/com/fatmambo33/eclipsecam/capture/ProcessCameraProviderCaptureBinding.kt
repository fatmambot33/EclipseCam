package com.fatmambo33.eclipsecam.capture

import android.content.Context
import android.os.Looper
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Validated request for binding one exact camera to the capture-only ImageCapture use case. */
data class CameraXCaptureBindingRequest(
    val cameraId: String,
    val width: Int,
    val height: Int,
) {
    init {
        require(cameraId.isNotBlank()) { "Camera id must not be blank." }
        require(width > 0) { "Capture width must be positive." }
        require(height > 0) { "Capture height must be positive." }
    }
}

/** CameraX objects owned by one successful capture binding. */
data class BoundCameraXCapture(
    val camera: Camera,
    val imageCapture: ImageCapture,
)

/** Fail-closed result from exact-camera CameraX binding. */
sealed interface CameraXCaptureBindingResult {
    data class Ready(val binding: BoundCameraXCapture) : CameraXCaptureBindingResult
    data class Unavailable(val reason: String) : CameraXCaptureBindingResult
}

/** Testable capture-use-case binding boundary. */
interface CameraXCaptureBindingPort {
    suspend fun bind(request: CameraXCaptureBindingRequest): CameraXCaptureBindingResult

    fun unbind()
}

/** Exact-id matching policy shared by the production CameraSelector and JVM tests. */
object ExactCameraIdMatcher {
    fun matches(requestedCameraId: String, candidateCameraId: String): Boolean =
        requestedCameraId.isNotBlank() && requestedCameraId == candidateCameraId
}

/**
 * Production CameraX capture binding backed by ProcessCameraProvider.
 *
 * The selected capability id is treated as authoritative: this adapter never falls back to another
 * lens. CameraX lifecycle binding and unbinding are always executed on Android's main thread even
 * when the foreground capture worker invokes this port from a background thread.
 */
@ExperimentalCamera2Interop
class ProcessCameraProviderCaptureBindingPort(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val callbackExecutor: Executor = ContextCompat.getMainExecutor(context),
) : CameraXCaptureBindingPort {
    private val applicationContext = context.applicationContext
    private var provider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    override suspend fun bind(
        request: CameraXCaptureBindingRequest,
    ): CameraXCaptureBindingResult = withContext(Dispatchers.Main.immediate) {
        runCatching {
            val cameraProvider = awaitProvider()
            val selector = exactCameraSelector(request.cameraId)
            if (!cameraProvider.hasCamera(selector)) {
                return@withContext CameraXCaptureBindingResult.Unavailable(
                    "Validated camera ${request.cameraId} is unavailable to CameraX.",
                )
            }

            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetResolution(Size(request.width, request.height))
                .build()

            imageCapture?.let { previousCapture ->
                cameraProvider.unbind(previousCapture)
            }
            val camera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, capture)
            provider = cameraProvider
            imageCapture = capture
            CameraXCaptureBindingResult.Ready(BoundCameraXCapture(camera, capture))
        }.getOrElse { error ->
            CameraXCaptureBindingResult.Unavailable(
                error.message ?: "CameraX capture binding failed.",
            )
        }
    }

    override fun unbind() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            unbindOnMainThread()
        } else {
            runBlocking(Dispatchers.Main.immediate) {
                unbindOnMainThread()
            }
        }
    }

    private fun unbindOnMainThread() {
        val capture = imageCapture
        if (capture != null) {
            runCatching { provider?.unbind(capture) }
        }
        imageCapture = null
        provider = null
    }

    private suspend fun awaitProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(applicationContext)
            future.addListener(
                {
                    if (continuation.isActive) {
                        runCatching(future::get)
                            .onSuccess(continuation::resume)
                            .onFailure { continuation.cancel(it) }
                    }
                },
                callbackExecutor,
            )
            continuation.invokeOnCancellation { future.cancel(true) }
        }

    private fun exactCameraSelector(cameraId: String): CameraSelector =
        CameraSelector.Builder()
            .addCameraFilter { cameraInfos ->
                cameraInfos.filter { cameraInfo -> cameraInfo.matchesCameraId(cameraId) }
            }
            .build()

    private fun CameraInfo.matchesCameraId(cameraId: String): Boolean =
        ExactCameraIdMatcher.matches(
            requestedCameraId = cameraId,
            candidateCameraId = Camera2CameraInfo.from(this).cameraId,
        )
}
