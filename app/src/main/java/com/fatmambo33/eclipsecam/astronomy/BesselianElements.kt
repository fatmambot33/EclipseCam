package com.fatmambo33.eclipsecam.astronomy

import java.time.Instant
import java.time.ZoneOffset

/** Polynomial Besselian elements for one eclipse. */
data class BesselianElements(
    val referenceTdtHour: Double,
    val deltaTSeconds: Double,
    val x: Polynomial,
    val y: Polynomial,
    val declinationDegrees: Polynomial,
    val penumbralRadius: Polynomial,
    val umbralRadius: Polynomial,
    val hourAngleDegrees: Polynomial,
    val tanF1: Double,
    val tanF2: Double,
) {
    /** Evaluate the elements at a UTC instant. */
    fun evaluate(instantUtc: Instant): EvaluatedBesselianElements {
        val utc = instantUtc.atZone(ZoneOffset.UTC)
        val utcHour = utc.hour + utc.minute / 60.0 + utc.second / 3600.0 + utc.nano / 3.6e12
        val tdtHour = utcHour + deltaTSeconds / 3600.0
        val t = tdtHour - referenceTdtHour
        require(tdtHour in 15.0..21.0) {
            "2026-08-12 Besselian elements are valid only from 15:00 to 21:00 TDT"
        }
        return EvaluatedBesselianElements(
            tHours = t,
            x = x.evaluate(t),
            y = y.evaluate(t),
            declinationDegrees = declinationDegrees.evaluate(t),
            penumbralRadius = penumbralRadius.evaluate(t),
            umbralRadius = umbralRadius.evaluate(t),
            hourAngleDegrees = normalizeDegrees(hourAngleDegrees.evaluate(t)),
        )
    }
}

data class EvaluatedBesselianElements(
    val tHours: Double,
    val x: Double,
    val y: Double,
    val declinationDegrees: Double,
    val penumbralRadius: Double,
    val umbralRadius: Double,
    val hourAngleDegrees: Double,
)

data class Polynomial(
    val c0: Double,
    val c1: Double = 0.0,
    val c2: Double = 0.0,
    val c3: Double = 0.0,
) {
    fun evaluate(t: Double): Double = ((c3 * t + c2) * t + c1) * t + c0
}

object Eclipse2026Aug12 {
    val elements = BesselianElements(
        referenceTdtHour = 18.0,
        deltaTSeconds = 71.4,
        x = Polynomial(0.475593, 0.5189288, -0.0000773, -0.0000088),
        y = Polynomial(0.771161, -0.2301664, -0.0001245, 0.0000037),
        declinationDegrees = Polynomial(14.79667, -0.012065, -0.000003),
        penumbralRadius = Polynomial(0.537954, 0.0000940, -0.0000121),
        umbralRadius = Polynomial(-0.008142, 0.0000935, -0.0000121),
        hourAngleDegrees = Polynomial(88.74776, 15.003093),
        tanF1 = 0.0046141,
        tanF2 = 0.0045911,
    )

    val greatestEclipseUtc: Instant = Instant.parse("2026-08-12T17:45:53.8Z")
}

private fun normalizeDegrees(value: Double): Double {
    val normalized = value % 360.0
    return if (normalized < 0.0) normalized + 360.0 else normalized
}
