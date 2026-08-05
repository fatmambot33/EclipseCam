package com.fatmambo33.eclipsecam.capture

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureServiceCommandControllerTest {
    private val start = Instant.parse("2026-08-12T17:00:00Z")
    private val later = start.plusSeconds(1)
    private val plan = CapturePlan(
        startsAtUtc = start,
        endsAtUtc = start.plusSeconds(60),
        instructions = listOf(
            CaptureInstruction(
                instantUtc = start.plusSeconds(30),
                phase = CapturePhase.PARTIAL,
                exposureStrategy = ExposureStrategy.FILTERED_PARTIAL,
            ),
        ),
    )

    @Test
    fun recoveredRunningSessionIsPersistedPaused() {
        val store = InMemoryCheckpointStore()
        val coordinator = CaptureSessionCoordinator.arm("session", plan, start, store)
        coordinator.start(start)
        val controller = CaptureServiceCommandController(coordinator, CaptureServiceState.PAUSED)

        val checkpoint = controller.normalizeRecoveredSession(later)

        assertEquals(CaptureSessionStatus.PAUSED, checkpoint.status)
        assertEquals(CaptureSessionStatus.PAUSED, store.checkpoint?.status)
        assertEquals(CaptureServiceState.PAUSED, controller.state)
    }

    @Test
    fun resumeAndPauseCommandsPersistBeforeChangingServiceState() {
        val store = InMemoryCheckpointStore()
        val coordinator = CaptureSessionCoordinator.arm("session", plan, start, store)
        val controller = CaptureServiceCommandController(coordinator, CaptureServiceState.PAUSED)

        val running = controller.command(CaptureServiceCommand.START, later)
        val paused = controller.command(CaptureServiceCommand.PAUSE, later.plusSeconds(1))

        assertEquals(CaptureSessionStatus.RUNNING, running.status)
        assertEquals(CaptureSessionStatus.PAUSED, paused.status)
        assertEquals(CaptureSessionStatus.PAUSED, store.checkpoint?.status)
        assertEquals(CaptureServiceState.PAUSED, controller.state)
    }

    @Test
    fun stopFromRunningPersistsRecoverablePause() {
        val store = InMemoryCheckpointStore()
        val coordinator = CaptureSessionCoordinator.arm("session", plan, start, store)
        val controller = CaptureServiceCommandController(coordinator, CaptureServiceState.PAUSED)
        controller.command(CaptureServiceCommand.START, later)

        val checkpoint = controller.command(CaptureServiceCommand.STOP, later.plusSeconds(1))

        assertEquals(CaptureSessionStatus.PAUSED, checkpoint.status)
        assertEquals(CaptureSessionStatus.PAUSED, store.checkpoint?.status)
        assertEquals(CaptureServiceState.STOPPED, controller.state)
    }

    @Test
    fun repeatedPauseDoesNotApplyInvalidSessionTransition() {
        val store = InMemoryCheckpointStore()
        val coordinator = CaptureSessionCoordinator.arm("session", plan, start, store)
        val controller = CaptureServiceCommandController(coordinator, CaptureServiceState.PAUSED)

        val checkpoint = controller.command(CaptureServiceCommand.PAUSE, later)

        assertEquals(CaptureSessionStatus.ARMED, checkpoint.status)
        assertEquals(CaptureServiceState.PAUSED, controller.state)
    }

    private class InMemoryCheckpointStore : CaptureCheckpointStore {
        var checkpoint: CaptureSessionCheckpoint? = null
            private set

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
