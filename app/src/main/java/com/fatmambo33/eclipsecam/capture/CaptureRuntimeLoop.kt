package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.device.health.DeviceHealthDecision
import java.time.Instant

data class CaptureRuntimeCommandResult(
    val commandResult: CaptureServiceCommandResult,
    val nextDirective: CaptureTickDirective,
)

data class CaptureRuntimeTickResult(
    val executionResult: CaptureExecutionResult,
    val serviceState: CaptureServiceState,
    val nextDirective: CaptureTickDirective,
)

/**
 * Joins durable service commands, capture execution, and wake-up scheduling into one runtime loop.
 *
 * Android lifecycle code can apply the returned directive without duplicating state rules. Starting
 * or resuming requests an immediate tick. Pausing, stopping, terminal execution, and safeguard
 * pauses always cancel future work. Each tick executes at most one engine step before deciding the
 * next wake-up.
 */
class CaptureRuntimeLoop(
    private val orchestrator: CaptureServiceOrchestrator,
    private val scheduler: CaptureTickScheduler = CaptureTickScheduler(),
) {
    val state: CaptureServiceState
        get() = orchestrator.state

    fun command(
        command: CaptureServiceCommand,
        nowUtc: Instant,
    ): CaptureRuntimeCommandResult {
        val result = orchestrator.command(command, nowUtc)
        return CaptureRuntimeCommandResult(
            commandResult = result,
            nextDirective = if (orchestrator.state == CaptureServiceState.RUNNING) {
                CaptureTickDirective.RunImmediately
            } else {
                CaptureTickDirective.Stop
            },
        )
    }

    fun tick(
        nowUtc: Instant,
        health: DeviceHealthDecision,
    ): CaptureRuntimeTickResult {
        val result = orchestrator.tick(nowUtc, health)
        return CaptureRuntimeTickResult(
            executionResult = result,
            serviceState = orchestrator.state,
            nextDirective = scheduler.next(result, nowUtc),
        )
    }
}
