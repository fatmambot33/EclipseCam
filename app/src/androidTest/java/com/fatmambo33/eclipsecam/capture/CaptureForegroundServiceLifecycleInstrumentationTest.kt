package com.fatmambo33.eclipsecam.capture

import android.Manifest
import android.app.NotificationManager
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.fatmambo33.eclipsecam.MainActivity
import java.time.Instant
import java.util.Collections
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@ExperimentalCamera2Interop
class CaptureForegroundServiceLifecycleInstrumentationTest {
    @get:Rule
    val foregroundPermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var service: CaptureForegroundService
    private lateinit var session: RecordingSession

    @Before
    fun installRuntime() {
        session = RecordingSession(CaptureServiceState.PAUSED)
        CaptureForegroundService.runtimeHostFactoryOverride = { createdService ->
            service = createdService
            CaptureForegroundServiceRuntimeHost(
                recoveryLoader = CaptureServiceRecoveryLoader(::readyRecovery),
                sessionCreator = CaptureRuntimeSessionCreator { session },
            )
        }
    }

    @After
    fun clearRuntime() {
        CaptureForegroundService.clearRuntimeHostFactoryOverride()
        context.getSystemService(NotificationManager::class.java)
            .cancel(CaptureForegroundService.NOTIFICATION_ID)
    }

    @Test
    fun serviceRoutesLifecycleCommandsAndKeepsForegroundStateAcrossActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            CaptureForegroundService.start(context)

            await("service to enter RUNNING") { session.state == CaptureServiceState.RUNNING }
            await("foreground notification") {
                context.getSystemService(NotificationManager::class.java)
                    .activeNotifications
                    .any { it.id == CaptureForegroundService.NOTIFICATION_ID }
            }
            assertEquals(listOf(CaptureServiceCommand.START), session.commandsSnapshot())

            CaptureForegroundService.pause(context)
            await("service to enter PAUSED") { session.state == CaptureServiceState.PAUSED }
            assertEquals(
                listOf(CaptureServiceCommand.START, CaptureServiceCommand.PAUSE),
                session.commandsSnapshot(),
            )

            activity.recreate()
            assertFalse(session.closed)
            assertEquals(CaptureServiceState.PAUSED, session.state)

            instrumentation.runOnMainSync {
                val result = service.onStartCommand(null, 0, 2)
                assertEquals(android.app.Service.START_STICKY, result)
            }
            assertEquals(CaptureServiceState.PAUSED, session.state)
            assertEquals(
                listOf(CaptureServiceCommand.START, CaptureServiceCommand.PAUSE),
                session.commandsSnapshot(),
            )

            CaptureForegroundService.start(context)
            await("service to resume RUNNING") { session.state == CaptureServiceState.RUNNING }
            assertEquals(
                listOf(
                    CaptureServiceCommand.START,
                    CaptureServiceCommand.PAUSE,
                    CaptureServiceCommand.START,
                ),
                session.commandsSnapshot(),
            )

            CaptureForegroundService.stop(context)
            await("STOP command") { CaptureServiceCommand.STOP in session.commandsSnapshot() }
            await("service destroy cleanup") { session.closed }
            assertTrue(session.closed)
            assertEquals(CaptureServiceState.STOPPED, session.state)
        }
    }

    private fun await(label: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!condition() && System.nanoTime() < deadline) {
            Thread.sleep(25)
        }
        assertTrue("Timed out waiting for $label.", condition())
    }

    private class RecordingSession(
        initialState: CaptureServiceState,
    ) : CaptureForegroundRuntimeSession {
        @Volatile
        override var state: CaptureServiceState = initialState
            private set

        @Volatile
        var closed: Boolean = false
            private set

        private val commands = Collections.synchronizedList(mutableListOf<CaptureServiceCommand>())

        override fun command(command: CaptureServiceCommand): CaptureRuntimeCommandResult? {
            commands += command
            state = when (command) {
                CaptureServiceCommand.START -> CaptureServiceState.RUNNING
                CaptureServiceCommand.PAUSE -> CaptureServiceState.PAUSED
                CaptureServiceCommand.STOP -> CaptureServiceState.STOPPED
            }
            return null
        }

        fun commandsSnapshot(): List<CaptureServiceCommand> = synchronized(commands) {
            commands.toList()
        }

        override fun tick(): CaptureRuntimeTickResult? = null

        override fun close() {
            closed = true
        }
    }

    private companion object {
        fun readyRecovery(): CaptureServiceBootstrapResult.Ready {
            val instant = Instant.parse("2026-08-12T17:00:00Z")
            val plan = CapturePlan(
                startsAtUtc = instant,
                endsAtUtc = instant,
                instructions = listOf(
                    CaptureInstruction(
                        instant,
                        CapturePhase.CONTACT_BURST,
                        ExposureStrategy.CONTACT_BRACKET,
                    ),
                ),
            )
            val coordinator = CaptureSessionCoordinator.arm(
                sessionId = "instrumented-service",
                plan = plan,
                nowUtc = instant.minusSeconds(60),
                checkpointStore = MemoryStore(),
            )
            return CaptureServiceBootstrapResult.Ready(
                plan,
                coordinator,
                CaptureServiceState.PAUSED,
            )
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
