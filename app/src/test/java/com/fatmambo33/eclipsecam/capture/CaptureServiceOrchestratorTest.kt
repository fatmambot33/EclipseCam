package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.device.health.CaptureReadiness
import com.fatmambo33.eclipsecam.device.health.DeviceHealthDecision
import com.fatmambo33.eclipsecam.device.health.DeviceHealthReason
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureServiceOrchestratorTest {
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
    fun startPauseResumeAndStopPersistSessionTransitions() {
        val fixture = fixture(CameraCaptureResult.Captured)

        fixture.orchestrator.command(CaptureServiceCommand.START, start.minusSeconds(5))
        fixture.orchestrator.command(CaptureServiceCommand.PAUSE, start.minusSeconds(4))
        fixture.orchestrator.command(CaptureServiceCommand.START, start.minusSeconds(3))
        fixture.orchestrator.command(CaptureServiceCommand.STOP, start.minusSeconds(2))

        assertEquals(CaptureServiceState.STOPPED, fixture.orchestrator.state)
        assertEquals(CaptureSessionStatus.PAUSED, fixture.coordinator.snapshot().status)
        assertEquals(5, fixture.store.writes.size)
    }

    @Test
    fun duplicateStartDoesNotRepeatSessionTransition() {
        val fixture = fixture(CameraCaptureResult.Captured)
        fixture.orchestrator.command(CaptureServiceCommand.START, start.minusSeconds(5))

        val result = fixture.orchestrator.command(
            CaptureServiceCommand.START,
            start.minusSeconds(4),
        )

        assertTrue(result is CaptureServiceCommandResult.Unchanged)
        assertEquals(2, fixture.store.writes.size)
    }

    @Test
    fun executionPauseSynchronizesServiceState() {
        val fixture = fixture(CameraCaptureResult.Captured)
        fixture.orchestrator.command(CaptureServiceCommand.START, start.minusSeconds(5))
        val blocked = DeviceHealthDecision(
            CaptureReadiness.BLOCKED,
            setOf(DeviceHealthReason.THERMAL_UNSAFE),
        )

        val result = fixture.orchestrator.tick(start, blocked)

        assertTrue(result is CaptureExecutionResult.Paused)
        assertEquals(CaptureServiceState.PAUSED, fixture.orchestrator.state)
        assertEquals(CaptureSessionStatus.PAUSED, fixture.coordinator.snapshot().status)
    }

    @Test
    fun completedExecutionStopsServiceWithoutLosingCheckpoint() {
        val fixture = fixture(CameraCaptureResult.Captured)
        fixture.orchestrator.command(CaptureServiceCommand.START, start.minusSeconds(5))

        fixture.orchestrator.tick(start, ready)
        val completed = fixture.orchestrator.tick(start.plusSeconds(1), ready)

        assertTrue(completed is CaptureExecutionResult.Captured)
        assertEquals(CaptureServiceState.STOPPED, fixture.orchestrator.state)
        assertEquals(CaptureSessionStatus.COMPLETED, fixture.coordinator.snapshot().status)
        assertEquals(2, fixture.coordinator.snapshot().capturedCount)
    }

    @Test
    fun pausedOrStoppedServiceNeverInvokesCamera() {
        var calls = 0
        val fixture = fixture {
            calls += 1
            CameraCaptureResult.Captured
        }

        assertTrue(fixture.orchestrator.tick(start, ready) is CaptureExecutionResult.Inactive)
        fixture.orchestrator.command(CaptureServiceCommand.STOP, start.minusSeconds(1))
        assertTrue(fixture.orchestrator.tick(start, ready) is CaptureExecutionResult.Inactive)
        assertEquals(0, calls)
    }

    private fun fixture(result: CameraCaptureResult): Fixture = fixture { result }

    private fun fixture(executor: CaptureInstructionExecutor): Fixture {
        val store = RecordingStore()
        val coordinator = CaptureSessionCoordinator.arm(
            sessionId = "service-session",
            plan = plan,
            nowUtc = start.minusSeconds(10),
            checkpointStore = store,
        )
        val engine = CaptureExecutionEngine(plan, coordinator, executor)
        return Fixture(
            coordinator = coordinator,
            store = store,
            orchestrator = CaptureServiceOrchestrator(coordinator, engine),
        )
    }

    private data class Fixture(
        val coordinator: CaptureSessionCoordinator,
        val store: RecordingStore,
        val orchestrator: CaptureServiceOrchestrator,
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
