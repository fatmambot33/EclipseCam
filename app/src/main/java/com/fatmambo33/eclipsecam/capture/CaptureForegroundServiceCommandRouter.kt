package com.fatmambo33.eclipsecam.capture

enum class CaptureForegroundServiceRequest {
    STICKY_RESTART,
    START,
    PAUSE,
    STOP,
}

sealed interface CaptureForegroundServiceRouteResult {
    data class Active(val state: CaptureServiceState) : CaptureForegroundServiceRouteResult
    data object Stop : CaptureForegroundServiceRouteResult
}

/** Routes Android service requests through the recovered runtime host without a command-only path. */
class CaptureForegroundServiceCommandRouter(
    private val host: CaptureForegroundServiceRuntimeHost,
) {
    fun initialize(): CaptureForegroundServiceRouteResult = when (val result = host.start()) {
        is CaptureRuntimeHostStartResult.Ready -> activeOrStop(result.state)
        CaptureRuntimeHostStartResult.Missing,
        is CaptureRuntimeHostStartResult.Rejected,
        is CaptureRuntimeHostStartResult.Failed,
        -> CaptureForegroundServiceRouteResult.Stop
    }

    fun route(request: CaptureForegroundServiceRequest): CaptureForegroundServiceRouteResult {
        if (request == CaptureForegroundServiceRequest.STOP) {
            host.command(CaptureServiceCommand.STOP)
            return CaptureForegroundServiceRouteResult.Stop
        }
        if (request == CaptureForegroundServiceRequest.STICKY_RESTART) {
            return activeOrStop(host.state)
        }

        val command = when (request) {
            CaptureForegroundServiceRequest.START -> CaptureServiceCommand.START
            CaptureForegroundServiceRequest.PAUSE -> CaptureServiceCommand.PAUSE
            CaptureForegroundServiceRequest.STICKY_RESTART,
            CaptureForegroundServiceRequest.STOP,
            -> error("Request handled before command mapping.")
        }
        host.command(command)
        return activeOrStop(host.state)
    }

    private fun activeOrStop(state: CaptureServiceState): CaptureForegroundServiceRouteResult =
        when (state) {
            CaptureServiceState.RUNNING,
            CaptureServiceState.PAUSED,
            -> CaptureForegroundServiceRouteResult.Active(state)

            CaptureServiceState.IDLE,
            CaptureServiceState.STOPPED,
            -> CaptureForegroundServiceRouteResult.Stop
        }
}
