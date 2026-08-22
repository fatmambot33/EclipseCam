package com.fatmambo33.eclipsecam.camera.preview

import androidx.annotation.StringRes
import com.fatmambo33.eclipsecam.R

/** User-visible lifecycle state for the CameraX preview surface. */
sealed interface CameraPreviewState {
    data object WaitingForPermission : CameraPreviewState

    data object Starting : CameraPreviewState

    data class Streaming(
        val lens: PreviewLens,
    ) : CameraPreviewState

    data class Unavailable(
        val reason: String,
    ) : CameraPreviewState
}

enum class PreviewLens {
    BACK,
    FRONT,
}

internal enum class CameraPreviewFailure {
    NO_USABLE_CAMERA,
    START_FAILED,
}

@StringRes
internal fun cameraPreviewFailureMessageRes(failure: CameraPreviewFailure): Int = when (failure) {
    CameraPreviewFailure.NO_USABLE_CAMERA -> R.string.camera_preview_failure_no_camera
    CameraPreviewFailure.START_FAILED -> R.string.camera_preview_failure_start
}

internal sealed interface CameraPreviewEvent {
    data object PermissionMissing : CameraPreviewEvent

    data object StartRequested : CameraPreviewEvent

    data class Started(
        val lens: PreviewLens,
    ) : CameraPreviewEvent
}

internal fun reduceCameraPreviewState(event: CameraPreviewEvent): CameraPreviewState = when (event) {
    CameraPreviewEvent.PermissionMissing -> CameraPreviewState.WaitingForPermission
    CameraPreviewEvent.StartRequested -> CameraPreviewState.Starting
    is CameraPreviewEvent.Started -> CameraPreviewState.Streaming(event.lens)
}
