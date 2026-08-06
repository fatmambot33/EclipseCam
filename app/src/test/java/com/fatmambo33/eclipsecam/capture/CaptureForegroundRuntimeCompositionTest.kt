package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.device.health.CaptureReadiness
import com.fatmambo33.eclipsecam.device.health.DeviceHealthDecision
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureForegroundRuntimeCompositionTest {
    @Test
    fun recoveredSessionResumesAndCapturesThroughComposedRuntime() {
        val captureAt = Instant.parse("2026-08-12T17:00:00Z")
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
        val store = MemoryCheckpointStore()
        val coordinator = CaptureSessionCoordinator.arm(
            sessionId = "session",
            plan = plan,
            nowUtc = captureAt.minusSeconds(60),
            checkpointStore = store,
        )
        coordinator.pause(captureAt.minusSeconds(30))
        val wakeups = RecordingWakeups()
        var captures = 0
        val driver = CaptureForegroundRuntimeComposition.create(
            recovery = CaptureServiceBootstrapResult.Ready(
                plan = plan,
                coordinator = coordinator,
                initialState = CaptureServiceState.PAUSED,
            ),
            instructionExecutor = CaptureInstructionExecutor {
                captures += 1
                CameraCaptureResult.Captured
            },
            healthProvider = CaptureRuntimeHealthProvider {
                DeviceHealthDecision(CaptureReadiness.READY, emptySet())
            },
            wakeups = wakeups,
        )

        driver.command(CaptureServiceCommand.START, captureAt.minusSeconds(1))
        val result = driver.tick(captureAt)

        assertEquals(1, captures)
        assertTrue(result.executionResult is CaptureExecutionResult.Captured)
        assertEquals(CaptureServiceState.STOPPED, result.serviceState)
        assertEquals(listOf("immediate", "cancel"), wakeups.events)
        assertEquals(CaptureSessionStatus.COMPLETED, store.checkpoint?.status)
    }

    @Test
    fun blockedHealthPausesBeforeCameraExecution() {
        val captureAt = Instant.parse("2026-08-12T17:00:00Z")
        val plan = CapturePlan(
            startsAtUtc = captureAt,
            endsAtUtc = captureAt,
            instructions = listOf(
                CaptureInstruction(captureAt, CapturePhase.PARTIAL, ExposureStrategy.FILTERED_PARTIAL),
            ),
        )
        val store = MemoryCheckpointStore()
        val coordinator = CaptureSessionCoordinator.arm("session", plan, captureAt.minusSeconds(2), store)
        val wakeups = RecordingWakeups()
        var captures = 0
        val driver = CaptureForegroundRuntimeComposition.create(
            recovery = CaptureServiceBootstrapResult.Ready(plan, coordinator, CaptureServiceState.PAUSED),
            instructionExecutor = CaptureInstructionExecutor {
                captures += 1
                CameraCaptureResult.Captured
            },
            healthProvider = CaptureRuntimeHealthProvider {
                DeviceHealthDecision(CaptureReadiness.BLOCKED, emptySet())
            },
            wakeups = wakeups,
        )

        driver.command(CaptureServiceCommand.START, captureAt.minusSeconds(1))
        val result = driver.tick(captureAt)

        assertEquals(0, captures)
        assertTrue(result.executionResult is CaptureExecutionResult.Paused)
        assertEquals(CaptureServiceState.PAUSED, result.serviceState)
        assertEquals(listOf("immediate", "cancel"), wakeups.events)
    }

    private class MemoryCheckpointStore : CaptureCheckpointStore {
        var checkpoint: CaptureSessionCheckpoint? = null

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

    private class RecordingWakeups : CaptureRuntimeWakeupPort {
        val events = mutableListOf<String>()

        override fun runImmediately() {
            events += "immediate"
        }

        override fun scheduleAt(instantUtc: Instant) {
            events += "at:$instantUtc"
        }

        override fun cancel() {
            events += "cancel"
        }
    }
}
