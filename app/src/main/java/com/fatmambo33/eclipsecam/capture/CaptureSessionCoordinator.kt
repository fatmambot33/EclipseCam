package com.fatmambo33.eclipsecam.capture

import java.time.Instant

sealed interface CaptureSessionRestoreResult {
    data object Missing : CaptureSessionRestoreResult
    data class Ready(val coordinator: CaptureSessionCoordinator) : CaptureSessionRestoreResult
    data class Rejected(val reason: String) : CaptureSessionRestoreResult
}

class CaptureSessionCoordinator private constructor(
    private val session: CaptureSession,
    private val checkpointStore: CaptureCheckpointStore,
) {
    fun snapshot(): CaptureSessionCheckpoint = session.snapshot()

    fun start(nowUtc: Instant): CaptureSessionCheckpoint =
        persist(session.start(nowUtc))

    fun pause(nowUtc: Instant): CaptureSessionCheckpoint =
        persist(session.pause(nowUtc))

    fun record(
        outcome: CaptureStepOutcome,
        nowUtc: Instant,
    ): CaptureSessionCheckpoint = persist(session.record(outcome, nowUtc))

    fun skip(
        count: Int,
        nowUtc: Instant,
    ): CaptureSessionCheckpoint = persist(session.skip(count, nowUtc))

    fun fail(
        reason: String,
        nowUtc: Instant,
    ): CaptureSessionCheckpoint = persist(session.fail(reason, nowUtc))

    fun clear(): Boolean = checkpointStore.clear()

    private fun persist(checkpoint: CaptureSessionCheckpoint): CaptureSessionCheckpoint {
        checkpointStore.write(checkpoint)
        return checkpoint
    }

    companion object {
        fun arm(
            sessionId: String,
            plan: CapturePlan,
            nowUtc: Instant,
            checkpointStore: CaptureCheckpointStore,
        ): CaptureSessionCoordinator {
            val coordinator = CaptureSessionCoordinator(
                session = CaptureSession.arm(sessionId, plan, nowUtc),
                checkpointStore = checkpointStore,
            )
            checkpointStore.write(coordinator.snapshot())
            return coordinator
        }

        fun restore(
            plan: CapturePlan,
            checkpointStore: CaptureCheckpointStore,
        ): CaptureSessionRestoreResult = when (val stored = checkpointStore.read()) {
            CheckpointReadResult.Missing -> CaptureSessionRestoreResult.Missing
            is CheckpointReadResult.Corrupt -> CaptureSessionRestoreResult.Rejected(stored.reason)
            is CheckpointReadResult.Loaded -> when (
                val recovery = CaptureSession.recover(plan, stored.checkpoint)
            ) {
                is CaptureSessionRecovery.Rejected -> CaptureSessionRestoreResult.Rejected(recovery.reason)
                is CaptureSessionRecovery.Ready -> CaptureSessionRestoreResult.Ready(
                    CaptureSessionCoordinator(recovery.session, checkpointStore),
                )
            }
        }
    }
}
