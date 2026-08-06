package com.fatmambo33.eclipsecam.capture

import java.time.Duration
import java.time.Instant
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

fun interface CaptureWakeupTask {
    fun cancel()
}

interface CaptureWakeupTaskScheduler : AutoCloseable {
    fun schedule(delay: Duration, task: () -> Unit): CaptureWakeupTask

    override fun close()
}

/** Single-threaded production scheduler for capture runtime wake-ups. */
class ExecutorCaptureWakeupTaskScheduler : CaptureWakeupTaskScheduler {
    private val executor = ScheduledThreadPoolExecutor(1) { runnable ->
        Thread(runnable, "EclipseCam-capture").apply { isDaemon = true }
    }.apply {
        removeOnCancelPolicy = true
        setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
        setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
    }

    override fun schedule(delay: Duration, task: () -> Unit): CaptureWakeupTask {
        require(!delay.isNegative) { "Capture wake-up delay must not be negative." }
        val future = executor.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS)
        return CaptureWakeupTask { future.cancel(false) }
    }

    override fun close() {
        executor.shutdownNow()
    }
}

/**
 * Replace-all wake-up adapter for the foreground capture runtime.
 *
 * Only one wake-up may be pending. Replacing, cancelling, or closing invalidates older callbacks,
 * so stale executor work cannot drive capture after a pause, stop, or service destruction.
 */
class CaptureRuntimeWakeupController(
    private val nowUtc: () -> Instant = Instant::now,
    private val scheduler: CaptureWakeupTaskScheduler = ExecutorCaptureWakeupTaskScheduler(),
    private val onWakeup: () -> Unit,
) : CaptureRuntimeWakeupPort, AutoCloseable {
    private val lock = Any()
    private var generation = 0L
    private var pending: CaptureWakeupTask? = null
    private var closed = false

    override fun runImmediately() {
        replace(Duration.ZERO)
    }

    override fun scheduleAt(instantUtc: Instant) {
        val delay = Duration.between(nowUtc(), instantUtc).coerceAtLeast(Duration.ZERO)
        replace(delay)
    }

    override fun cancel() {
        synchronized(lock) {
            generation += 1
            pending?.cancel()
            pending = null
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            generation += 1
            pending?.cancel()
            pending = null
        }
        scheduler.close()
    }

    private fun replace(delay: Duration) {
        val token: Long
        synchronized(lock) {
            if (closed) return
            generation += 1
            token = generation
            pending?.cancel()
            pending = scheduler.schedule(delay) { dispatch(token) }
        }
    }

    private fun dispatch(token: Long) {
        val shouldRun = synchronized(lock) {
            if (closed || token != generation) {
                false
            } else {
                pending = null
                true
            }
        }
        if (shouldRun) onWakeup()
    }
}

private fun Duration.coerceAtLeast(minimum: Duration): Duration =
    if (this < minimum) minimum else this
