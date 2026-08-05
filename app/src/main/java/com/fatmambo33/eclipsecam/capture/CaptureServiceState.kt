package com.fatmambo33.eclipsecam.capture

enum class CaptureServiceCommand { START, PAUSE, STOP }

enum class CaptureServiceState { IDLE, RUNNING, PAUSED, STOPPED }

object CaptureServiceStateReducer {
    fun reduce(
        current: CaptureServiceState,
        command: CaptureServiceCommand,
    ): CaptureServiceState = when (command) {
        CaptureServiceCommand.START -> when (current) {
            CaptureServiceState.IDLE,
            CaptureServiceState.PAUSED,
            -> CaptureServiceState.RUNNING

            CaptureServiceState.RUNNING -> CaptureServiceState.RUNNING
            CaptureServiceState.STOPPED -> CaptureServiceState.STOPPED
        }

        CaptureServiceCommand.PAUSE -> when (current) {
            CaptureServiceState.RUNNING -> CaptureServiceState.PAUSED
            else -> current
        }

        CaptureServiceCommand.STOP -> CaptureServiceState.STOPPED
    }
}
