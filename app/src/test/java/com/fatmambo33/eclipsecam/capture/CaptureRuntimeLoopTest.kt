package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.device.health.CaptureReadiness
import com.fatmambo33.eclipsecam.device.health.DeviceHealthDecision
import com.fatmambo33.eclipsecam.device.health.DeviceHealthReason
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureRuntimeLoopTest {
    private val start = Instant.parse("2026-08-12T17:45:00Z")
    private val ready = DeviceHealthDecision(CaptureReadiness.READY, emptySet())

    @Test
    fun startRequestsImmediateTickAndFutureInstructionIsScheduled() {
        val loop = loop(instructionAt = start.plusSeconds(5))

        val command = loop.command(CaptureServiceCommand.START, start)
        val tick = loop.tick(start, ready)

        assertEquals(CaptureTickDirective.RunImmediately, command.nextDirective)
        assertEquals(
            CaptureTickDirective.ScheduleAt(start.plusSeconds(5)),
            tick.nextDirective,
        )
        assertEquals(CaptureServiceState.RUNNING, tick.serviceState)
    }

    @Test
    fun finalCaptureStopsRuntimeLoop() {
        val loop = loop(instructionAt = start)
        loop.command(CaptureServiceCommand.START, start)

        val tick = loop.tick(start, ready)

        assertTrue(tick.executionResult is CaptureExecutionResult.Captured)
        assertEquals(CaptureTickDirective.Stop, tick.nextDirective)
        assertEquals(CaptureServiceState.STOPPED, tick.serviceState)
    }

    @Test
    fun pauseCommandCancelsFutureTicks() {
        val loop = loop(instructionAt = start.plusSeconds(5))
        loop.command(CaptureServiceCommand.START, start)

        val paused = loop.command(CaptureServiceCommand.PAUSE, start.plusSeconds(1))

        assertEquals(CaptureTickDirective.Stop, paused.nextDirective)
        assertEquals(CaptureServiceState.PAUSED, loop.state)
    }

    @Test
    fun blockedHealthPersistsPauseAndStopsScheduling() {
        val loop = loop(instructionAt = start)
        loop.command(CaptureServiceCommand.START, start)
        val blocked = DeviceHealthDecision(
            CaptureReadiness.BLOCKED,
            setOf(DeviceHealthReason.THERMAL_UNSAFE),
        )

        val tick = loop.tick(start.plusSeconds(1), blocked)

        assertTrue(tick.executionResult is CaptureExecutionResult.Paused)
        assertEquals(CaptureTickDirective.Stop, tick.nextDirective)
        assertEquals(CaptureServiceState.PAUSED, tick.serviceState)
    }

    private fun loop(instructionAt: Instant): CaptureRuntimeLoop {
        val plan = CapturePlan(
            startsAtUtc = start,
            endsAtUtc = start.plusSeconds(60),
            instructions = listOf(
                CaptureInstruction(
                    instantUtc = instructionAt,
                    phase = CapturePhase.TOTALITY,
                    exposureStrategy = ExposureStrategy.TOTALITY_BRACKET,
                ),
            ),
        )
        val store = InMemoryCheckpointStore()
        val coordinator = CaptureSessionCoordinator.arm("session", plan, start, store)
        val engine = CaptureExecutionEngine(
            plan = plan,
            coordinator = coordinator,
            executor = CaptureInstructionExecutor { CameraCaptureResult.Captured },
        )
        return CaptureRuntimeLoop(
            CaptureServiceOrchestrator(
                coordinator = coordinator,
                executionEngine = engine,
            ),
        )
    }

    private class InMemoryCheckpointStore : CaptureCheckpointStore {
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
