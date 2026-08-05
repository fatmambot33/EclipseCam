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
    data class SkippedLate(val checkpoint: CaptureSessionCheckpoint) : CaptureExecutionResult
    data class Paused(val checkpoint: CaptureSessionCheckpoint, val reason: String) : CaptureExecutionResult
    data class Failed(val checkpoint: CaptureSessionCheckpoint, val reason: String) : CaptureExecutionResult
    data class Finished(val checkpoint: CaptureSessionCheckpoint) : CaptureExecutionResult
    data class Inactive(val status: CaptureSessionStatus) : CaptureExecutionResult
}

/**
 * Executes at most one due capture instruction per tick.
 *
 * The engine is framework-neutral so scheduling, safeguard and camera-error behavior can be
 * validated without an Android device. Every state mutation flows through the coordinator and is
 * therefore persisted before the result is returned.
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
        if (Duration.between(instruction.instantUtc, nowUtc) > maximumLateness) {
            return CaptureExecutionResult.SkippedLate(
                coordinator.record(CaptureStepOutcome.SKIPPED, nowUtc),
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
