package com.fatmambo33.eclipsecam.capture

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureRuntimeWakeupControllerTest {
    @Test
    fun replacingWakeupCancelsPreviousTaskAndRunsOnlyLatest() {
        val scheduler = FakeScheduler()
        var wakeups = 0
        val controller = CaptureRuntimeWakeupController(
            nowUtc = { Instant.parse("2026-08-12T17:00:00Z") },
            scheduler = scheduler,
            onWakeup = { wakeups += 1 },
        )

        controller.scheduleAt(Instant.parse("2026-08-12T17:00:10Z"))
        controller.runImmediately()

        assertEquals(Duration.ofSeconds(10), scheduler.tasks[0].delay)
        assertTrue(scheduler.tasks[0].cancelled)
        assertEquals(Duration.ZERO, scheduler.tasks[1].delay)

        scheduler.tasks[0].run()
        scheduler.tasks[1].run()
        assertEquals(1, wakeups)
    }

    @Test
    fun pastWakeupUsesZeroDelay() {
        val scheduler = FakeScheduler()
        val controller = CaptureRuntimeWakeupController(
            nowUtc = { Instant.parse("2026-08-12T17:00:10Z") },
            scheduler = scheduler,
            onWakeup = {},
        )

        controller.scheduleAt(Instant.parse("2026-08-12T17:00:00Z"))

        assertEquals(Duration.ZERO, scheduler.tasks.single().delay)
    }

    @Test
    fun cancelInvalidatesQueuedCallback() {
        val scheduler = FakeScheduler()
        var wakeups = 0
        val controller = CaptureRuntimeWakeupController(
            scheduler = scheduler,
            onWakeup = { wakeups += 1 },
        )

        controller.runImmediately()
        controller.cancel()
        scheduler.tasks.single().run()

        assertTrue(scheduler.tasks.single().cancelled)
        assertEquals(0, wakeups)
    }

    @Test
    fun closeCancelsWorkRejectsFutureSchedulingAndClosesScheduler() {
        val scheduler = FakeScheduler()
        var wakeups = 0
        val controller = CaptureRuntimeWakeupController(
            scheduler = scheduler,
            onWakeup = { wakeups += 1 },
        )

        controller.runImmediately()
        controller.close()
        controller.runImmediately()
        scheduler.tasks.single().run()

        assertTrue(scheduler.closed)
        assertTrue(scheduler.tasks.single().cancelled)
        assertEquals(1, scheduler.tasks.size)
        assertEquals(0, wakeups)
    }

    private class FakeScheduler : CaptureWakeupTaskScheduler {
        val tasks = mutableListOf<FakeTask>()
        var closed = false

        override fun schedule(delay: Duration, task: () -> Unit): CaptureWakeupTask {
            assertFalse(closed)
            return FakeTask(delay, task).also(tasks::add)
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeTask(
        val delay: Duration,
        private val callback: () -> Unit,
    ) : CaptureWakeupTask {
        var cancelled = false

        override fun cancel() {
            cancelled = true
        }

        fun run() {
            callback()
        }
    }
}
