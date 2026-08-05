package com.fatmambo33.eclipsecam.ar

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

data class SkyDirection(
    val azimuthDegrees: Double,
    val elevationDegrees: Double,
) {
    init {
        require(azimuthDegrees.isFinite()) { "Azimuth must be finite." }
        require(elevationDegrees.isFinite() && elevationDegrees in -90.0..90.0) {
            "Elevation must be finite and between -90 and 90 degrees."
        }
    }

    val normalizedAzimuthDegrees: Double = normalizeAzimuth(azimuthDegrees)
}

data class CameraView(
    val azimuthDegrees: Double,
    val elevationDegrees: Double,
    val rollDegrees: Double,
    val horizontalFieldOfViewDegrees: Double,
    val verticalFieldOfViewDegrees: Double,
    val viewportWidthPixels: Int,
    val viewportHeightPixels: Int,
    val confidence: ProjectionConfidence,
) {
    init {
        require(azimuthDegrees.isFinite()) { "Azimuth must be finite." }
        require(elevationDegrees.isFinite() && elevationDegrees in -90.0..90.0) {
            "Elevation must be finite and between -90 and 90 degrees."
        }
        require(rollDegrees.isFinite()) { "Roll must be finite." }
        require(horizontalFieldOfViewDegrees.isFinite() && horizontalFieldOfViewDegrees in 1.0..179.0) {
            "Horizontal field of view must be between 1 and 179 degrees."
        }
        require(verticalFieldOfViewDegrees.isFinite() && verticalFieldOfViewDegrees in 1.0..179.0) {
            "Vertical field of view must be between 1 and 179 degrees."
        }
        require(viewportWidthPixels > 0) { "Viewport width must be positive." }
        require(viewportHeightPixels > 0) { "Viewport height must be positive." }
    }
}

enum class ProjectionConfidence { HIGH, MEDIUM, LOW, UNAVAILABLE }

data class ScreenPoint(
    val xPixels: Double,
    val yPixels: Double,
    val insideViewport: Boolean,
)

sealed interface ProjectionResult {
    data class Visible(val point: ScreenPoint) : ProjectionResult
    data object BehindCamera : ProjectionResult
    data class Unavailable(val reason: String) : ProjectionResult
}

data class TrajectorySample(
    val id: String,
    val direction: SkyDirection,
)

data class ProjectedTrajectorySample(
    val id: String,
    val result: ProjectionResult,
)

enum class FrameFit { FITS, CLIPPED, BEHIND_CAMERA, UNAVAILABLE }

data class FramingAssessment(
    val fit: FrameFit,
    val projectedSamples: List<ProjectedTrajectorySample>,
    val horizontalCorrectionDegrees: Double?,
    val verticalCorrectionDegrees: Double?,
    val rollCorrectionDegrees: Double?,
    val message: String,
)

object ProjectionEngine {
    fun project(direction: SkyDirection, camera: CameraView): ProjectionResult {
        if (camera.confidence == ProjectionConfidence.UNAVAILABLE) {
            return ProjectionResult.Unavailable("Orientation is unavailable.")
        }
        if (camera.confidence == ProjectionConfidence.LOW) {
            return ProjectionResult.Unavailable("Orientation confidence is too low for reliable framing.")
        }

        val cameraBasis = cameraBasis(camera)
        val target = skyVector(direction)
        val depth = target dot cameraBasis.forward
        if (depth <= 0.0) return ProjectionResult.BehindCamera

        val horizontalTangent = (target dot cameraBasis.right) / depth
        val verticalTangent = (target dot cameraBasis.up) / depth
        val xNormalized = horizontalTangent / tan(camera.horizontalFieldOfViewDegrees.toRadians() / 2.0)
        val yNormalized = verticalTangent / tan(camera.verticalFieldOfViewDegrees.toRadians() / 2.0)
        val xPixels = (xNormalized + 1.0) * camera.viewportWidthPixels / 2.0
        val yPixels = (1.0 - yNormalized) * camera.viewportHeightPixels / 2.0

        return ProjectionResult.Visible(
            ScreenPoint(
                xPixels = xPixels,
                yPixels = yPixels,
                insideViewport = abs(xNormalized) <= 1.0 && abs(yNormalized) <= 1.0,
            ),
        )
    }

