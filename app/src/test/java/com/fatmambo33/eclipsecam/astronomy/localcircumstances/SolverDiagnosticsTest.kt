package com.fatmambo33.eclipsecam.astronomy.localcircumstances

import org.junit.Assert.assertEquals
import org.junit.Test

class SolverDiagnosticsTest {
    private val calculator = BesselianLocalCircumstancesCalculator()

    @Test
    fun `total eclipse reports converged bounded search`() {
        val result = calculator.calculate(
            Observer(latitudeDegrees = 43.3717, longitudeDegrees = -6.1883, elevationMeters = 100.0),
        )

        assertEquals(SolverStatus.CONVERGED, result.solverDiagnostics.status)
        assertEquals(10L, result.solverDiagnostics.rootToleranceMillis)
        assertEquals(50, result.solverDiagnostics.maximumRootIterations)
        assertEquals(80, result.solverDiagnostics.maximumMinimumIterations)
    }

    @Test
    fun `no eclipse is distinct from numerical failure`() {
        val result = calculator.calculate(
            Observer(latitudeDegrees = -33.8688, longitudeDegrees = 151.2093),
        )

        assertEquals(EclipseVisibility.NONE, result.visibility)
        assertEquals(SolverStatus.NO_ECLIPSE, result.solverDiagnostics.status)
    }

    @Test
    fun `invalid model state reports failed explicitly`() {
        val result = LocalEclipseCircumstances(
            observer = Observer(0.0, 0.0),
            visibility = EclipseVisibility.NONE,
            contacts = emptyMap(),
            magnitude = 0.0,
            obscuration = 0.0,
            totalityDurationSeconds = null,
            uncertainty = ModelUncertainty(2.0, 3.0, "fixture"),
            modelValid = false,
        )

        assertEquals(SolverStatus.FAILED, result.solverDiagnostics.status)
    }
}
