package com.fatmambo33.eclipsecam.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureServiceStateReducerTest {
    @Test
    fun startRunsIdleService() {
        assertEquals(
            CaptureServiceState.RUNNING,
            CaptureServiceStateReducer.reduce(
                CaptureServiceState.IDLE,
                CaptureServiceCommand.START,
            ),
        )
    }

    @Test
    fun pauseAndResumeAreDeterministic() {
        val paused = CaptureServiceStateReducer.reduce(
            CaptureServiceState.RUNNING,
            CaptureServiceCommand.PAUSE,
        )
        val resumed = CaptureServiceStateReducer.reduce(paused, CaptureServiceCommand.START)

        assertEquals(CaptureServiceState.PAUSED, paused)
        assertEquals(CaptureServiceState.RUNNING, resumed)
    }

    @Test
    fun duplicateCommandsAreIdempotent() {
        assertEquals(
            CaptureServiceState.RUNNING,
            CaptureServiceStateReducer.reduce(
                CaptureServiceState.RUNNING,
                CaptureServiceCommand.START,
            ),
        )
        assertEquals(
            CaptureServiceState.PAUSED,
            CaptureServiceStateReducer.reduce(
                CaptureServiceState.PAUSED,
                CaptureServiceCommand.PAUSE,
            ),
        )
    }

    @Test
    fun stoppedServiceCannotBeRestartedByStaleCommand() {
        assertEquals(
            CaptureServiceState.STOPPED,
            CaptureServiceStateReducer.reduce(
                CaptureServiceState.STOPPED,
                CaptureServiceCommand.START,
            ),
        )
    }

    @Test
    fun stopAlwaysTerminatesServiceState() {
        CaptureServiceState.entries.forEach { current ->
            assertEquals(
                CaptureServiceState.STOPPED,
                CaptureServiceStateReducer.reduce(current, CaptureServiceCommand.STOP),
            )
        }
    }
}
