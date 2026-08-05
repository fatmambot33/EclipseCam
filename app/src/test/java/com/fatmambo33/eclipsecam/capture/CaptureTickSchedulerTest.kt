package com.fatmambo33.eclipsecam.capture

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureTickSchedulerTest {
    private val now = Instant.parse("2026-08-12T17:45:00Z")
    private val scheduler = CaptureTickScheduler(Duration.ofMillis(100))

    @Test
    fun waitingSchedulesAtFutureInstruction() {
        val next = now.plusSeconds(5)

        assertEquals(
            CaptureTickDirective.ScheduleAt(next),
            scheduler.next(CaptureExecutionResult.Waiting(next), now),
        )
    }

    @Test
    fun waitingUsesMinimumDelayAtClockBoundary() {
        assertEquals(
            CaptureTickDirective.ScheduleAt(now.plusMillis(100)),
            scheduler.next(CaptureExecutionResult.Waiting(now), now),
        )
    }

    @Test
    fun progressDrainsPendingWorkImmediately() {
        val checkpoint = checkpoint(CaptureSessionStatus.RUNNING)

        listOf(
            CaptureExecutionResult.Captured(checkpoint),
            CaptureExecutionResult.SkippedLate(checkpoint, skippedInstructionCount = 2),
            CaptureExecutionResult.SkippedDegraded(checkpoint, reason = "Battery low"),
        ).forEach { result ->
            assertEquals(CaptureTickDirective.RunImmediately, scheduler.next(result, now))
        }
    }

    @Test
    fun completedProgressStopsScheduling() {
        val completed = checkpoint(CaptureSessionStatus.COMPLETED, nextInstructionIndex = 1)

        assertEquals(
            CaptureTickDirective.Stop,
            scheduler.next(CaptureExecutionResult.Captured(completed), now),
        )
    }

    @Test
    fun pausedFailedFinishedAndInactiveStopScheduling() {
        val paused = checkpoint(CaptureSessionStatus.PAUSED)
        val failed = checkpoint(CaptureSessionStatus.FAILED, failureReason = "Camera failed")
        val completed = checkpoint(CaptureSessionStatus.COMPLETED, nextInstructionIndex = 1)

        listOf(
            CaptureExecutionResult.Paused(paused, "Thermal"),
            CaptureExecutionResult.Failed(failed, "Camera failed"),
            CaptureExecutionResult.Finished(completed),
            CaptureExecutionResult.Inactive(CaptureSessionStatus.PAUSED),
        ).forEach { result ->
            assertEquals(CaptureTickDirective.Stop, scheduler.next(result, now))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun minimumDelayMustBePositive() {
        CaptureTickScheduler(Duration.ZERO)
    }

    private fun checkpoint(
        status: CaptureSessionStatus,
        nextInstructionIndex: Int = 0,
        failureReason: String? = null,
    ) = CaptureSessionCheckpoint(
        sessionId = "session",
        planStartsAtUtc = now,
        planEndsAtUtc = now.plusSeconds(60),
        nextInstructionIndex = nextInstructionIndex,
        capturedCount = nextInstructionIndex,
        skippedCount = 0,
        status = status,
        updatedAtUtc = now,
        failureReason = failureReason,
    )
}