    fun assessTrajectory(
        samples: List<TrajectorySample>,
        camera: CameraView,
        edgeMarginFraction: Double = 0.05,
    ): FramingAssessment {
        require(samples.isNotEmpty()) { "At least one trajectory sample is required." }
        require(edgeMarginFraction.isFinite() && edgeMarginFraction in 0.0..0.45) {
            "Edge margin must be between 0 and 0.45."
        }

        val projected = samples.map { sample ->
            ProjectedTrajectorySample(sample.id, project(sample.direction, camera))
        }
        val unavailable = projected.firstOrNull { it.result is ProjectionResult.Unavailable }
        if (unavailable != null) {
            val reason = (unavailable.result as ProjectionResult.Unavailable).reason
            return FramingAssessment(
                fit = FrameFit.UNAVAILABLE,
                projectedSamples = projected,
                horizontalCorrectionDegrees = null,
                verticalCorrectionDegrees = null,
                rollCorrectionDegrees = null,
                message = reason,
            )
        }
        if (projected.any { it.result is ProjectionResult.BehindCamera }) {
            val correction = correctionTo(samples, camera)
            return FramingAssessment(
                fit = FrameFit.BEHIND_CAMERA,
                projectedSamples = projected,
                horizontalCorrectionDegrees = correction.first,
                verticalCorrectionDegrees = correction.second,
                rollCorrectionDegrees = normalizeSignedDegrees(-camera.rollDegrees),
                message = "Turn the phone toward the eclipse trajectory.",
            )
        }

        val points = projected.map { (it.result as ProjectionResult.Visible).point }
        val marginX = camera.viewportWidthPixels * edgeMarginFraction
        val marginY = camera.viewportHeightPixels * edgeMarginFraction
        val fits = points.all {
            it.xPixels in marginX..(camera.viewportWidthPixels - marginX) &&
                it.yPixels in marginY..(camera.viewportHeightPixels - marginY)
        }
        val correction = correctionTo(samples, camera)
        return FramingAssessment(
            fit = if (fits) FrameFit.FITS else FrameFit.CLIPPED,
            projectedSamples = projected,
            horizontalCorrectionDegrees = correction.first,
            verticalCorrectionDegrees = correction.second,
            rollCorrectionDegrees = normalizeSignedDegrees(-camera.rollDegrees),
            message = if (fits) {
                "The full eclipse trajectory fits in frame."
            } else {
                directionalMessage(correction.first, correction.second)
            },
        )
    }

    private fun correctionTo(
        samples: List<TrajectorySample>,
        camera: CameraView,
    ): Pair<Double, Double> {
        val mean = normalizedMeanDirection(samples.map(TrajectorySample::direction))
        return normalizeSignedDegrees(mean.normalizedAzimuthDegrees - normalizeAzimuth(camera.azimuthDegrees)) to
            (mean.elevationDegrees - camera.elevationDegrees)
    }

    private fun directionalMessage(horizontal: Double, vertical: Double): String {
        val horizontalText = when {
            horizontal > 0.5 -> "move right"
            horizontal < -0.5 -> "move left"
            else -> null
        }
        val verticalText = when {
            vertical > 0.5 -> "tilt up"
            vertical < -0.5 -> "tilt down"
            else -> null
        }
        val instruction = listOfNotNull(horizontalText, verticalText).joinToString(" and ")
        return if (instruction.isEmpty()) {
            "Use a wider lens or rotate the phone to fit the full trajectory."
        } else {
            "${instruction.replaceFirstChar(Char::uppercase)} to centre the eclipse trajectory."
        }
    }
}

private data class Vector3(val east: Double, val north: Double, val up: Double) {
    infix fun dot(other: Vector3): Double = east * other.east + north * other.north + up * other.up
    operator fun times(scale: Double): Vector3 = Vector3(east * scale, north * scale, up * scale)
    operator fun plus(other: Vector3): Vector3 = Vector3(east + other.east, north + other.north, up + other.up)
}

private data class CameraBasis(
    val forward: Vector3,
    val right: Vector3,
    val up: Vector3,
)

private fun cameraBasis(camera: CameraView): CameraBasis {
    val azimuth = normalizeAzimuth(camera.azimuthDegrees).toRadians()
    val elevation = camera.elevationDegrees.toRadians()
    val forward = Vector3(
        east = cos(elevation) * sin(azimuth),
        north = cos(elevation) * cos(azimuth),
        up = sin(elevation),
    )
    val baseRight = Vector3(cos(azimuth), -sin(azimuth), 0.0)
    val baseUp = Vector3(
        east = -sin(elevation) * sin(azimuth),
        north = -sin(elevation) * cos(azimuth),
        up = cos(elevation),
    )
    val roll = camera.rollDegrees.toRadians()
    val right = baseRight * cos(roll) + baseUp * sin(roll)
    val up = baseUp * cos(roll) + baseRight * -sin(roll)
    return CameraBasis(forward, right, up)
}

private fun skyVector(direction: SkyDirection): Vector3 {
    val azimuth = direction.normalizedAzimuthDegrees.toRadians()
    val elevation = direction.elevationDegrees.toRadians()
    return Vector3(
        east = cos(elevation) * sin(azimuth),
        north = cos(elevation) * cos(azimuth),
        up = sin(elevation),
    )
}

private fun normalizedMeanDirection(directions: List<SkyDirection>): SkyDirection {
    val sum = directions.map(::skyVector).reduce(Vector3::plus)
    val horizontal = max(1e-12, kotlin.math.sqrt(sum.east * sum.east + sum.north * sum.north))
    val azimuth = kotlin.math.atan2(sum.east, sum.north) * 180.0 / PI
    val elevation = kotlin.math.atan2(sum.up, horizontal) * 180.0 / PI
    return SkyDirection(azimuth, elevation)
}

internal fun normalizeAzimuth(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0

internal fun normalizeSignedDegrees(degrees: Double): Double {
    val normalized = normalizeAzimuth(degrees)
    return if (normalized > 180.0) normalized - 360.0 else normalized
}

private fun Double.toRadians(): Double = this * PI / 180.0
