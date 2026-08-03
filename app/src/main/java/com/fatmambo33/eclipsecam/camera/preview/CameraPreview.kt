package com.fatmambo33.eclipsecam.camera.preview

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

/** Result of attempting to bind a camera preview. */
sealed interface CameraPreviewState {
    data object Idle : CameraPreviewState
    data object Binding : CameraPreviewState
    data class Ready(val lensFacing: Int) : CameraPreviewState
    data class Error(val message: String) : CameraPreviewState
}

/**
 * Lifecycle-aware CameraX preview surface.
 *
 * Permission ownership stays with the caller. This composable never requests
 * permission, uploads media, or starts capture work.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    onStateChanged: (CameraPreviewState) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)

    DisposableEffect(lifecycleOwner, lensFacing) {
        onStateChanged(CameraPreviewState.Binding)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        providerFuture.addListener(
            {
                runCatching {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val selector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview)
                    onStateChanged(CameraPreviewState.Ready(lensFacing))
                }.onFailure {
                    onStateChanged(
                        CameraPreviewState.Error(it.message ?: "Unable to bind camera preview"),
                    )
                }
            },
            executor,
        )

        onDispose {
            if (providerFuture.isDone) {
                runCatching { providerFuture.get().unbindAll() }
            }
        }
    }
}

internal fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
