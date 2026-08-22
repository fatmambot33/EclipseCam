package com.fatmambo33.eclipsecam.camera.preview

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fatmambo33.eclipsecam.ar.EclipseTrajectoryOverlay
import com.fatmambo33.eclipsecam.ar.FramingAssessment

/**
 * Lifecycle-bound CameraX preview with a non-interactive eclipse trajectory overlay.
 *
 * The back camera is preferred. Devices without a back camera fall back to the
 * front camera and report the selected lens through [onStateChanged]. Media is
 * never captured or uploaded by this surface.
 */
@Composable
fun CameraPreviewSurface(
    permissionGranted: Boolean,
    modifier: Modifier = Modifier,
    framingAssessment: FramingAssessment? = null,
    onStateChanged: (CameraPreviewState) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnStateChanged = rememberUpdatedState(onStateChanged)
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
        EclipseTrajectoryOverlay(
            assessment = framingAssessment,
            modifier = Modifier.fillMaxSize(),
        )
    }

    DisposableEffect(permissionGranted, context, lifecycleOwner, previewView) {
        if (!permissionGranted) {
            currentOnStateChanged.value(reduceCameraPreviewState(CameraPreviewEvent.PermissionMissing))
            return@DisposableEffect onDispose { }
        }

        currentOnStateChanged.value(reduceCameraPreviewState(CameraPreviewEvent.StartRequested))
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var disposed = false
        providerFuture.addListener(
            {
                if (disposed) return@addListener

                val provider = runCatching { providerFuture.get() }.getOrElse {
                    currentOnStateChanged.value(
                        unavailablePreviewState(context, CameraPreviewFailure.START_FAILED),
                    )
                    return@addListener
                }
                val selection = selectAvailableLens(provider)
                if (selection == null) {
                    currentOnStateChanged.value(
                        unavailablePreviewState(context, CameraPreviewFailure.NO_USABLE_CAMERA),
                    )
                    return@addListener
                }

                runCatching {
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selection.selector, preview)
                    selection.lens
                }.onSuccess { lens ->
                    if (!disposed) {
                        currentOnStateChanged.value(
                            reduceCameraPreviewState(CameraPreviewEvent.Started(lens)),
                        )
                    }
                }.onFailure {
                    if (!disposed) {
                        currentOnStateChanged.value(
                            unavailablePreviewState(context, CameraPreviewFailure.START_FAILED),
                        )
                    }
                }
            },
            ContextCompat.getMainExecutor(context),
        )

        onDispose {
            disposed = true
            runCatching {
                if (providerFuture.isDone) providerFuture.get().unbindAll()
            }
        }
    }
}

private fun unavailablePreviewState(
    context: Context,
    failure: CameraPreviewFailure,
): CameraPreviewState.Unavailable = CameraPreviewState.Unavailable(
    context.getString(cameraPreviewFailureMessageRes(failure)),
)

private data class LensSelection(
    val selector: CameraSelector,
    val lens: PreviewLens,
)

private fun selectAvailableLens(provider: ProcessCameraProvider): LensSelection? {
    val candidates = listOf(
        LensSelection(CameraSelector.DEFAULT_BACK_CAMERA, PreviewLens.BACK),
        LensSelection(CameraSelector.DEFAULT_FRONT_CAMERA, PreviewLens.FRONT),
    )
    return candidates.firstOrNull { candidate ->
        runCatching { provider.hasCamera(candidate.selector) }.getOrDefault(false)
    }
}
