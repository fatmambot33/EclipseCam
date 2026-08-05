package com.fatmambo33.eclipsecam.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ProjectionEngineTest {
    @Test
    fun projectsCameraDirectionToViewportCentre() {
        val result = ProjectionEngine.project(
            direction = SkyDirection(180.0, 30.0),
            camera = camera(azimuth = 180.0, elevation = 30.0),
        ) as ProjectionResult.Visible

        assertEquals(500.0, result.point.xPixels, 1e-6)
        assertEquals(250.0, result.point.yPixels, 1e-6)
        assertTrue(result.point.insideViewport)
    }

    @Test
    fun projectsHorizontalFieldOfViewBoundaryToViewportEdge() {
        val result = ProjectionEngine.project(
            direction = SkyDirection(225.0, 0.0),
            camera = camera(
                azimuth = 180.0,
                elevation = 0.0,
                horizontalFov = 90.0,
                verticalFov = 60.0,
            ),
        ) as ProjectionResult.Visible

        assertEquals(1000.0, result.point.xPixels, 1e-6)
        assertEquals(250.0, result.point.yPixels, 1e-6)
        assertTrue(result.point.insideViewport)
    }

    @Test
    fun reportsTargetBehindCamera() {
        val result = ProjectionEngine.project(
            direction = SkyDirection(0.0, 0.0),
            camera = camera(azimuth = 180.0, elevation = 0.0),
        )

        assertEquals(ProjectionResult.BehindCamera, result)
    }

    @Test
    fun appliesPhoneRollToScreenCoordinates() {
        val unrolled = ProjectionEngine.project(
            direction = SkyDirection(190.0, 20.0),
            camera = camera(azimuth = 180.0, elevation = 20.0, roll = 0.0),
        ) as ProjectionResult.Visible
        val rolled = ProjectionEngine.project(
            direction = SkyDirection(190.0, 20.0),
            camera = camera(azimuth = 180.0, elevation = 20.0, roll = 90.0),
        ) as ProjectionResult.Visible

        assertTrue(unrolled.point.xPixels > 500.0)
        assertEquals(250.0, unrolled.point.yPixels, 0.5)
        assertEquals(500.0, rolled.point.xPixels, 0.5)
        assertTrue(rolled.point.yPixels > 250.0)
    }

    @Test
    fun supportsPortraitViewportAndSelectedLensFieldOfView() {
        val widePortrait = ProjectionEngine.project(
            direction = SkyDirection(205.0, 25.0),
            camera = camera(
                azimuth = 180.0,
                elevation = 25.0,
                width = 500,
                height = 1000,
                horizontalFov = 80.0,
            ),
        ) as ProjectionResult.Visible
        val narrowPortrait = ProjectionEngine.project(
            direction = SkyDirection(205.0, 25.0),
            camera = camera(
                azimuth = 180.0,
                elevation = 25.0,
                width = 500,
                height = 1000,
                horizontalFov = 30.0,
            ),
        ) as ProjectionResult.Visible

        assertTrue(widePortrait.point.insideViewport)
        assertTrue(!narrowPortrait.point.insideViewport)
    }

    @Test
    fun reportsFullTrajectoryFitWithMargin() {
        val assessment = ProjectionEngine.assessTrajectory(
            samples = listOf(
                TrajectorySample("C1", SkyDirection(175.0, 28.0)),
                TrajectorySample("MAX", SkyDirection(180.0, 30.0)),
                TrajectorySample("C4", SkyDirection(185.0, 32.0)),
            ),
            camera = camera(azimuth = 180.0, elevation = 30.0),
        )

        assertEquals(FrameFit.FITS, assessment.fit)
        assertEquals(3, assessment.projectedSamples.size)
        assertTrue(abs(assessment.horizontalCorrectionDegrees ?: 99.0) < 0.1)
        assertTrue(abs(assessment.verticalCorrectionDegrees ?: 99.0) < 0.1)
        assertEquals("The full eclipse trajectory fits in frame.", assessment.message)
    }

    @Test
    fun reportsClippedTrajectoryAndDirectionalGuidance() {
        val assessment = ProjectionEngine.assessTrajectory(
            samples = listOf(
                TrajectorySample("C1", SkyDirection(205.0, 35.0)),
                TrajectorySample("MAX", SkyDirection(210.0, 38.0)),
                TrajectorySample("C4", SkyDirection(215.0, 40.0)),
            ),
            camera = camera(
                azimuth = 180.0,
                elevation = 25.0,
                horizontalFov = 40.0,
                verticalFov = 30.0,
            ),
        )

        assertEquals(FrameFit.CLIPPED, assessment.fit)
        assertTrue((assessment.horizontalCorrectionDegrees ?: 0.0) > 20.0)
        assertTrue((assessment.verticalCorrectionDegrees ?: 0.0) > 10.0)
        assertTrue(assessment.message.contains("Move right"))
        assertTrue(assessment.message.contains("tilt up"))
    }

    @Test
    fun rejectsLowAndUnavailableOrientationConfidence() {
        val sample = SkyDirection(180.0, 30.0)

        val low = ProjectionEngine.project(sample, camera(confidence = ProjectionConfidence.LOW))
        val unavailable = ProjectionEngine.project(sample, camera(confidence = ProjectionConfidence.UNAVAILABLE))

        assertTrue(low is ProjectionResult.Unavailable)
        assertTrue(unavailable is ProjectionResult.Unavailable)
    }

    @Test
    fun trajectoryAssessmentDoesNotOfferFalseCorrectionsWhenUnavailable() {
        val assessment = ProjectionEngine.assessTrajectory(
            samples = listOf(TrajectorySample("MAX", SkyDirection(180.0, 30.0))),
            camera = camera(confidence = ProjectionConfidence.LOW),
        )

        assertEquals(FrameFit.UNAVAILABLE, assessment.fit)
        assertNull(assessment.horizontalCorrectionDegrees)
        assertNull(assessment.verticalCorrectionDegrees)
        assertNull(assessment.rollCorrectionDegrees)
    }

    @Test
    fun normalizesNorthCrossingGuidanceToShortestTurn() {
        val assessment = ProjectionEngine.assessTrajectory(
            samples = listOf(TrajectorySample("MAX", SkyDirection(2.0, 20.0))),
            camera = camera(azimuth = 358.0, elevation = 20.0),
        )

        assertEquals(4.0, assessment.horizontalCorrectionDegrees ?: 0.0, 1e-6)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidFieldOfView() {
        camera(horizontalFov = 180.0)
    }

    private fun camera(
        azimuth: Double = 180.0,
        elevation: Double = 30.0,
        roll: Double = 0.0,
        horizontalFov: Double = 60.0,
        verticalFov: Double = 45.0,
        width: Int = 1000,
        height: Int = 500,
        confidence: ProjectionConfidence = ProjectionConfidence.HIGH,
    ) = CameraView(
        azimuthDegrees = azimuth,
        elevationDegrees = elevation,
        rollDegrees = roll,
        horizontalFieldOfViewDegrees = horizontalFov,
        verticalFieldOfViewDegrees = verticalFov,
        viewportWidthPixels = width,
        viewportHeightPixels = height,
        confidence = confidence,
    )
}
