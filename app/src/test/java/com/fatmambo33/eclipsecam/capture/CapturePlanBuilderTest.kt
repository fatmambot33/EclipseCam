package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.astronomy.localcircumstances.ContactCircumstance
import com.fatmambo33.eclipsecam.astronomy.localcircumstances.EclipseContact
import com.fatmambo33.eclipsecam.astronomy.localcircumstances.EclipseVisibility
import com.fatmambo33.eclipsecam.astronomy.localcircumstances.LocalEclipseCircumstances
import com.fatmambo33.eclipsecam.astronomy.localcircumstances.ModelUncertainty
import com.fatmambo33.eclipsecam.astronomy.localcircumstances.Observer
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturePlanBuilderTest {
    private val c1 = Instant.parse("2026-08-12T16:00:00Z")
    private val c2 = Instant.parse("2026-08-12T17:44:00Z")
    private val maximum = Instant.parse("2026-08-12T17:45:00Z")
    private val c3 = Instant.parse("2026-08-12T17:46:00Z")
    private val c4 = Instant.parse("2026-08-12T19:00:00Z")

    @Test
    fun totalEclipseUsesFastestCadenceInsideTotality() {
        val result = CapturePlanBuilder().build(circumstances(EclipseVisibility.TOTAL)) as CapturePlanResult.Ready

        val totality = result.plan.instructions.filter { it.phase == CapturePhase.TOTALITY }
        assertEquals(c2.plusSeconds(1), totality.first().instantUtc)
        assertEquals(c3.minusSeconds(1), totality.last().instantUtc)
        assertTrue(totality.zipWithNext().all { (a, b) -> b.instantUtc.epochSecond - a.instantUtc.epochSecond == 1L })
        assertEquals(ExposureStrategy.TOTALITY_BRACKET, totality.first().exposureStrategy)
        assertEquals(ExposureStrategy.TOTALITY_BRACKET, instructionAt(result.plan, maximum).exposureStrategy)
    }

    @Test
    fun partialEclipseNeverSchedulesTotalityStrategy() {
        val result = CapturePlanBuilder().build(
            circumstances(EclipseVisibility.PARTIAL, includeInternalContacts = false),
        ) as CapturePlanResult.Ready

        assertTrue(result.plan.instructions.none { it.phase == CapturePhase.TOTALITY })
        assertTrue(result.plan.instructions.any { it.phase == CapturePhase.CONTACT_BURST })
        assertEquals(c1, result.plan.startsAtUtc)
        assertEquals(c4, result.plan.endsAtUtc)
    }

    @Test
    fun exactContactsUseContactBracketsWithoutDuplicates() {
        val result = CapturePlanBuilder().build(circumstances(EclipseVisibility.TOTAL)) as CapturePlanResult.Ready
        val instants = result.plan.instructions.map(CaptureInstruction::instantUtc)

        listOf(c1, c2, c3, c4).forEach { contact ->
            val instruction = instructionAt(result.plan, contact)
            assertEquals(CapturePhase.CONTACT_BURST, instruction.phase)
            assertEquals(ExposureStrategy.CONTACT_BRACKET, instruction.exposureStrategy)
        }
        assertEquals(instants.distinct().size, instants.size)
    }

    @Test
    fun invalidScientificModelIsRejected() {
        val result = CapturePlanBuilder().build(circumstances(EclipseVisibility.TOTAL, modelValid = false))

        assertTrue(result is CapturePlanResult.Unavailable)
    }

    @Test
    fun totalEclipseWithoutInternalContactsIsRejected() {
        val result = CapturePlanBuilder().build(
            circumstances(EclipseVisibility.TOTAL, includeInternalContacts = false),
        )

        assertTrue(result is CapturePlanResult.Unavailable)
    }

    private fun instructionAt(plan: CapturePlan, instant: Instant): CaptureInstruction =
        requireNotNull(plan.instructions.firstOrNull { it.instantUtc == instant })

    private fun circumstances(
        visibility: EclipseVisibility,
        includeInternalContacts: Boolean = true,
        modelValid: Boolean = true,
    ): LocalEclipseCircumstances {
        val contacts = linkedMapOf(
            EclipseContact.C1 to contact(EclipseContact.C1, c1),
            EclipseContact.MAXIMUM to contact(EclipseContact.MAXIMUM, maximum),
            EclipseContact.C4 to contact(EclipseContact.C4, c4),
        )
        if (includeInternalContacts) {
            contacts[EclipseContact.C2] = contact(EclipseContact.C2, c2)
            contacts[EclipseContact.C3] = contact(EclipseContact.C3, c3)
        }
        return LocalEclipseCircumstances(
            observer = Observer(43.0, -3.0),
            visibility = visibility,
            contacts = contacts,
            magnitude = 1.01,
            obscuration = 1.0,
            totalityDurationSeconds = if (visibility == EclipseVisibility.TOTAL) 120.0 else null,
            uncertainty = ModelUncertainty(1.0, 2.0, "fixture"),
            modelValid = modelValid,
        )
    }

    private fun contact(type: EclipseContact, instant: Instant) = ContactCircumstance(
        contact = type,
        instantUtc = instant,
        sunAltitudeDegrees = 20.0,
        sunAzimuthDegrees = 250.0,
    )
}
