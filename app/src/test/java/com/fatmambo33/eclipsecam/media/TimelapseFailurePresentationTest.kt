package com.fatmambo33.eclipsecam.media

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelapseFailurePresentationTest {
    @Test
    fun `known timelapse failures map to stable categories`() {
        assertEquals(
            TimelapseFailurePresentation.NO_READABLE_FRAMES,
            timelapseFailurePresentation("No readable original JPEG captures are available."),
        )
        assertEquals(
            TimelapseFailurePresentation.GENERATED_DIRECTORY_UNAVAILABLE,
            timelapseFailurePresentation("Unable to create the local generated-media directory."),
        )
        assertEquals(
            TimelapseFailurePresentation.EMPTY_VIDEO_OUTPUT,
            timelapseFailurePresentation("The video encoder completed without a playable output file."),
        )
    }

    @Test
    fun `unexpected diagnostics collapse to generic presentation`() {
        assertEquals(
            TimelapseFailurePresentation.RENDER_FAILED,
            timelapseFailurePresentation("Codec failed at /data/user/0/private/session.mp4"),
        )
    }
}
