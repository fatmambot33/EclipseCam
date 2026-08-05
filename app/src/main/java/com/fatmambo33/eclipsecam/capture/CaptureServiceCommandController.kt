package com.fatmambo33.eclipsecam.capture

import java.time.Instant

/**
 * Keeps foreground-service commands synchronized with the durable capture checkpoint.
 *
 * Android service state must never advance independently from the recoverable session. A recovered
 * running checkpoint is immediately persisted as paused, and explicit pause, resume, and stop
 * commands update durable state before the service exposes the resulting lifecycle state.
 */
class CaptureServiceCommandController(
    private val coordinator: CaptureSessionCoordinator,
    initialState: CaptureServiceState,
) {
    var state: CaptureServiceState = initialState
        private set

    fun normalizeRecoveredSession(nowUtc: Instant): CaptureSessionCheckpoint {
        val checkpoint = coordinator.snapshot()
        if (state == CaptureServiceState.PAUSED && checkpoint.status == CaptureSessionStatus.RUNNING) {
            return coordinator.pause(nowUtc)
        }
        return checkpoint
    }

    fun command(
        command: CaptureServiceCommand,
        nowUtc: Instant,
    ): CaptureSessionCheckpoint {
        val previous = state
        val next = CaptureServiceStateReducer.reduce(previous, command)
        val checkpoint = when {
            previous == CaptureServiceState.PAUSED && next == CaptureServiceState.RUNNING ->
                when (coordinator.snapshot().status) {
                    CaptureSessionStatus.ARMED,
                    CaptureSessionStatus.PAUSED,
                    -> coordinator.start(nowUtc)

                    CaptureSessionStatus.RUNNING -> coordinator.snapshot()
                    CaptureSessionStatus.COMPLETED,
                    CaptureSessionStatus.FAILED,
                    -> coordinator.snapshot()
                }

            previous == CaptureServiceState.RUNNING && next == CaptureServiceState.PAUSED ->
                if (coordinator.snapshot().status == CaptureSessionStatus.RUNNING) {
                    coordinator.pause(nowUtc)
                } else {
                    coordinator.snapshot()
                }

            previous == CaptureServiceState.RUNNING && next == CaptureServiceState.STOPPED ->
                if (coordinator.snapshot().status == CaptureSessionStatus.RUNNING) {
                    coordinator.pause(nowUtc)
                } else {
                    coordinator.snapshot()
                }

            else -> coordinator.snapshot()
        }
        state = next
        return checkpoint
    }
}
