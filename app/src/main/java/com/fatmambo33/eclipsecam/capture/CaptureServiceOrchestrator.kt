package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.device.health.DeviceHealthDecision
import java.time.Instant

sealed interface CaptureServiceCommandResult {
    data class StateChanged(
        val serviceState: CaptureServiceState,
        val checkpoint: CaptureSessionCheckpoint,
    ) : CaptureServiceCommandResult

    data class Unchanged(
        val serviceState: CaptureServiceState,
        val checkpoint: CaptureSessionCheckpoint,
    ) : CaptureServiceCommandResult
}

/**
 * Coordinates foreground-service commands with the durable capture session and execution engine.
 *
 * Android lifecycle code can delegate to this class without duplicating transition rules. Stopping
 * a running service persists a paused checkpoint so a later service instance can recover safely.
 */
class CaptureServiceOrchestrator(
    private val coordinator: CaptureSessionCoordinator,
    private val executionEngine: CaptureExecutionEngine,
    initialState: CaptureServiceState = CaptureServiceState.IDLE,
) {
    var state: CaptureServiceState = initialState
        private set

    fun command(
        command: CaptureServiceCommand,
        nowUtc: Instant,
    ): CaptureServiceCommandResult {
        val previous = state
        val next = CaptureServiceStateReducer.reduce(previous, command)
        val checkpoint = when {
            previous == CaptureServiceState.IDLE && next == CaptureServiceState.RUNNING ->
                coordinator.start(nowUtc)

            previous == CaptureServiceState.PAUSED && next == CaptureServiceState.RUNNING ->
                coordinator.start(nowUtc)

            previous == CaptureServiceState.RUNNING && next == CaptureServiceState.PAUSED ->
                coordinator.pause(nowUtc)

            previous == CaptureServiceState.RUNNING && next == CaptureServiceState.STOPPED ->
                coordinator.pause(nowUtc)

            else -> coordinator.snapshot()
        }
        state = next
        return if (previous == next) {
            CaptureServiceCommandResult.Unchanged(next, checkpoint)
        } else {
            CaptureServiceCommandResult.StateChanged(next, checkpoint)
        }
    }

    fun tick(
        nowUtc: Instant,
        health: DeviceHealthDecision,
    ): CaptureExecutionResult {
        if (state != CaptureServiceState.RUNNING) {
            return CaptureExecutionResult.Inactive(coordinator.snapshot().status)
        }

        val result = executionEngine.tick(nowUtc, health)
        state = when (result) {
            is CaptureExecutionResult.Paused -> CaptureServiceState.PAUSED
            is CaptureExecutionResult.Failed,
            is CaptureExecutionResult.Finished,
            -> CaptureServiceState.STOPPED

            is CaptureExecutionResult.Captured,
            is CaptureExecutionResult.SkippedLate,
            is CaptureExecutionResult.Waiting,
            is CaptureExecutionResult.Inactive,
            -> state
        }
        return result
    }
}
