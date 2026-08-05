package com.fatmambo33.eclipsecam.capture

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CapturePlanStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val start = Instant.parse("2026-08-12T17:00:00Z")
    private val plan = CapturePlan(
        startsAtUtc = start,
        endsAtUtc = start.plusSeconds(2),
        instructions = listOf(
            CaptureInstruction(start, CapturePhase.PARTIAL, ExposureStrategy.FILTERED_PARTIAL),
            CaptureInstruction(start.plusSeconds(1), CapturePhase.CONTACT_BURST, ExposureStrategy.CONTACT_BRACKET),
            CaptureInstruction(start.plusSeconds(2), CapturePhase.TOTALITY, ExposureStrategy.TOTALITY_BRACKET),
        ),
    )

    @Test
    fun codecRoundTripsCompletePlan() {
        val result = CapturePlanCodec.decode(CapturePlanCodec.encode(plan))

        assertEquals(plan, (result as CapturePlanReadResult.Loaded).plan)
    }

    @Test
    fun corruptUnsupportedOrIncompletePlansAreRejected() {
        assertTrue(CapturePlanCodec.decode("version=99\n") is CapturePlanReadResult.Corrupt)
        assertTrue(CapturePlanCodec.decode("version=1\ninstructionCount=0\n") is CapturePlanReadResult.Corrupt)
        assertTrue(
            CapturePlanCodec.decode(
                "version=1\nstartsAtUtc=$start\nendsAtUtc=${start.plusSeconds(1)}\ninstructionCount=1\n",
            ) is CapturePlanReadResult.Corrupt,
        )
    }

    @Test
    fun fileStoreWritesReadsOverwritesAndClears() {
        val file = temporaryFolder.newFolder("capture-plan").resolve("plan.txt")
        val store = FileCapturePlanStore(file)

        assertTrue(store.read() is CapturePlanReadResult.Missing)
        store.write(plan)
        assertEquals(plan, (store.read() as CapturePlanReadResult.Loaded).plan)

        val updated = plan.copy(endsAtUtc = start.plusSeconds(1), instructions = plan.instructions.take(2))
        store.write(updated)
        assertEquals(updated, (store.read() as CapturePlanReadResult.Loaded).plan)

        assertTrue(store.clear())
        assertTrue(store.read() is CapturePlanReadResult.Missing)
    }

    @Test
    fun fileStorePreservesCorruptContentForDiagnosis() {
        val file = temporaryFolder.newFile("plan.txt")
        file.writeText("not-a-plan")
        val store = FileCapturePlanStore(file)

        assertTrue(store.read() is CapturePlanReadResult.Corrupt)
        assertTrue(file.exists())
    }
}
