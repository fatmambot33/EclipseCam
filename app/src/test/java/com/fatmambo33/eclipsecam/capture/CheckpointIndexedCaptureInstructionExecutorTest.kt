package com.fatmambo33.eclipsecam.capture

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckpointIndexedCaptureInstructionExecutorTest {
    @Test
    fun forwardsDurableCheckpointIndexToSuspendingCameraPipeline() {
        val first = instruction("2026-08-12T17:00:00Z")
        val second = instruction("2026-08-12T17:00:01Z")
        val plan = plan(first, second)
        val coordinator = coordinator(plan)
        coordinator.start(Instant.parse("2026-08-12T16:59:00Z"))
        coordinator.record(CaptureStepOutcome.CAPTURED, Instant.parse("2026-08-12T17:00:00Z"))
        var receivedIndex = -1
        var receivedInstruction: CaptureInstruction? = null
        val executor = CheckpointIndexedCaptureInstructionExecutor(
            plan = plan,
            coordinator = coordinator,
            indexedCapture = IndexedCameraCapturePort { index, value ->
                receivedIndex = index
                receivedInstruction = value
                CameraCaptureResult.Captured
            },
        )

        val result = executor.capture(second)

        assertEquals(CameraCaptureResult.Captured, result)
        assertEquals(1, receivedIndex)
        assertEquals(second, receivedInstruction)
    }

    @Test
    fun failsClosedWhenRuntimeInstructionDoesNotMatchDurableIndex() {
        val expected = instruction("2026-08-12T17:00:00Z")
        val unexpected = instruction("2026-08-12T17:00:01Z")
        val plan = plan(expected)
        val coordinator = coordinator(plan)
        var cameraCalls = 0
        val executor = CheckpointIndexedCaptureInstructionExecutor(
            plan = plan,
            coordinator = coordinator,
            indexedCapture = IndexedCameraCapturePort { _, _ ->
                cameraCalls += 1
                CameraCaptureResult.Captured
            },
        )

        val result = executor.capture(unexpected)

        assertTrue(result is CameraCaptureResult.FatalError)
        assertEquals(0, cameraCalls)
    }

    @Test
    fun failsClosedWhenCheckpointIsBeyondPlan() {
        val only = instruction("2026-08-12T17:00:00Z")
        val plan = plan(only)
        val coordinator = coordinator(plan)
        coordinator.start(Instant.parse("2026-08-12T16:59:00Z"))
        coordinator.record(CaptureStepOutcome.CAPTURED, Instant.parse("2026-08-12T17:00:00Z"))
        var cameraCalls = 0
        val executor = CheckpointIndexedCaptureInstructionExecutor(
            plan = plan,
            coordinator = coordinator,
            indexedCapture = IndexedCameraCapturePort { _, _ ->
                cameraCalls += 1
                CameraCaptureResult.Captured
            },
        )

        val result = executor.capture(only)

        assertTrue(result is CameraCaptureResult.FatalError)
        assertEquals(0, cameraCalls)
    }

    private fun instruction(instant: String) = CaptureInstruction(
        instantUtc = Instant.parse(instant),
        phase = CapturePhase.CONTACT_BURST,
        exposureStrategy = ExposureStrategy.CONTACT_BRACKET,
    )

    private fun plan(vararg instructions: CaptureInstruction) = CapturePlan(
        startsAtUtc = instructions.first().instantUtc,
        endsAtUtc = instructions.last().instantUtc,
        instructions = instructions.toList(),
    )

    private fun coordinator(plan: CapturePlan) = CaptureSessionCoordinator.arm(
        sessionId = "indexed-adapter-test",
        plan = plan,
        nowUtc = Instant.parse("2026-08-12T16:58:00Z"),
        checkpointStore = MemoryCheckpointStore(),
    )

    private class MemoryCheckpointStore : CaptureCheckpointStore {
        private var checkpoint: CaptureSessionCheckpoint? = null

        override fun write(checkpoint: CaptureSessionCheckpoint) {
            this.checkpoint = checkpoint
        }

        override fun read(): CheckpointReadResult = checkpoint
            ?.let(CheckpointReadResult::Loaded)
            ?: CheckpointReadResult.Missing

        override fun clear(): Boolean {
            checkpoint = null
            return true
        }
    }
}
