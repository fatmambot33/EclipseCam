package com.fatmambo33.eclipsecam.ar

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayModelContractTest {
    @Test
    fun unavailableAssessmentKeepsGuidanceAndNoMarkers() {
        val assessment = FramingAssessment(
            fit = FrameFit.UNAVAILABLE,
            projectedSamples = emptyList(),
            horizontalCorrectionDegrees = null,
            verticalCorrectionDegrees = null,
            rollCorrectionDegrees = null,
            message = "Orientation confidence is too low for reliable framing.",
        )

        assertEquals(
            "fit=UNAVAILABLE;message=Orientation confidence is too low for reliable framing.",
            assessment.toOverlayModel().semanticSnapshot,
        )
    }
}
