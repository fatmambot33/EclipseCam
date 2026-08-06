package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.device.health.CaptureReadiness
import com.fatmambo33.eclipsecam.device.health.DeviceHealthDecision
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CaptureForegroundServiceSessionFactoryTest {
    @Test
    fun composesRecoveredPlanCheckpointCameraHealthAndScheduler() {
        val captureAt = Instant.parse("2026-08-12T17:00:00Z")
        val recovery = pausedRecovery(captureAt)
        val scheduler = FakeScheduler()
        var factoryRecovery: CaptureServiceBootstrapResult.Ready? = null
        var receivedIndex = -1
        var captures = 0
        val factory = CaptureForegroundServiceSessionFactory(
            indexedCameraFactory = CaptureIndexedCameraFactory { value ->
                factoryRecovery = value
                IndexedCameraCapturePort { index, _ ->
                    receivedIndex = index
                    captures += 1
                    CameraCaptureResult.Captured
                }
            },
            healthProviderFactory = CaptureRuntimeHealthProviderFactory {
                CaptureRuntimeHealthProvider {
                    DeviceHealthDecision(CaptureReadiness.READY, emptySet())
                }
            },
            schedulerFactory = CaptureWakeupSchedulerFactory { scheduler },
            nowUtc = { captureAt },
        )

        val session = factory.create(recovery)
        session.command(CaptureServiceCommand.START)
        scheduler.tasks.single().run()

        assertSame(recovery, factoryRecovery)
        assertEquals(0, receivedIndex)
        assertEquals(1, captures)
        assertEquals(CaptureServiceState.STOPPED, session.state)
        assertEquals(CaptureSessionStatus.COMPLETED, recovery.coordinator.snapshot().status)
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
        return CaptureServiceBootstrapResult.Ready(plan, coordinator, CaptureServiceState.PAUSED)
    }

    private class MemoryCheckpointStore : CaptureCheckpointStore {
        private var checkpoint: CaptureSessionCheckpoint? = null
        override fun write(checkpoint: CaptureSessionCheckpoint) { this.checkpoint = checkpoint }
        override fun read(): CheckpointReadResult = checkpoint
            ?.let(CheckpointReadResult::Loaded)
            ?: CheckpointReadResult.Missing
        override fun clear(): Boolean { checkpoint = null; return true }
    }

    private class FakeScheduler : CaptureWakeupTaskScheduler {
        val tasks = mutableListOf<FakeTask>()
        override fun schedule(delay: Duration, task: () -> Unit): CaptureWakeupTask =
            FakeTask(delay, task).also(tasks::add)
        override fun close() = Unit
    }

    private class FakeTask(
        val delay: Duration,
        private val callback: () -> Unit,
    ) : CaptureWakeupTask {
        override fun cancel() = Unit
        fun run() = callback()
    }
}
