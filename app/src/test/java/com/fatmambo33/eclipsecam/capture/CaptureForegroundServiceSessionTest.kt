package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.device.health.CaptureReadiness
import com.fatmambo33.eclipsecam.device.health.DeviceHealthDecision
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureForegroundServiceSessionTest {
    @Test
    fun startSchedulesWakeupThatCapturesAndCompletesRecoveredSession() {
        val captureAt = Instant.parse("2026-08-12T17:00:00Z")
        val recovery = pausedRecovery(captureAt)
        val scheduler = FakeScheduler()
        var captures = 0
        val session = CaptureForegroundServiceSession(
            recovery = recovery,
            instructionExecutor = CaptureInstructionExecutor {
                captures += 1
                CameraCaptureResult.Captured
            },
            healthProvider = readyHealth(),
            nowUtc = { captureAt },
            scheduler = scheduler,
        )

        session.command(CaptureServiceCommand.START)
        assertEquals(CaptureServiceState.RUNNING, session.state)
        assertEquals(Duration.ZERO, scheduler.tasks.single().delay)

        scheduler.tasks.single().run()

        assertEquals(1, captures)
        assertEquals(CaptureServiceState.STOPPED, session.state)
        assertEquals(CaptureSessionStatus.COMPLETED, recovery.coordinator.snapshot().status)
    }

    @Test
    fun pauseInvalidatesAlreadyQueuedWakeupBeforeCameraExecution() {
        val captureAt = Instant.parse("2026-08-12T17:00:00Z")
        val scheduler = FakeScheduler()
        var captures = 0
        val session = CaptureForegroundServiceSession(
            recovery = pausedRecovery(captureAt),
            instructionExecutor = CaptureInstructionExecutor {
                captures += 1
                CameraCaptureResult.Captured
            },
            healthProvider = readyHealth(),
            nowUtc = { captureAt },
            scheduler = scheduler,
        )

        session.command(CaptureServiceCommand.START)
        session.command(CaptureServiceCommand.PAUSE)
        scheduler.tasks.single().run()

        assertTrue(scheduler.tasks.single().cancelled)
        assertEquals(0, captures)
        assertEquals(CaptureServiceState.PAUSED, session.state)
    }

    @Test
    fun closeCancelsWorkAndRejectsFutureCommandsAndTicks() {
        val captureAt = Instant.parse("2026-08-12T17:00:00Z")
        val scheduler = FakeScheduler()
        val session = CaptureForegroundServiceSession(
            recovery = pausedRecovery(captureAt),
            instructionExecutor = CaptureInstructionExecutor { CameraCaptureResult.Captured },
            healthProvider = readyHealth(),
            nowUtc = { captureAt },
            scheduler = scheduler,
        )

        session.command(CaptureServiceCommand.START)
        session.close()

        assertTrue(scheduler.closed)
        assertTrue(scheduler.tasks.single().cancelled)
        assertNull(session.command(CaptureServiceCommand.START))
        assertNull(session.tick())
    }

    private fun pausedRecovery(captureAt: Instant): CaptureServiceBootstrapResult.Ready {
        val plan = CapturePlan(
            startsAtUtc = captureAt,
            endsAtUtc = captureAt,
            instructions = listOf(
                CaptureInstruction(
                    instantUtc = captureAt,
                    phase = CapturePhase.CONTACT_BURST,
                    exposureStrategy = ExposureStrategy.CONTACT_BRACKET,
                ),
            ),
        )
        val coordinator = CaptureSessionCoordinator.arm(
            sessionId = "session",
            plan = plan,
            nowUtc = captureAt.minusSeconds(60),
            checkpointStore = MemoryCheckpointStore(),
        )
        coordinator.start(captureAt.minusSeconds(45))
        coordinator.pause(captureAt.minusSeconds(30))
        return CaptureServiceBootstrapResult.Ready(
            plan = plan,
            coordinator = coordinator,
            initialState = CaptureServiceState.PAUSED,
        )
    }

    private fun readyHealth() = CaptureRuntimeHealthProvider {
        DeviceHealthDecision(CaptureReadiness.READY, emptySet())
    }

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

    private class FakeScheduler : CaptureWakeupTaskScheduler {
        val tasks = mutableListOf<FakeTask>()
        var closed = false

        override fun schedule(delay: Duration, task: () -> Unit): CaptureWakeupTask =
            FakeTask(delay, task).also(tasks::add)

        override fun close() {
            closed = true
        }
    }

    private class FakeTask(
        val delay: Duration,
        private val callback: () -> Unit,
    ) : CaptureWakeupTask {
        var cancelled = false

        override fun cancel() {
            cancelled = true
        }

        fun run() {
            callback()
        }
    }
}
