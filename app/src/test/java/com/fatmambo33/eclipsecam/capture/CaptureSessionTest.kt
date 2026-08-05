package com.fatmambo33.eclipsecam.capture

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureSessionTest {
    private val start = Instant.parse("2026-08-12T17:00:00Z")
    private val instructions = listOf(
        CaptureInstruction(start, CapturePhase.PARTIAL, ExposureStrategy.FILTERED_PARTIAL),
        CaptureInstruction(start.plusSeconds(1), CapturePhase.TOTALITY, ExposureStrategy.TOTALITY_BRACKET),
    )
    private val plan = CapturePlan(start, start.plusSeconds(1), instructions)

    @Test
    fun progressCompletesAndCountsOutcomes() {
        val session = CaptureSession.arm("session-1", plan, start.minusSeconds(10))
        session.start(start.minusSeconds(5))
        session.record(CaptureStepOutcome.CAPTURED, start)
        val done = session.record(CaptureStepOutcome.SKIPPED, start.plusSeconds(1))

        assertEquals(CaptureSessionStatus.COMPLETED, done.status)
        assertEquals(1, done.capturedCount)
        assertEquals(1, done.skippedCount)
        assertEquals(2, done.nextInstructionIndex)
    }

    @Test
    fun pausedSessionCanBeRecoveredAndResumed() {
        val session = CaptureSession.arm("session-2", plan, start.minusSeconds(10))
        session.start(start.minusSeconds(5))
        session.record(CaptureStepOutcome.CAPTURED, start)
        val paused = session.pause(start.plusSeconds(1))

        val recovery = CaptureSession.recover(plan, paused) as CaptureSessionRecovery.Ready
        val resumed = recovery.session.start(start.plusSeconds(2))

        assertEquals(CaptureSessionStatus.RUNNING, resumed.status)
        assertEquals(1, resumed.nextInstructionIndex)
    }

    @Test
    fun incompatiblePlanIsRejected() {
        val checkpoint = CaptureSession.arm("session-3", plan, start.minusSeconds(10)).snapshot()
        val otherPlan = plan.copy(endsAtUtc = start.plusSeconds(2))

        assertTrue(CaptureSession.recover(otherPlan, checkpoint) is CaptureSessionRecovery.Rejected)
    }

    @Test
    fun inconsistentCountersAreRejected() {
        val checkpoint = CaptureSession.arm("session-4", plan, start.minusSeconds(10)).snapshot().copy(
            nextInstructionIndex = 1,
            capturedCount = 0,
            skippedCount = 0,
        )

        assertTrue(CaptureSession.recover(plan, checkpoint) is CaptureSessionRecovery.Rejected)
    }

    @Test
    fun failureRecordsLocalReason() {
        val session = CaptureSession.arm("session-5", plan, start.minusSeconds(10))
        session.start(start.minusSeconds(5))
        val failed = session.fail("Camera unavailable", start)

        assertEquals(CaptureSessionStatus.FAILED, failed.status)
        assertEquals("Camera unavailable", failed.failureReason)
    }
}
