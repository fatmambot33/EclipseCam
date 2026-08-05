package com.fatmambo33.eclipsecam.camera.preview

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

internal sealed interface CameraPreviewEvent {
    data object PermissionMissing : CameraPreviewEvent

    data object StartRequested : CameraPreviewEvent

    data class Started(
        val lens: PreviewLens,
    ) : CameraPreviewEvent

    data class Failed(
        val reason: String?,
    ) : CameraPreviewEvent
}

internal fun reduceCameraPreviewState(event: CameraPreviewEvent): CameraPreviewState = when (event) {
    CameraPreviewEvent.PermissionMissing -> CameraPreviewState.WaitingForPermission
    CameraPreviewEvent.StartRequested -> CameraPreviewState.Starting
    is CameraPreviewEvent.Started -> CameraPreviewState.Streaming(event.lens)
    is CameraPreviewEvent.Failed -> CameraPreviewState.Unavailable(
        reason = event.reason?.takeIf(String::isNotBlank) ?: "Camera preview is unavailable on this device.",
    )
}
