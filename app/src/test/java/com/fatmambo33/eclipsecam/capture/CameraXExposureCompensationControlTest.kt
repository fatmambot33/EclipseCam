package com.fatmambo33.eclipsecam.capture

import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraXExposureCompensationControlTest {
    @Test
    fun forwardsRequestedIndexAndAwaitsSuccess() = runBlocking {
        val port = RecordingPort(CompletedFuture.success(2))
        val control = CameraXExposureCompensationControl(port)

        assertEquals(CameraXControlResult.Applied, control.apply(2))
        assertEquals(2, port.requestedIndex)
    }

    @Test
    fun rejectedIndexFailsClosedWithOperationContext() = runBlocking {
        val port = RecordingPort(
            CompletedFuture.failure(IllegalArgumentException("outside supported range")),
        )
        val control = CameraXExposureCompensationControl(port)

        val result = control.apply(-3) as CameraXControlResult.FatalFailure

        assertEquals(
            "Set exposure compensation to -3: outside supported range",
            result.reason,
        )
    }

    @Test
    fun cameraCancellationIsRecoverable() = runBlocking {
        val port = RecordingPort(CompletedFuture.cancelled())
        val control = CameraXExposureCompensationControl(port)

        val result = control.apply(1) as CameraXControlResult.RecoverableFailure

        assertEquals(
            "Set exposure compensation to 1: CameraX cancelled the operation.",
            result.reason,
        )
    }

    private class RecordingPort(
        private val future: ListenableFuture<Int>,
    ) : CameraXExposureCompensationPort {
        var requestedIndex: Int? = null
            private set

        override fun setExposureCompensationIndex(index: Int): ListenableFuture<Int> {
            requestedIndex = index
            return future
        }
    }

    private class CompletedFuture<T> private constructor(
        private val value: T? = null,
        private val failure: Throwable? = null,
        private val cancelled: Boolean = false,
    ) : ListenableFuture<T> {
        override fun addListener(listener: Runnable, executor: Executor) {
            executor.execute(listener)
        }

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
            fun <T> success(value: T): CompletedFuture<T> = CompletedFuture(value = value)

            fun <T> failure(error: Throwable): CompletedFuture<T> =
                CompletedFuture(failure = error)

            fun <T> cancelled(): CompletedFuture<T> = CompletedFuture(cancelled = true)
        }
    }
}
