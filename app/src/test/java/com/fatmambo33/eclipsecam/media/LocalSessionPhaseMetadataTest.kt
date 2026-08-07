package com.fatmambo33.eclipsecam.media

import com.fatmambo33.eclipsecam.capture.CaptureInstruction
import com.fatmambo33.eclipsecam.capture.CapturePhase
import com.fatmambo33.eclipsecam.capture.CapturePlan
import com.fatmambo33.eclipsecam.capture.CaptureSessionCheckpoint
import com.fatmambo33.eclipsecam.capture.CaptureSessionStatus
import com.fatmambo33.eclipsecam.capture.ExposureStrategy
import java.io.File
import java.nio.file.Files
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalSessionPhaseMetadataTest {
    @Test
    fun indexProjectsPersistedPlanPhaseAndInstructionIndexOntoOriginalCaptures() {
        val root = Files.createTempDirectory("phase-index").toFile()
        try {
            val start = Instant.parse("2026-08-12T17:00:00Z")
            val plan = CapturePlan(
                startsAtUtc = start,
                endsAtUtc = start.plusSeconds(2),
                instructions = listOf(
                    CaptureInstruction(start, CapturePhase.PARTIAL, ExposureStrategy.FILTERED_PARTIAL),
                    CaptureInstruction(start.plusSeconds(1), CapturePhase.CONTACT_BURST, ExposureStrategy.CONTACT_BRACKET),
                    CaptureInstruction(start.plusSeconds(2), CapturePhase.TOTALITY, ExposureStrategy.TOTALITY_BRACKET),
                ),
            )
            val checkpoint = CaptureSessionCheckpoint(
                sessionId = "phase-session",
                planStartsAtUtc = plan.startsAtUtc,
                planEndsAtUtc = plan.endsAtUtc,
                nextInstructionIndex = 3,
                capturedCount = 3,
                skippedCount = 0,
                status = CaptureSessionStatus.COMPLETED,
                updatedAtUtc = start.plusSeconds(3),
            )
            FileLocalCaptureSessionJournal(root).record(plan, checkpoint)
            val directory = File(root, "phase-session")
            File(directory, "000000_partial.jpg").writeBytes(byteArrayOf(1))
            File(directory, "000001_contact.jpg").writeBytes(byteArrayOf(2))
            File(directory, "000002_totality.jpg").writeBytes(byteArrayOf(3))
            File(directory, "legacy.jpg").writeBytes(byteArrayOf(4))

            val captures = LocalSessionIndex(root).listSessions().single().captures.associateBy { it.file.name }

            assertEquals(CapturePhase.PARTIAL, captures.getValue("000000_partial.jpg").phase)
            assertEquals(0, captures.getValue("000000_partial.jpg").instructionIndex)
            assertEquals(CapturePhase.CONTACT_BURST, captures.getValue("000001_contact.jpg").phase)
            assertEquals(1, captures.getValue("000001_contact.jpg").instructionIndex)
            assertEquals(CapturePhase.TOTALITY, captures.getValue("000002_totality.jpg").phase)
            assertEquals(2, captures.getValue("000002_totality.jpg").instructionIndex)
            assertNull(captures.getValue("legacy.jpg").phase)
            assertNull(captures.getValue("legacy.jpg").instructionIndex)
        } finally {
            root.deleteRecursively()
        }
    }
}
