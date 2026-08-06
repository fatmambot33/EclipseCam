package com.fatmambo33.eclipsecam.capture

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureForegroundServiceCommandRouterTest {
    @Test
    fun missingRecoveryStopsFailClosed() {
        val router = CaptureForegroundServiceCommandRouter(
            CaptureForegroundServiceRuntimeHost(
                recoveryLoader = CaptureServiceRecoveryLoader { CaptureServiceBootstrapResult.Missing },
                sessionCreator = CaptureRuntimeSessionCreator { error("must not create") },
            ),
        )

        assertEquals(CaptureForegroundServiceRouteResult.Stop, router.initialize())
    }

    @Test
    fun stickyRestartKeepsRecoveredSessionPausedUntilExplicitStart() {
        val session = FakeSession(CaptureServiceState.PAUSED)
        val router = CaptureForegroundServiceCommandRouter(host(session))

        assertEquals(
            CaptureForegroundServiceRouteResult.Active(CaptureServiceState.PAUSED),
            router.initialize(),
        )
        assertEquals(
            CaptureForegroundServiceRouteResult.Active(CaptureServiceState.PAUSED),
            router.route(CaptureForegroundServiceRequest.STICKY_RESTART),
        )
        assertTrue(session.commands.isEmpty())

        assertEquals(
            CaptureForegroundServiceRouteResult.Active(CaptureServiceState.RUNNING),
            router.route(CaptureForegroundServiceRequest.START),
        )
        assertEquals(listOf(CaptureServiceCommand.START), session.commands)
    }

    @Test
    fun pauseAndStopRouteThroughOwnedRuntime() {
        val session = FakeSession(CaptureServiceState.RUNNING)
        val router = CaptureForegroundServiceCommandRouter(host(session))
        router.initialize()

        assertEquals(
            CaptureForegroundServiceRouteResult.Active(CaptureServiceState.PAUSED),
            router.route(CaptureForegroundServiceRequest.PAUSE),
        )
        assertEquals(
            CaptureForegroundServiceRouteResult.Stop,
            router.route(CaptureForegroundServiceRequest.STOP),
        )
        assertEquals(
            listOf(CaptureServiceCommand.PAUSE, CaptureServiceCommand.STOP),
            session.commands,
        )
    }

    private fun host(session: FakeSession) = CaptureForegroundServiceRuntimeHost(
        recoveryLoader = CaptureServiceRecoveryLoader { readyRecovery() },
        sessionCreator = CaptureRuntimeSessionCreator { session },
    )

    private fun readyRecovery(): CaptureServiceBootstrapResult.Ready {
        val instant = Instant.parse("2026-08-12T17:00:00Z")
        val plan = CapturePlan(
            startsAtUtc = instant,
            endsAtUtc = instant,
            instructions = listOf(
                CaptureInstruction(
                    instantUtc = instant,
                    phase = CapturePhase.CONTACT_BURST,
                    exposureStrategy = ExposureStrategy.CONTACT_BRACKET,
                ),
            ),
        )
        val coordinator = CaptureSessionCoordinator.arm(
            sessionId = "session",
            plan = plan,
            nowUtc = instant.minusSeconds(60),
            checkpointStore = MemoryStore(),
        )
        return CaptureServiceBootstrapResult.Ready(plan, coordinator, CaptureServiceState.PAUSED)
    }

    private class FakeSession(
        override var state: CaptureServiceState,
    ) : CaptureForegroundRuntimeSession {
        val commands = mutableListOf<CaptureServiceCommand>()

        override fun command(command: CaptureServiceCommand): CaptureRuntimeCommandResult? {
            commands += command
            state = when (command) {
                CaptureServiceCommand.START -> CaptureServiceState.RUNNING
                CaptureServiceCommand.PAUSE -> CaptureServiceState.PAUSED
                CaptureServiceCommand.STOP -> CaptureServiceState.STOPPED
            }
            return null
        }

        override fun tick(): CaptureRuntimeTickResult? = null

        override fun close() = Unit
    }

    private class MemoryStore : CaptureCheckpointStore {
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
