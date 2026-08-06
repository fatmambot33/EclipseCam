package com.fatmambo33.eclipsecam.capture

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureForegroundServiceRuntimeHostTest {
    @Test
    fun missingRecoveryDoesNotConstructSession() {
        var creates = 0
        val host = CaptureForegroundServiceRuntimeHost(
            recoveryLoader = CaptureServiceRecoveryLoader { CaptureServiceBootstrapResult.Missing },
            sessionCreator = CaptureRuntimeSessionCreator {
                creates += 1
                FakeSession()
            },
        )

        assertEquals(CaptureRuntimeHostStartResult.Missing, host.start())
        assertEquals(0, creates)
        assertEquals(CaptureServiceState.IDLE, host.state)
        assertNull(host.command(CaptureServiceCommand.START))
    }

    @Test
    fun constructionFailureFailsClosedAndCanBeRetried() {
        var attempts = 0
        val host = CaptureForegroundServiceRuntimeHost(
            recoveryLoader = CaptureServiceRecoveryLoader { readyRecovery() },
            sessionCreator = CaptureRuntimeSessionCreator {
                attempts += 1
                error("CameraX dependencies unavailable.")
            },
        )

        assertEquals(
            CaptureRuntimeHostStartResult.Failed("CameraX dependencies unavailable."),
            host.start(),
        )
        assertEquals(
            CaptureRuntimeHostStartResult.Failed("CameraX dependencies unavailable."),
            host.start(),
        )
        assertEquals(2, attempts)
        assertEquals(CaptureServiceState.IDLE, host.state)
    }

    @Test
    fun repeatedStartOwnsOneSessionAndCloseIsIdempotent() {
        val session = FakeSession(CaptureServiceState.PAUSED)
        var creates = 0
        val host = CaptureForegroundServiceRuntimeHost(
            recoveryLoader = CaptureServiceRecoveryLoader { readyRecovery() },
            sessionCreator = CaptureRuntimeSessionCreator {
                creates += 1
                session
            },
        )

        assertEquals(CaptureRuntimeHostStartResult.Ready(CaptureServiceState.PAUSED), host.start())
        assertEquals(CaptureRuntimeHostStartResult.Ready(CaptureServiceState.PAUSED), host.start())
        assertEquals(1, creates)

        host.command(CaptureServiceCommand.START)
        assertEquals(listOf(CaptureServiceCommand.START), session.commands)

        host.close()
        host.close()
        assertTrue(session.closed)
        assertNull(host.command(CaptureServiceCommand.START))
        assertEquals(
            CaptureRuntimeHostStartResult.Failed("Capture runtime host is closed."),
            host.start(),
        )
    }

    private fun readyRecovery(): CaptureServiceBootstrapResult.Ready {
        val instant = Instant.parse("2026-08-12T17:00:00Z")
        val plan = CapturePlan(
            startsAtUtc = instant,
            endsAtUtc = instant,
            instructions = listOf(
                CaptureInstruction(instant, CapturePhase.CONTACT_BURST, ExposureStrategy.CONTACT_BRACKET),
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
        override var state: CaptureServiceState = CaptureServiceState.IDLE,
    ) : CaptureForegroundRuntimeSession {
        val commands = mutableListOf<CaptureServiceCommand>()
        var closed = false

        override fun command(command: CaptureServiceCommand): CaptureRuntimeCommandResult? {
            commands += command
            return null
        }

        override fun tick(): CaptureRuntimeTickResult? = null

        override fun close() {
            assertFalse(closed)
            closed = true
        }
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
