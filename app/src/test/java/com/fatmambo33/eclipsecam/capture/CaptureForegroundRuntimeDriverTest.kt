package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.device.health.CaptureReadiness
import com.fatmambo33.eclipsecam.device.health.DeviceHealthDecision
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureForegroundRuntimeDriverTest {
    @Test
    fun startCommandReplacesWakeupWithImmediateTick() {
        val now = Instant.parse("2026-08-12T17:00:00Z")
        val runtime = FakeRuntime(
            commandResult = CaptureRuntimeCommandResult(
                commandResult = CaptureServiceCommandResult.StateChanged(
                    CaptureServiceState.RUNNING,
                    checkpoint(now, CaptureSessionStatus.RUNNING),
                ),
                nextDirective = CaptureTickDirective.RunImmediately,
            ),
        )
        val wakeups = RecordingWakeups()
        val driver = CaptureForegroundRuntimeDriver(runtime, readyHealth(), wakeups)

        driver.command(CaptureServiceCommand.START, now)

        assertEquals(listOf("immediate"), wakeups.events)
    }

    @Test
    fun tickSamplesFreshHealthAndSchedulesReturnedInstant() {
        val now = Instant.parse("2026-08-12T17:00:00Z")
        val next = now.plusSeconds(4)
        var healthReads = 0
        val health = DeviceHealthDecision(CaptureReadiness.READY, emptySet())
        val runtime = FakeRuntime(
            tickResult = CaptureRuntimeTickResult(
                executionResult = CaptureExecutionResult.Waiting(next),
                serviceState = CaptureServiceState.RUNNING,
                nextDirective = CaptureTickDirective.ScheduleAt(next),
            ),
        )
        val wakeups = RecordingWakeups()
        val driver = CaptureForegroundRuntimeDriver(
            runtime = runtime,
            healthProvider = CaptureRuntimeHealthProvider {
                healthReads += 1
                health
            },
            wakeups = wakeups,
        )

        driver.tick(now)

        assertEquals(1, healthReads)
        assertEquals(health, runtime.lastHealth)
        assertEquals(listOf("at:$next"), wakeups.events)
    }

    @Test
    fun terminalDirectiveCancelsPendingWakeup() {
        val now = Instant.parse("2026-08-12T17:00:00Z")
        val runtime = FakeRuntime(
            tickResult = CaptureRuntimeTickResult(
                executionResult = CaptureExecutionResult.Inactive(CaptureSessionStatus.PAUSED),
                serviceState = CaptureServiceState.PAUSED,
                nextDirective = CaptureTickDirective.Stop,
            ),
        )
        val wakeups = RecordingWakeups()
        val driver = CaptureForegroundRuntimeDriver(runtime, readyHealth(), wakeups)

        driver.tick(now)
        driver.shutdown()

        assertEquals(listOf("cancel", "cancel"), wakeups.events)
    }

    private fun readyHealth(): CaptureRuntimeHealthProvider = CaptureRuntimeHealthProvider {
        DeviceHealthDecision(CaptureReadiness.READY, emptySet())
    }

    private fun checkpoint(
        now: Instant,
        status: CaptureSessionStatus,
    ): CaptureSessionCheckpoint = CaptureSessionCheckpoint(
        sessionId = "session",
        planStartsAtUtc = now,
        planEndsAtUtc = now.plusSeconds(10),
        nextInstructionIndex = 0,
        capturedCount = 0,
        skippedCount = 0,
        status = status,
        updatedAtUtc = now,
    )

    private class FakeRuntime(
        private val commandResult: CaptureRuntimeCommandResult? = null,
        private val tickResult: CaptureRuntimeTickResult? = null,
    ) : CaptureRuntimePort {
        override val state: CaptureServiceState
            get() = tickResult?.serviceState
                ?: commandResult?.commandResult?.let {
                    when (it) {
                        is CaptureServiceCommandResult.StateChanged -> it.serviceState
                        is CaptureServiceCommandResult.Unchanged -> it.serviceState
                    }
                }
                ?: CaptureServiceState.IDLE

        var lastHealth: DeviceHealthDecision? = null
            private set

        override fun command(
            command: CaptureServiceCommand,
            nowUtc: Instant,
        ): CaptureRuntimeCommandResult = requireNotNull(commandResult)

        override fun tick(
            nowUtc: Instant,
            health: DeviceHealthDecision,
        ): CaptureRuntimeTickResult {
            lastHealth = health
            return requireNotNull(tickResult)
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
