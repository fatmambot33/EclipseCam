package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.device.health.CaptureReadiness
import com.fatmambo33.eclipsecam.device.health.DeviceHealthDecision
import java.time.Duration
import java.time.Instant

sealed interface CameraCaptureResult {
    data object Captured : CameraCaptureResult
    data class RecoverableError(val reason: String) : CameraCaptureResult
    data class FatalError(val reason: String) : CameraCaptureResult
}

fun interface CaptureInstructionExecutor {
    fun capture(instruction: CaptureInstruction): CameraCaptureResult
}

sealed interface CaptureExecutionResult {
    data class Waiting(val nextInstructionAtUtc: Instant) : CaptureExecutionResult
    data class Captured(val checkpoint: CaptureSessionCheckpoint) : CaptureExecutionResult
    data class SkippedLate(
        val checkpoint: CaptureSessionCheckpoint,
        val skippedInstructionCount: Int,
    ) : CaptureExecutionResult
    data class Paused(val checkpoint: CaptureSessionCheckpoint, val reason: String) : CaptureExecutionResult
    data class Failed(val checkpoint: CaptureSessionCheckpoint, val reason: String) : CaptureExecutionResult
    data class Finished(val checkpoint: CaptureSessionCheckpoint) : CaptureExecutionResult
    data class Inactive(val status: CaptureSessionStatus) : CaptureExecutionResult
}

/**
 * Executes at most one recoverable due capture instruction per tick.
 *
 * Consecutive instructions beyond [maximumLateness] are skipped as one atomic checkpoint update so
 * process suspension cannot leave the service replaying a stale backlog while the eclipse moves on.
 * The engine is framework-neutral and every state mutation is persisted before returning.
 */
class CaptureExecutionEngine(
    private val plan: CapturePlan,
    private val coordinator: CaptureSessionCoordinator,
    private val executor: CaptureInstructionExecutor,
    private val maximumLateness: Duration = Duration.ofSeconds(10),
) {
    init {
        require(!maximumLateness.isNegative)
    }

    fun tick(
        nowUtc: Instant,
        health: DeviceHealthDecision,
    ): CaptureExecutionResult {
        val checkpoint = coordinator.snapshot()
        if (checkpoint.status == CaptureSessionStatus.COMPLETED) {
            return CaptureExecutionResult.Finished(checkpoint)
        }
        if (checkpoint.status != CaptureSessionStatus.RUNNING) {
            return CaptureExecutionResult.Inactive(checkpoint.status)
        }
        if (health.readiness == CaptureReadiness.BLOCKED) {
            val reason = "Capture paused by device safeguard: ${health.reasons.sortedBy { it.name }.joinToString()}"
            return CaptureExecutionResult.Paused(coordinator.pause(nowUtc), reason)
        }

        val instruction = plan.instructions.getOrNull(checkpoint.nextInstructionIndex)
            ?: return CaptureExecutionResult.Failed(
                coordinator.fail("Capture plan has no pending instruction.", nowUtc),
                "Capture plan has no pending instruction.",
            )
        if (nowUtc.isBefore(instruction.instantUtc)) {
            return CaptureExecutionResult.Waiting(instruction.instantUtc)
        }

        val lateCount = plan.instructions
            .asSequence()
            .drop(checkpoint.nextInstructionIndex)
            .takeWhile { candidate -> Duration.between(candidate.instantUtc, nowUtc) > maximumLateness }
            .count()
        if (lateCount > 0) {
            return CaptureExecutionResult.SkippedLate(
                checkpoint = coordinator.skip(lateCount, nowUtc),
                skippedInstructionCount = lateCount,
            )
        }

        return when (val result = executor.capture(instruction)) {
            CameraCaptureResult.Captured -> CaptureExecutionResult.Captured(
                coordinator.record(CaptureStepOutcome.CAPTURED, nowUtc),
            )
            is CameraCaptureResult.RecoverableError -> CaptureExecutionResult.Paused(
                coordinator.pause(nowUtc),
                result.reason,
            )
            is CameraCaptureResult.FatalError -> CaptureExecutionResult.Failed(
                coordinator.fail(result.reason, nowUtc),
                result.reason,
            )
        }
    }
}
