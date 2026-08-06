package com.fatmambo33.eclipsecam.capture

import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class Camera2ManualInfinityFocusControlTest {
    @Test
    fun appliesManualInfinityAndAwaitsConfirmation() = runBlocking {
        val port = RecordingPort(CompletedFuture.success(null))
        val control = Camera2ManualInfinityFocusControl(port)

        assertEquals(CameraXControlResult.Applied, control.apply())
        assertEquals(listOf(true), port.requests)
    }

    @Test
    fun restoresContinuousAutoFocusAndAwaitsConfirmation() = runBlocking {
        val port = RecordingPort(CompletedFuture.success(null))
        val control = Camera2ManualInfinityFocusControl(port)

        assertEquals(CameraXControlResult.Applied, control.restoreContinuousAutoFocus())
        assertEquals(listOf(false), port.requests)
    }

    @Test
    fun rejectedManualFocusFailsClosedWithContext() = runBlocking {
        val port = RecordingPort(
            CompletedFuture.failure(UnsupportedOperationException("manual focus unavailable")),
        )
        val control = Camera2ManualInfinityFocusControl(port)

        val result = control.apply() as CameraXControlResult.FatalFailure

        assertEquals(
            "Apply manual infinity focus: manual focus unavailable",
            result.reason,
        )
    }

    @Test
    fun cameraCancellationIsRecoverable() = runBlocking {
        val port = RecordingPort(CompletedFuture.cancelled())
        val control = Camera2ManualInfinityFocusControl(port)

        val result = control.apply() as CameraXControlResult.RecoverableFailure

        assertEquals(
            "Apply manual infinity focus: CameraX cancelled the operation.",
            result.reason,
        )
    }

    private class RecordingPort(
        private val future: ListenableFuture<Void>,
    ) : Camera2ManualInfinityFocusPort {
        val requests = mutableListOf<Boolean>()

        override fun setManualInfinity(enabled: Boolean): ListenableFuture<Void> {
            requests += enabled
            return future
        }
    }

    private class CompletedFuture<T> private constructor(
        private val value: T? = null,
        private val failure: Throwable? = null,
        private val cancelled: Boolean = false,
    ) : ListenableFuture<T> {
        override fun addListener(listener: Runnable, executor: Executor) = executor.execute(listener)
        override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
        override fun isCancelled(): Boolean = cancelled
        override fun isDone(): Boolean = true
        override fun get(): T {
            if (cancelled) throw CancellationException()
            failure?.let { throw ExecutionException(it) }
            @Suppress("UNCHECKED_CAST")
            return value as T
        }
        override fun get(timeout: Long, unit: TimeUnit): T = get()

        companion object {
            fun <T> success(value: T?): CompletedFuture<T> = CompletedFuture(value = value)
            fun <T> failure(error: Throwable): CompletedFuture<T> = CompletedFuture(failure = error)
            fun <T> cancelled(): CompletedFuture<T> = CompletedFuture(cancelled = true)
        }
    }
}
