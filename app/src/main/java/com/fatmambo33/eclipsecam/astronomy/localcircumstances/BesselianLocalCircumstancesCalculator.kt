package com.fatmambo33.eclipsecam.astronomy.localcircumstances

import com.fatmambo33.eclipsecam.astronomy.BesselianElements
import com.fatmambo33.eclipsecam.astronomy.Eclipse2026Aug12
import com.fatmambo33.eclipsecam.astronomy.EvaluatedBesselianElements
import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Offline local-circumstances solver based on Besselian elements.
 *
 * The observer is projected onto the fundamental plane for each trial instant.
 * Contact instants are found as roots of the apparent limb-separation equations,
 * while maximum eclipse is found by minimizing the shadow-axis separation.
 */
class BesselianLocalCircumstancesCalculator(
    private val elements: BesselianElements = Eclipse2026Aug12.elements,
) : LocalCircumstancesCalculator {

    override fun calculate(observer: Observer): LocalEclipseCircumstances {
        val start = validWindowStartUtc()
        val end = validWindowEndUtc()
        val externalRoots = findRoots(start, end, observer) { geometry ->
            geometry.separation - geometry.penumbralRadius
        }

        if (externalRoots.size < 2) {
            return noEclipse(observer)
        }

        val c1 = externalRoots.first()
        val c4 = externalRoots.last()
        val maximum = minimize(c1, c4, observer)
        val maximumGeometry = geometry(maximum, observer)
        val internalRoots = findRoots(c1, c4, observer) { geometry ->
            geometry.separation - abs(geometry.umbralRadius)
        }
        val isTotal = maximumGeometry.umbralRadius < 0.0 && internalRoots.size >= 2

        val contacts = linkedMapOf<EclipseContact, ContactCircumstance>()
        contacts[EclipseContact.C1] = circumstance(EclipseContact.C1, c1, observer)
        if (isTotal) {
            contacts[EclipseContact.C2] = circumstance(EclipseContact.C2, internalRoots.first(), observer)
        }
        contacts[EclipseContact.MAXIMUM] = circumstance(EclipseContact.MAXIMUM, maximum, observer)
        if (isTotal) {
            contacts[EclipseContact.C3] = circumstance(EclipseContact.C3, internalRoots.last(), observer)
        }
        contacts[EclipseContact.C4] = circumstance(EclipseContact.C4, c4, observer)

        val solarRadius = (maximumGeometry.penumbralRadius + maximumGeometry.umbralRadius) / 2.0
        val lunarRadius = (maximumGeometry.penumbralRadius - maximumGeometry.umbralRadius) / 2.0
        val magnitude = ((lunarRadius + solarRadius - maximumGeometry.separation) / (2.0 * solarRadius))
            .coerceAtLeast(0.0)
        val obscuration = overlapFraction(
            solarRadius = solarRadius,
            lunarRadius = lunarRadius,
            separation = maximumGeometry.separation,
        )
        val totalityDuration = if (isTotal) {
            Duration.between(internalRoots.first(), internalRoots.last()).toMillis() / 1_000.0
        } else {
            null
        }

        return LocalEclipseCircumstances(
            observer = observer,
            visibility = if (isTotal) EclipseVisibility.TOTAL else EclipseVisibility.PARTIAL,
            contacts = contacts,
            magnitude = magnitude,
            obscuration = obscuration,
            totalityDurationSeconds = totalityDuration,
            uncertainty = ModelUncertainty(
                timingSeconds = 2.0,
                pathKilometers = 3.0,
                notes = "Polynomial Besselian model; path-edge uncertainty includes lunar-limb relief.",
            ),
            modelValid = true,
        )
    }

    private fun noEclipse(observer: Observer) = LocalEclipseCircumstances(
        observer = observer,
        visibility = EclipseVisibility.NONE,
        contacts = emptyMap(),
        magnitude = 0.0,
        obscuration = 0.0,
        totalityDurationSeconds = null,
        uncertainty = ModelUncertainty(
            timingSeconds = 2.0,
            pathKilometers = 3.0,
            notes = "No external contacts found inside the published element validity window.",
        ),
        modelValid = true,
    )

    private fun circumstance(
        contact: EclipseContact,
        instant: Instant,
        observer: Observer,
    ): ContactCircumstance {
        val evaluated = elements.evaluate(instant)
        val horizontal = horizontalCoordinates(evaluated, observer)
        return ContactCircumstance(
            contact = contact,
            instantUtc = instant,
            sunAltitudeDegrees = horizontal.altitudeDegrees,
            sunAzimuthDegrees = horizontal.azimuthDegrees,
        )
    }

    private fun geometry(instant: Instant, observer: Observer): FundamentalPlaneGeometry {
        val evaluated = elements.evaluate(instant)
        val latitude = observer.latitudeDegrees.toRadians()
        val declination = evaluated.declinationDegrees.toRadians()
        val hourAngle = normalizeSignedDegrees(
            evaluated.hourAngleDegrees + observer.longitudeDegrees,
        ).toRadians()

        val flatteningFactor = 0.99664719
        val elevationEarthRadii = observer.elevationMeters / 6_378_137.0
        val denominator = sqrt(cos(latitude) * cos(latitude) +
            flatteningFactor * flatteningFactor * sin(latitude) * sin(latitude))
        val rhoCosPhi = cos(latitude) / denominator + elevationEarthRadii * cos(latitude)
        val rhoSinPhi = flatteningFactor * flatteningFactor * sin(latitude) / denominator +
            elevationEarthRadii * sin(latitude)

        val xi = rhoCosPhi * sin(hourAngle)
        val eta = rhoSinPhi * cos(declination) - rhoCosPhi * cos(hourAngle) * sin(declination)
        val zeta = rhoSinPhi * sin(declination) + rhoCosPhi * cos(hourAngle) * cos(declination)
        val u = evaluated.x - xi
        val v = evaluated.y - eta

        return FundamentalPlaneGeometry(
            separation = sqrt(u * u + v * v),
            penumbralRadius = evaluated.penumbralRadius - zeta * elements.tanF1,
            umbralRadius = evaluated.umbralRadius - zeta * elements.tanF2,
        )
    }

    private fun findRoots(
        start: Instant,
        end: Instant,
        observer: Observer,
        equation: (FundamentalPlaneGeometry) -> Double,
    ): List<Instant> {
        val roots = mutableListOf<Instant>()
        val stepSeconds = 30L
        var left = start
        var leftValue = equation(geometry(left, observer))
        while (left < end) {
            val right = minInstant(left.plusSeconds(stepSeconds), end)
            val rightValue = equation(geometry(right, observer))
            if (leftValue == 0.0 || leftValue * rightValue < 0.0) {
                val root = bisect(left, right, observer, equation)
                if (roots.none { abs(Duration.between(it, root).toMillis()) < 500L }) {
                    roots += root
                }
            }
            left = right
            leftValue = rightValue
        }
        return roots
    }

    private fun bisect(
        initialLeft: Instant,
        initialRight: Instant,
        observer: Observer,
        equation: (FundamentalPlaneGeometry) -> Double,
    ): Instant {
        var left = initialLeft
        var right = initialRight
        var leftValue = equation(geometry(left, observer))
        repeat(50) {
            if (Duration.between(left, right).toMillis() <= 10L) return midpoint(left, right)
            val middle = midpoint(left, right)
            val middleValue = equation(geometry(middle, observer))
            if (leftValue * middleValue <= 0.0) {
                right = middle
            } else {
                left = middle
                leftValue = middleValue
            }
        }
        return midpoint(left, right)
    }

    private fun minimize(start: Instant, end: Instant, observer: Observer): Instant {
        var left = start.toEpochMilli().toDouble()
        var right = end.toEpochMilli().toDouble()
        val golden = (sqrt(5.0) - 1.0) / 2.0
        repeat(80) {
            val c = right - golden * (right - left)
            val d = left + golden * (right - left)
            val cValue = geometry(Instant.ofEpochMilli(c.toLong()), observer).separation
            val dValue = geometry(Instant.ofEpochMilli(d.toLong()), observer).separation
            if (cValue < dValue) right = d else left = c
        }
        return Instant.ofEpochMilli(((left + right) / 2.0).toLong())
    }

    private fun horizontalCoordinates(
        evaluated: EvaluatedBesselianElements,
        observer: Observer,
    ): HorizontalCoordinates {
        val latitude = observer.latitudeDegrees.toRadians()
        val declination = evaluated.declinationDegrees.toRadians()
        val hourAngle = normalizeSignedDegrees(
            evaluated.hourAngleDegrees + observer.longitudeDegrees,
        ).toRadians()
        val altitude = asin(
            sin(latitude) * sin(declination) + cos(latitude) * cos(declination) * cos(hourAngle),
        )
        val azimuth = atan2(
            sin(hourAngle),
            cos(hourAngle) * sin(latitude) - kotlin.math.tan(declination) * cos(latitude),
        )
        return HorizontalCoordinates(
            altitudeDegrees = altitude.toDegrees(),
            azimuthDegrees = normalizeDegrees(azimuth.toDegrees() + 180.0),
        )
    }

    private fun overlapFraction(
        solarRadius: Double,
        lunarRadius: Double,
        separation: Double,
    ): Double {
        if (separation >= solarRadius + lunarRadius) return 0.0
        if (separation <= abs(lunarRadius - solarRadius)) {
            return min(1.0, lunarRadius * lunarRadius / (solarRadius * solarRadius))
        }
        val solarAngle = acos(
            ((separation * separation + solarRadius * solarRadius - lunarRadius * lunarRadius) /
                (2.0 * separation * solarRadius)).coerceIn(-1.0, 1.0),
        )
        val lunarAngle = acos(
            ((separation * separation + lunarRadius * lunarRadius - solarRadius * solarRadius) /
                (2.0 * separation * lunarRadius)).coerceIn(-1.0, 1.0),
        )
        val triangle = 0.5 * sqrt(
            max(
                0.0,
                (-separation + solarRadius + lunarRadius) *
                    (separation + solarRadius - lunarRadius) *
                    (separation - solarRadius + lunarRadius) *
                    (separation + solarRadius + lunarRadius),
            ),
        )
        val overlapArea = solarRadius * solarRadius * solarAngle +
            lunarRadius * lunarRadius * lunarAngle - triangle
        return (overlapArea / (PI * solarRadius * solarRadius)).coerceIn(0.0, 1.0)
    }

    private fun validWindowStartUtc(): Instant =
        Instant.parse("2026-08-12T15:00:00Z").minusMillis((elements.deltaTSeconds * 1_000.0).toLong())

    private fun validWindowEndUtc(): Instant =
        Instant.parse("2026-08-12T21:00:00Z").minusMillis((elements.deltaTSeconds * 1_000.0).toLong())

    private fun midpoint(left: Instant, right: Instant): Instant =
        Instant.ofEpochMilli((left.toEpochMilli() + right.toEpochMilli()) / 2L)

    private fun minInstant(first: Instant, second: Instant): Instant = if (first < second) first else second
}

private data class FundamentalPlaneGeometry(
    val separation: Double,
    val penumbralRadius: Double,
    val umbralRadius: Double,
)

private data class HorizontalCoordinates(
    val altitudeDegrees: Double,
    val azimuthDegrees: Double,
)

private fun Double.toRadians(): Double = this * PI / 180.0
private fun Double.toDegrees(): Double = this * 180.0 / PI

private fun normalizeDegrees(value: Double): Double {
    val result = value % 360.0
    return if (result < 0.0) result + 360.0 else result
}

private fun normalizeSignedDegrees(value: Double): Double {
    val normalized = normalizeDegrees(value)
    return if (normalized > 180.0) normalized - 360.0 else normalized
}
