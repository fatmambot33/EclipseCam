package com.fatmambo33.eclipsecam.media

/** Stable user-facing categories for local timelapse failures.
 *
 * Encoder/IO exception messages are diagnostic implementation details and must never be rendered
 * directly: they are not localizable and may expose device-specific paths or codec details.
 */
enum class TimelapseFailurePresentation {
    NO_READABLE_FRAMES,
    GENERATED_DIRECTORY_UNAVAILABLE,
    EMPTY_VIDEO_OUTPUT,
    RENDER_FAILED,
}

fun timelapseFailurePresentation(reason: String): TimelapseFailurePresentation = when (reason) {
    "No readable original JPEG captures are available." ->
        TimelapseFailurePresentation.NO_READABLE_FRAMES
    "Unable to create the local generated-media directory." ->
        TimelapseFailurePresentation.GENERATED_DIRECTORY_UNAVAILABLE
    "The video encoder completed without a playable output file." ->
        TimelapseFailurePresentation.EMPTY_VIDEO_OUTPUT
    else -> TimelapseFailurePresentation.RENDER_FAILED
}
