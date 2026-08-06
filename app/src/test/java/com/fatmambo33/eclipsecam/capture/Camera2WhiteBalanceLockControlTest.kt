package com.fatmambo33.eclipsecam.capture

import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class Camera2WhiteBalanceLockControlTest {
    @Test
    fun locksAndAwaitsConfirmedCompletion() = runBlocking {
        val port = RecordingPort(CompletedFuture.success())
        val control = Camera2WhiteBalanceLockControl(port)

        assertEquals(CameraXControlResult.Applied, control.lock())
        assertEquals(listOf(true), port.requests)
    }

    @Test
    fun unlocksAndAwaitsConfirmedCompletion() = runBlocking {
        val port = RecordingPort(CompletedFuture.success())
        val control = Camera2WhiteBalanceLockControl(port)

        assertEquals(CameraXControlResult.Applied, control.unlock())
        assertEquals(listOf(false), port.requests)
    }

    @Test
    fun rejectedLockFailsClosedWithOperationContext() = runBlocking {
        val port = RecordingPort(
            CompletedFuture.failure(UnsupportedOperationException("AWB lock unavailable")),
        )
        val control = Camera2WhiteBalanceLockControl(port)

        val result = control.lock() as CameraXControlResult.FatalFailure

        assertEquals(
            "Lock automatic white balance: AWB lock unavailable",
            result.reason,
        )
    }

    @Test
    fun cameraCancellationIsRecoverable() = runBlocking {
        val port = RecordingPort(CompletedFuture.cancelled())
        val control = Camera2WhiteBalanceLockControl(port)

        val result = control.unlock() as CameraXControlResult.RecoverableFailure

        assertEquals(
            "Unlock automatic white balance: CameraX cancelled the operation.",
            result.reason,
        )
    }

    private class RecordingPort(
        private val future: ListenableFuture<Void>,
    ) : Camera2WhiteBalanceLockPort {
        val requests = mutableListOf<Boolean>()

        override fun setLocked(locked: Boolean): ListenableFuture<Void> {
            requests += locked
            return future
        }
    }

    private class CompletedFuture private constructor(
        private val failure: Throwable? = null,
        private val cancelled: Boolean = false,
    ) : ListenableFuture<Void> {
        override fun addListener(listener: Runnable, executor: Executor) {
            executor.execute(listener)
        }

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false

        override fun isCancelled(): Boolean = cancelled

        override fun isDone(): Boolean = true

        override fun get(): Void? {
            if (cancelled) throw CancellationException()
            failure?.let { throw ExecutionException(it) }
            return null
        }

        override fun get(timeout: Long, unit: TimeUnit): Void? = get()

        companion object {
            fun success(): CompletedFuture = CompletedFuture()

            fun failure(error: Throwable): CompletedFuture = CompletedFuture(failure = error)

            fun cancelled(): CompletedFuture = CompletedFuture(cancelled = true)
        }
    }
}
