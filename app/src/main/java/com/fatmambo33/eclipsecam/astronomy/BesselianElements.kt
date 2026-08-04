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

/**
 * Current NASA/GSFC Besselian element set for the 12 August 2026 total eclipse.
 *
 * Source: NASA Solar Eclipse Search Engine, dataset `20260812`, updated 2023-10-30.
 * Eclipse predictions are by Fred Espenak, NASA's GSFC.
 */
object Eclipse2026Aug12 {
    const val SOURCE_DATASET = "NASA-GSFC-SEdata-20260812-2023-10-30"

    val elements = BesselianElements(
        referenceTdtHour = 18.0,
        deltaTSeconds = 75.4,
        x = Polynomial(0.4755140, 0.5189249, -0.0000773),
        y = Polynomial(0.7711830, -0.2301680, -0.0001246),
        declinationDegrees = Polynomial(14.7966700, -0.0120650, -0.0000030),
        penumbralRadius = Polynomial(0.5379550, 0.0000939, -0.0000121),
        umbralRadius = Polynomial(-0.0081420, 0.0000935, -0.0000121),
        hourAngleDegrees = Polynomial(88.747787, 15.003090),
        tanF1 = 0.0046141,
        tanF2 = 0.0045911,
    )

    val greatestEclipseUtc: Instant = Instant.parse("2026-08-12T17:45:51Z")
}

private fun normalizeDegrees(value: Double): Double {
    val normalized = value % 360.0
    return if (normalized < 0.0) normalized + 360.0 else normalized
}
