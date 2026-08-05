package com.fatmambo33.eclipsecam.capture

import java.time.Instant

enum class CaptureSessionStatus { ARMED, RUNNING, PAUSED, COMPLETED, FAILED }

enum class CaptureStepOutcome { CAPTURED, SKIPPED }

data class CaptureSessionCheckpoint(
    val sessionId: String,
    val planStartsAtUtc: Instant,
    val planEndsAtUtc: Instant,
    val nextInstructionIndex: Int,
    val capturedCount: Int,
    val skippedCount: Int,
    val status: CaptureSessionStatus,
    val updatedAtUtc: Instant,
    val failureReason: String? = null,
) {
    init {
        require(sessionId.isNotBlank())
        require(nextInstructionIndex >= 0)
        require(capturedCount >= 0)
        require(skippedCount >= 0)
        require(!planEndsAtUtc.isBefore(planStartsAtUtc))
        require((status == CaptureSessionStatus.FAILED) == (failureReason != null))
    }
}

sealed interface CaptureSessionRecovery {
    data class Ready(val session: CaptureSession) : CaptureSessionRecovery
    data class Rejected(val reason: String) : CaptureSessionRecovery
}

class CaptureSession private constructor(
    val plan: CapturePlan,
    private var checkpoint: CaptureSessionCheckpoint,
) {
    fun snapshot(): CaptureSessionCheckpoint = checkpoint

    fun start(nowUtc: Instant): CaptureSessionCheckpoint = transition(nowUtc) {
        require(status == CaptureSessionStatus.ARMED || status == CaptureSessionStatus.PAUSED)
        copy(status = CaptureSessionStatus.RUNNING, updatedAtUtc = nowUtc)
    }

    fun pause(nowUtc: Instant): CaptureSessionCheckpoint = transition(nowUtc) {
        require(status == CaptureSessionStatus.RUNNING)
        copy(status = CaptureSessionStatus.PAUSED, updatedAtUtc = nowUtc)
    }

    fun record(outcome: CaptureStepOutcome, nowUtc: Instant): CaptureSessionCheckpoint = transition(nowUtc) {
        require(status == CaptureSessionStatus.RUNNING)
        require(nextInstructionIndex < plan.instructions.size)
        val next = nextInstructionIndex + 1
        copy(
            nextInstructionIndex = next,
            capturedCount = capturedCount + if (outcome == CaptureStepOutcome.CAPTURED) 1 else 0,
            skippedCount = skippedCount + if (outcome == CaptureStepOutcome.SKIPPED) 1 else 0,
            status = if (next == plan.instructions.size) CaptureSessionStatus.COMPLETED else status,
            updatedAtUtc = nowUtc,
        )
    }

    fun skip(count: Int, nowUtc: Instant): CaptureSessionCheckpoint = transition(nowUtc) {
        require(status == CaptureSessionStatus.RUNNING)
        require(count > 0)
        require(nextInstructionIndex + count <= plan.instructions.size)
        val next = nextInstructionIndex + count
        copy(
            nextInstructionIndex = next,
            skippedCount = skippedCount + count,
            status = if (next == plan.instructions.size) CaptureSessionStatus.COMPLETED else status,
            updatedAtUtc = nowUtc,
        )
    }

    fun fail(reason: String, nowUtc: Instant): CaptureSessionCheckpoint = transition(nowUtc) {
        require(status != CaptureSessionStatus.COMPLETED)
        require(reason.isNotBlank())
        copy(status = CaptureSessionStatus.FAILED, updatedAtUtc = nowUtc, failureReason = reason)
    }

    private inline fun transition(
        nowUtc: Instant,
        block: CaptureSessionCheckpoint.() -> CaptureSessionCheckpoint,
    ): CaptureSessionCheckpoint {
        require(!nowUtc.isBefore(checkpoint.updatedAtUtc))
        checkpoint = checkpoint.block()
        return checkpoint
    }

    companion object {
        fun arm(sessionId: String, plan: CapturePlan, nowUtc: Instant): CaptureSession = CaptureSession(
            plan,
            CaptureSessionCheckpoint(
                sessionId = sessionId,
                planStartsAtUtc = plan.startsAtUtc,
                planEndsAtUtc = plan.endsAtUtc,
                nextInstructionIndex = 0,
                capturedCount = 0,
                skippedCount = 0,
                status = CaptureSessionStatus.ARMED,
                updatedAtUtc = nowUtc,
            ),
        )

        fun recover(plan: CapturePlan, checkpoint: CaptureSessionCheckpoint): CaptureSessionRecovery {
            if (checkpoint.planStartsAtUtc != plan.startsAtUtc || checkpoint.planEndsAtUtc != plan.endsAtUtc) {
                return CaptureSessionRecovery.Rejected("Checkpoint does not match the capture plan.")
            }
            if (checkpoint.nextInstructionIndex > plan.instructions.size) {
                return CaptureSessionRecovery.Rejected("Checkpoint instruction index is outside the capture plan.")
            }
            if (checkpoint.capturedCount + checkpoint.skippedCount != checkpoint.nextInstructionIndex) {
                return CaptureSessionRecovery.Rejected("Checkpoint counters are inconsistent.")
            }
            if (checkpoint.status == CaptureSessionStatus.COMPLETED && checkpoint.nextInstructionIndex != plan.instructions.size) {
                return CaptureSessionRecovery.Rejected("Completed checkpoint has pending instructions.")
            }
            return CaptureSessionRecovery.Ready(CaptureSession(plan, checkpoint))
        }
    }
}
