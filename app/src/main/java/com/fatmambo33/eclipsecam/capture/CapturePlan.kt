package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.astronomy.localcircumstances.EclipseContact
import com.fatmambo33.eclipsecam.astronomy.localcircumstances.EclipseVisibility
import com.fatmambo33.eclipsecam.astronomy.localcircumstances.LocalEclipseCircumstances
import java.time.Duration
import java.time.Instant

/** Eclipse phase associated with a scheduled capture. */
enum class CapturePhase { PARTIAL, CONTACT_BURST, TOTALITY }

/** Conservative hook consumed later by the camera executor. */
enum class ExposureStrategy { FILTERED_PARTIAL, CONTACT_BRACKET, TOTALITY_BRACKET }

/** One deterministic capture instruction. */
data class CaptureInstruction(
    val instantUtc: Instant,
    val phase: CapturePhase,
    val exposureStrategy: ExposureStrategy,
)

/** A local capture plan. It contains no network or camera-framework state. */
data class CapturePlan(
    val startsAtUtc: Instant,
    val endsAtUtc: Instant,
    val instructions: List<CaptureInstruction>,
) {
    init {
        require(!endsAtUtc.isBefore(startsAtUtc))
        require(instructions.isNotEmpty())
        require(instructions == instructions.sortedBy(CaptureInstruction::instantUtc))
        require(instructions.map(CaptureInstruction::instantUtc).distinct().size == instructions.size)
    }
}

data class CaptureCadence(
    val partial: Duration = Duration.ofSeconds(60),
    val contactBurst: Duration = Duration.ofSeconds(5),
    val totality: Duration = Duration.ofSeconds(1),
    val contactBurstRadius: Duration = Duration.ofMinutes(2),
    val maximumInstructions: Int = 20_000,
) {
    init {
        require(!partial.isZero && !partial.isNegative)
        require(!contactBurst.isZero && !contactBurst.isNegative)
        require(!totality.isZero && !totality.isNegative)
        require(!contactBurstRadius.isNegative)
        require(maximumInstructions > 0)
    }
}

sealed interface CapturePlanResult {
    data class Ready(val plan: CapturePlan) : CapturePlanResult
    data class Unavailable(val reason: String) : CapturePlanResult
}

/** Builds a bounded, phase-sensitive schedule from validated local contacts. */
class CapturePlanBuilder(
    private val cadence: CaptureCadence = CaptureCadence(),
) {
    fun build(circumstances: LocalEclipseCircumstances): CapturePlanResult {
        if (!circumstances.modelValid) {
            return CapturePlanResult.Unavailable("Scientific model is not valid.")
        }
        if (circumstances.visibility == EclipseVisibility.NONE) {
            return CapturePlanResult.Unavailable("No eclipse is visible from this position.")
        }

        val c1 = circumstances.contacts[EclipseContact.C1]?.instantUtc
            ?: return CapturePlanResult.Unavailable("C1 is missing.")
        val maximum = circumstances.contacts[EclipseContact.MAXIMUM]?.instantUtc
            ?: return CapturePlanResult.Unavailable("Maximum eclipse is missing.")
        val c4 = circumstances.contacts[EclipseContact.C4]?.instantUtc
            ?: return CapturePlanResult.Unavailable("C4 is missing.")
        if (!(c1 < maximum && maximum < c4)) {
            return CapturePlanResult.Unavailable("Eclipse contacts are not chronologically ordered.")
        }

        val windows = if (circumstances.visibility == EclipseVisibility.TOTAL) {
            totalWindows(circumstances, c1, c4)
                ?: return CapturePlanResult.Unavailable("Totality contacts are missing or invalid.")
        } else {
            partialWindows(c1, maximum, c4)
        }

        val instructions = linkedMapOf<Instant, CaptureInstruction>()
        windows.forEach { window ->
            var instant = window.start
            while (!instant.isAfter(window.end)) {
                instructions[instant] = CaptureInstruction(instant, window.phase, window.strategy)
                if (instructions.size > cadence.maximumInstructions) {
                    return CapturePlanResult.Unavailable("Capture plan exceeds the safe instruction limit.")
                }
                instant = instant.plus(window.interval)
            }
        }
        listOf(c1, maximum, c4).forEach { contact ->
            instructions[contact] = CaptureInstruction(contact, CapturePhase.CONTACT_BURST, ExposureStrategy.CONTACT_BRACKET)
        }

        val ordered = instructions.values.sortedBy(CaptureInstruction::instantUtc)
        return CapturePlanResult.Ready(CapturePlan(c1, c4, ordered))
    }

    private fun totalWindows(
        circumstances: LocalEclipseCircumstances,
        c1: Instant,
        c4: Instant,
    ): List<Window>? {
        val c2 = circumstances.contacts[EclipseContact.C2]?.instantUtc ?: return null
        val c3 = circumstances.contacts[EclipseContact.C3]?.instantUtc ?: return null
        if (!(c1 < c2 && c2 < c3 && c3 < c4)) return null
        return listOfNotNull(
            window(c1, c2.minus(cadence.contactBurstRadius), cadence.partial, CapturePhase.PARTIAL, ExposureStrategy.FILTERED_PARTIAL),
            window(c2.minus(cadence.contactBurstRadius), c2, cadence.contactBurst, CapturePhase.CONTACT_BURST, ExposureStrategy.CONTACT_BRACKET),
            window(c2, c3, cadence.totality, CapturePhase.TOTALITY, ExposureStrategy.TOTALITY_BRACKET),
            window(c3, c3.plus(cadence.contactBurstRadius), cadence.contactBurst, CapturePhase.CONTACT_BURST, ExposureStrategy.CONTACT_BRACKET),
            window(c3.plus(cadence.contactBurstRadius), c4, cadence.partial, CapturePhase.PARTIAL, ExposureStrategy.FILTERED_PARTIAL),
        )
    }

    private fun partialWindows(c1: Instant, maximum: Instant, c4: Instant): List<Window> = listOfNotNull(
        window(c1, maximum.minus(cadence.contactBurstRadius), cadence.partial, CapturePhase.PARTIAL, ExposureStrategy.FILTERED_PARTIAL),
        window(maximum.minus(cadence.contactBurstRadius), maximum.plus(cadence.contactBurstRadius), cadence.contactBurst, CapturePhase.CONTACT_BURST, ExposureStrategy.CONTACT_BRACKET),
        window(maximum.plus(cadence.contactBurstRadius), c4, cadence.partial, CapturePhase.PARTIAL, ExposureStrategy.FILTERED_PARTIAL),
    )

    private fun window(
        start: Instant,
        end: Instant,
        interval: Duration,
        phase: CapturePhase,
        strategy: ExposureStrategy,
    ): Window? = if (end.isBefore(start)) null else Window(start, end, interval, phase, strategy)

    private data class Window(
        val start: Instant,
        val end: Instant,
        val interval: Duration,
        val phase: CapturePhase,
        val strategy: ExposureStrategy,
    )
}
