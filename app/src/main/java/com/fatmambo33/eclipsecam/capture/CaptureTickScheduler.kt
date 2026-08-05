package com.fatmambo33.eclipsecam.capture

import java.time.Duration
import java.time.Instant

sealed interface CaptureTickDirective {
    data object RunImmediately : CaptureTickDirective
    data class ScheduleAt(val instantUtc: Instant) : CaptureTickDirective
    data object Stop : CaptureTickDirective
}

/**
 * Converts one capture execution result into the next foreground-service wake-up decision.
 *
 * Progress results are drained immediately so stale skips and tightly spaced burst instructions do
 * not wait for an arbitrary polling interval. Waiting results are scheduled at the next instruction,
 * with a small floor that prevents a clock-boundary busy loop. Terminal, paused, and inactive
 * results never schedule more work.
 */
class CaptureTickScheduler(
    private val minimumDelay: Duration = Duration.ofMillis(50),
) {
    init {
        require(!minimumDelay.isNegative && !minimumDelay.isZero)
    }

    fun next(
        result: CaptureExecutionResult,
        nowUtc: Instant,
    ): CaptureTickDirective = when (result) {
        is CaptureExecutionResult.Waiting -> CaptureTickDirective.ScheduleAt(
            maxOf(result.nextInstructionAtUtc, nowUtc.plus(minimumDelay)),
        )

        is CaptureExecutionResult.Captured -> progressDirective(result.checkpoint)
        is CaptureExecutionResult.SkippedLate -> progressDirective(result.checkpoint)
        is CaptureExecutionResult.SkippedDegraded -> progressDirective(result.checkpoint)

        is CaptureExecutionResult.Paused,
        is CaptureExecutionResult.Failed,
        is CaptureExecutionResult.Finished,
        is CaptureExecutionResult.Inactive,
        -> CaptureTickDirective.Stop
    }

    private fun progressDirective(checkpoint: CaptureSessionCheckpoint): CaptureTickDirective =
        if (checkpoint.status == CaptureSessionStatus.COMPLETED) {
            CaptureTickDirective.Stop
        } else {
            CaptureTickDirective.RunImmediately
        }
}
