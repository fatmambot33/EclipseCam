package com.fatmambo33.eclipsecam.capture

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureSessionCoordinatorTest {
    private val start = Instant.parse("2026-08-12T17:00:00Z")
    private val plan = CapturePlan(
        startsAtUtc = start,
        endsAtUtc = start.plusSeconds(1),
        instructions = listOf(
            CaptureInstruction(start, CapturePhase.PARTIAL, ExposureStrategy.FILTERED_PARTIAL),
            CaptureInstruction(
                start.plusSeconds(1),
                CapturePhase.TOTALITY,
                ExposureStrategy.TOTALITY_BRACKET,
            ),
        ),
    )

    @Test
    fun armAndEveryTransitionArePersisted() {
        val store = RecordingCheckpointStore()
        val coordinator = CaptureSessionCoordinator.arm(
            sessionId = "session-1",
            plan = plan,
            nowUtc = start.minusSeconds(10),
            checkpointStore = store,
        )

        coordinator.start(start.minusSeconds(5))
        coordinator.record(CaptureStepOutcome.CAPTURED, start)
        coordinator.pause(start.plusSeconds(1))

        assertEquals(4, store.writes.size)
        assertEquals(CaptureSessionStatus.ARMED, store.writes[0].status)
        assertEquals(CaptureSessionStatus.RUNNING, store.writes[1].status)
        assertEquals(1, store.writes[2].capturedCount)
        assertEquals(CaptureSessionStatus.PAUSED, store.writes[3].status)
    }

    @Test
    fun restoredCoordinatorContinuesFromStoredInstruction() {
        val store = RecordingCheckpointStore()
        val original = CaptureSessionCoordinator.arm(
            "session-2",
            plan,
            start.minusSeconds(10),
            store,
        )
        original.start(start.minusSeconds(5))
        original.record(CaptureStepOutcome.CAPTURED, start)
        original.pause(start.plusMillis(100))

        val restored = CaptureSessionCoordinator.restore(plan, store)
            as CaptureSessionRestoreResult.Ready
        restored.coordinator.start(start.plusMillis(200))
        val completed = restored.coordinator.record(
            CaptureStepOutcome.SKIPPED,
            start.plusSeconds(1),
        )

        assertEquals(CaptureSessionStatus.COMPLETED, completed.status)
        assertEquals(1, completed.capturedCount)
        assertEquals(1, completed.skippedCount)
    }

    @Test
    fun corruptCheckpointIsRejectedWithoutStartingSession() {
        val store = RecordingCheckpointStore(
            readResult = CheckpointReadResult.Corrupt("bad checkpoint"),
        )

        val result = CaptureSessionCoordinator.restore(plan, store)

        assertTrue(result is CaptureSessionRestoreResult.Rejected)
        assertEquals(0, store.writes.size)
    }

    @Test
    fun missingCheckpointIsReportedExplicitly() {
        val result = CaptureSessionCoordinator.restore(
            plan,
            RecordingCheckpointStore(CheckpointReadResult.Missing),
        )

        assertEquals(CaptureSessionRestoreResult.Missing, result)
    }

    @Test
    fun mismatchedPlanIsRejected() {
        val store = RecordingCheckpointStore()
        CaptureSessionCoordinator.arm(
            "session-3",
            plan,
            start.minusSeconds(10),
            store,
        )
        val changedPlan = plan.copy(endsAtUtc = start.plusSeconds(2))

        assertTrue(
            CaptureSessionCoordinator.restore(changedPlan, store) is
                CaptureSessionRestoreResult.Rejected,
        )
    }

    private class RecordingCheckpointStore(
        private var readResult: CheckpointReadResult = CheckpointReadResult.Missing,
    ) : CaptureCheckpointStore {
        val writes = mutableListOf<CaptureSessionCheckpoint>()

        override fun write(checkpoint: CaptureSessionCheckpoint) {
            writes += checkpoint
            readResult = CheckpointReadResult.Loaded(checkpoint)
        }

        override fun read(): CheckpointReadResult = readResult

        override fun clear(): Boolean {
            readResult = CheckpointReadResult.Missing
            return true
        }
    }
}
