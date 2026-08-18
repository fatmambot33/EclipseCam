package com.fatmambo33.eclipsecam.media

/** Stable user-facing categories for local montage failures.
 *
 * Renderer/IO exception messages are diagnostic implementation details and must never be rendered
 * directly: they are not localizable and may expose device-specific paths or codec details.
 */
enum class MontageFailurePresentation {
    NO_READABLE_PHASE_FRAMES,
    GENERATED_DIRECTORY_UNAVAILABLE,
    EMPTY_RENDER_OUTPUT,
    RENDER_FAILED,
}

fun montageFailurePresentation(reason: String): MontageFailurePresentation = when (reason) {
    "No readable capture with persisted eclipse-phase metadata is available." ->
        MontageFailurePresentation.NO_READABLE_PHASE_FRAMES
    "Unable to create the local generated-media directory." ->
        MontageFailurePresentation.GENERATED_DIRECTORY_UNAVAILABLE
    "The montage renderer completed without an image output." ->
        MontageFailurePresentation.EMPTY_RENDER_OUTPUT
    else -> MontageFailurePresentation.RENDER_FAILED
}
