package com.fatmambo33.eclipsecam.capture

import java.time.Instant

interface CaptureForegroundRuntimeSession : AutoCloseable {
    val state: CaptureServiceState

    fun command(command: CaptureServiceCommand): CaptureRuntimeCommandResult?

    fun tick(): CaptureRuntimeTickResult?

    override fun close()
}

/**
 * Lifecycle owner for one recovered foreground capture session.
 *
 * Commands from Android lifecycle callbacks and scheduled wake-ups are serialized through one lock.
 * The owned wake-up controller uses replace-all scheduling, while [close] invalidates pending work
 * before releasing the scheduler so destroyed service instances cannot execute stale capture ticks.
 */
class CaptureForegroundServiceSession(
    recovery: CaptureServiceBootstrapResult.Ready,
    instructionExecutor: CaptureInstructionExecutor,
    healthProvider: CaptureRuntimeHealthProvider,
    nowUtc: () -> Instant = Instant::now,
    scheduler: CaptureWakeupTaskScheduler = ExecutorCaptureWakeupTaskScheduler(),
) : CaptureForegroundRuntimeSession {
    private val lock = Any()
    private val clock = nowUtc
    private var closed = false
    private lateinit var driver: CaptureForegroundRuntimeDriver
    private val wakeups = CaptureRuntimeWakeupController(
        nowUtc = nowUtc,
        scheduler = scheduler,
        onWakeup = { tickFromWakeup() },
    )

    init {
        driver = CaptureForegroundRuntimeComposition.create(
            recovery = recovery,
            instructionExecutor = instructionExecutor,
            healthProvider = healthProvider,
            wakeups = wakeups,
        )
    }

    override val state: CaptureServiceState
        get() = synchronized(lock) { driver.state }

    override fun command(command: CaptureServiceCommand): CaptureRuntimeCommandResult? = synchronized(lock) {
        if (closed) return null
        driver.command(command, clock())
    }

    override fun tick(): CaptureRuntimeTickResult? = synchronized(lock) {
        if (closed) return null
        driver.tick(clock())
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            driver.shutdown()
        }
        wakeups.close()
    }

    private fun tickFromWakeup() {
        tick()
    }
}
