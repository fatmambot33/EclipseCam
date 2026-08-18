package com.fatmambo33.eclipsecam.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MontageFailurePresentationTest {
    @Test
    fun mapsKnownDomainFailuresToStablePresentationCategories() {
        assertEquals(
            MontageFailurePresentation.NO_READABLE_PHASE_FRAMES,
            montageFailurePresentation(
                "No readable capture with persisted eclipse-phase metadata is available.",
            ),
        )
        assertEquals(
            MontageFailurePresentation.GENERATED_DIRECTORY_UNAVAILABLE,
            montageFailurePresentation("Unable to create the local generated-media directory."),
        )
        assertEquals(
            MontageFailurePresentation.EMPTY_RENDER_OUTPUT,
            montageFailurePresentation("The montage renderer completed without an image output."),
        )
    }

    @Test
    fun hidesUnexpectedRendererDetailsBehindGenericFailureCategory() {
        assertEquals(
            MontageFailurePresentation.RENDER_FAILED,
            montageFailurePresentation("/data/user/0/app/files/private-path: codec exploded"),
        )
    }
}
