package com.fatmambo33.eclipsecam.ar

import org.junit.Assert.assertEquals
import org.junit.Test

class EclipseTrajectoryOverlayTest {
    @Test
    fun createsStableSemanticSnapshotForVisibleContacts() {
        val assessment = FramingAssessment(
            fit = FrameFit.FITS,
            projectedSamples = listOf(
                ProjectedTrajectorySample(
                    id = "C1",
                    result = ProjectionResult.Visible(ScreenPoint(100.9, 80.2, true)),
                ),
                ProjectedTrajectorySample(
                    id = "MAX",
                    result = ProjectionResult.Visible(ScreenPoint(240.0, 120.0, true)),
                ),
                ProjectedTrajectorySample(
                    id = "C4",
                    result = ProjectionResult.Visible(ScreenPoint(390.4, 160.8, false)),
                ),
            ),
            horizontalCorrectionDegrees = 0.0,
            verticalCorrectionDegrees = 0.0,
            rollCorrectionDegrees = 0.0,
            message = "The full eclipse trajectory fits in frame.",
        )

        assertEquals(
            "fit=FITS;message=The full eclipse trajectory fits in frame.;C1=100,80,inside;MAX=240,120,inside;C4=390,160,outside",
            assessment.toOverlayModel().semanticSnapshot,
        )
    }

    @Test
    fun omitsNonVisibleSamplesFromOverlayMarkers() {
        val assessment = FramingAssessment(
            fit = FrameFit.BEHIND_CAMERA,
            projectedSamples = listOf(
                ProjectedTrajectorySample("C1", ProjectionResult.BehindCamera),
                ProjectedTrajectorySample(
                    "MAX",
                    ProjectionResult.Unavailable("Orientation is unavailable."),
                ),
            ),
            horizontalCorrectionDegrees = 30.0,
            verticalCorrectionDegrees = 5.0,
            rollCorrectionDegrees = 0.0,
            message = "Turn the phone toward the eclipse trajectory.",
        )

        assertEquals(emptyList<OverlayMarker>(), assessment.toOverlayModel().markers)
    }
}
