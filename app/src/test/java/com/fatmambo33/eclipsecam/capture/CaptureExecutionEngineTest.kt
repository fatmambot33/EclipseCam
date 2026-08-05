package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.device.health.CaptureReadiness
import com.fatmambo33.eclipsecam.device.health.DeviceHealthDecision
import com.fatmambo33.eclipsecam.device.health.DeviceHealthReason
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureExecutionEngineTest {
    private val start = Instant.parse("2026-08-12T17:00:00Z")
    private val plan = CapturePlan(
        startsAtUtc = start,
        endsAtUtc = start.plusSeconds(1),
        instructions = listOf(
            CaptureInstruction(start, CapturePhase.PARTIAL, ExposureStrategy.FILTERED_PARTIAL),
            CaptureInstruction(start.plusSeconds(1), CapturePhase.TOTALITY, ExposureStrategy.TOTALITY_BRACKET),
        ),
    )
    private val ready = DeviceHealthDecision(CaptureReadiness.READY, emptySet())

    @Test
    fun waitsWithoutMutatingBeforeInstructionIsDue() {
        val fixture = fixture(CameraCaptureResult.Captured)

        val result = fixture.engine.tick(start.minusSeconds(1), ready)

        assertTrue(result is CaptureExecutionResult.Waiting)
        assertEquals(2, fixture.store.writes.size)
    }

    @Test
    fun capturesOneInstructionAndPersistsProgress() {
        val fixture = fixture(CameraCaptureResult.Captured)

        val result = fixture.engine.tick(start, ready) as CaptureExecutionResult.Captured

        assertEquals(1, result.checkpoint.capturedCount)
        assertEquals(1, result.checkpoint.nextInstructionIndex)
        assertEquals(3, fixture.store.writes.size)
    }

    @Test
    fun lateInstructionIsSkippedWithoutCallingCamera() {
        var calls = 0
        val fixture = fixture {
            calls += 1
            CameraCaptureResult.Captured
        }

        val result = fixture.engine.tick(start.plusSeconds(11), ready) as CaptureExecutionResult.SkippedLate

        assertEquals(0, calls)
        assertEquals(1, result.skippedInstructionCount)
        assertEquals(1, result.checkpoint.skippedCount)
        assertEquals(1, result.checkpoint.nextInstructionIndex)
        assertEquals(CaptureSessionStatus.RUNNING, result.checkpoint.status)
        assertEquals(3, fixture.store.writes.size)
    }

    @Test
    fun allIrrecoverablyLateInstructionsAreSkippedAtomically() {
        var calls = 0
        val fixture = fixture {
            calls += 1
            CameraCaptureResult.Captured
        }

        val result = fixture.engine.tick(start.plusSeconds(12), ready) as CaptureExecutionResult.SkippedLate

        assertEquals(0, calls)
        assertEquals(2, result.skippedInstructionCount)
        assertEquals(2, result.checkpoint.skippedCount)
        assertEquals(2, result.checkpoint.nextInstructionIndex)
        assertEquals(CaptureSessionStatus.COMPLETED, result.checkpoint.status)
        assertEquals(3, fixture.store.writes.size)
    }

    @Test
    fun latenessToleranceBoundaryRemainsEligibleForCapture() {
        var calls = 0
        val fixture = fixture {
            calls += 1
            CameraCaptureResult.Captured
        }

        val skipped = fixture.engine.tick(start.plusSeconds(11), ready) as CaptureExecutionResult.SkippedLate
        val captured = fixture.engine.tick(start.plusSeconds(11), ready) as CaptureExecutionResult.Captured

        assertEquals(1, skipped.skippedInstructionCount)
        assertEquals(1, calls)
        assertEquals(1, captured.checkpoint.capturedCount)
        assertEquals(CaptureSessionStatus.COMPLETED, captured.checkpoint.status)
    }

    @Test
    fun blockingHealthPausesBeforeCameraExecution() {
        var calls = 0
        val fixture = fixture {
            calls += 1
            CameraCaptureResult.Captured
        }
        val blocked = DeviceHealthDecision(
            CaptureReadiness.BLOCKED,
            setOf(DeviceHealthReason.THERMAL_UNSAFE),
        )

        val result = fixture.engine.tick(start, blocked) as CaptureExecutionResult.Paused

        assertEquals(0, calls)
        assertEquals(CaptureSessionStatus.PAUSED, result.checkpoint.status)
        assertTrue(result.reason.contains("THERMAL_UNSAFE"))
    }

    @Test
    fun recoverableCameraErrorPausesAndFatalErrorFails() {
        val recoverable = fixture(CameraCaptureResult.RecoverableError("Camera busy"))
        val paused = recoverable.engine.tick(start, ready) as CaptureExecutionResult.Paused
        assertEquals(CaptureSessionStatus.PAUSED, paused.checkpoint.status)

        val fatal = fixture(CameraCaptureResult.FatalError("Camera disconnected"))
        val failed = fatal.engine.tick(start, ready) as CaptureExecutionResult.Failed
        assertEquals(CaptureSessionStatus.FAILED, failed.checkpoint.status)
        assertEquals("Camera disconnected", failed.checkpoint.failureReason)
    }

    @Test
    fun completedSessionReturnsFinishedWithoutCameraCall() {
        var calls = 0
        val fixture = fixture {
            calls += 1
            CameraCaptureResult.Captured
        }
        fixture.engine.tick(start, ready)
        fixture.engine.tick(start.plusSeconds(1), ready)

        val result = fixture.engine.tick(start.plusSeconds(2), ready)

        assertTrue(result is CaptureExecutionResult.Finished)
        assertEquals(2, calls)
    }

    private fun fixture(result: CameraCaptureResult): Fixture = fixture { result }

    private fun fixture(executor: CaptureInstructionExecutor): Fixture {
        val store = RecordingStore()
        val coordinator = CaptureSessionCoordinator.arm(
            sessionId = "session",
            plan = plan,
            nowUtc = start.minusSeconds(10),
            checkpointStore = store,
        )
        coordinator.start(start.minusSeconds(5))
        return Fixture(
            coordinator = coordinator,
            store = store,
            engine = CaptureExecutionEngine(
                plan = plan,
                coordinator = coordinator,
                executor = executor,
                maximumLateness = Duration.ofSeconds(10),
            ),
        )
    }

    private data class Fixture(
        val coordinator: CaptureSessionCoordinator,
        val store: RecordingStore,
        val engine: CaptureExecutionEngine,
    )

    private class RecordingStore : CaptureCheckpointStore {
        val writes = mutableListOf<CaptureSessionCheckpoint>()
        private var checkpoint: CaptureSessionCheckpoint? = null

        override fun write(checkpoint: CaptureSessionCheckpoint) {
            writes += checkpoint
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
