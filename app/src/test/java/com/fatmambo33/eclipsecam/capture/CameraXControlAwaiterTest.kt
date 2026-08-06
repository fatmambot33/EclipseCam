package com.fatmambo33.eclipsecam.capture

import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraXControlAwaiterTest {
    private val awaiter = CameraXControlAwaiter()

    @Test
    fun completedOperationIsApplied() = runBlocking {
        val future = ControllableFuture().apply { succeed() }

        assertEquals(
            CameraXControlResult.Applied,
            awaiter.await("Set exposure compensation", future),
        )
    }

    @Test
    fun rejectedOperationFailsClosedWithContext() = runBlocking {
        val future = ControllableFuture().apply {
            fail(IllegalArgumentException("outside supported range"))
        }

        val result = awaiter.await("Set exposure compensation", future)
            as CameraXControlResult.FatalFailure

        assertEquals(
            "Set exposure compensation: outside supported range",
            result.reason,
        )
    }

    @Test
    fun unknownOperationFailureIsFatal() = runBlocking {
        val future = ControllableFuture().apply { fail(IllegalStateException()) }

        val result = awaiter.await("Lock white balance", future)
            as CameraXControlResult.FatalFailure

        assertEquals(
            "Lock white balance: CameraX control failed unexpectedly.",
            result.reason,
        )
    }

    @Test
    fun cameraCancellationIsRecoverable() = runBlocking {
        val future = ControllableFuture().apply { cancel(false) }

        val result = awaiter.await("Start focus and metering", future)
            as CameraXControlResult.RecoverableFailure

        assertEquals(
            "Start focus and metering: CameraX cancelled the operation.",
            result.reason,
        )
    }

    @Test
    fun coroutineCancellationCancelsPendingCameraFuture() = runBlocking {
        val future = ControllableFuture()
        val job = launch {
            awaiter.await("Bind camera", future)
        }
        yield()

        job.cancelAndJoin()

        assertTrue(future.isCancelled)
    }

    private class ControllableFuture : ListenableFuture<Void> {
        private sealed interface State {
            data object Pending : State
            data object Succeeded : State
            data object Cancelled : State
            data class Failed(val error: Throwable) : State
        }

        private data class Listener(val command: Runnable, val executor: Executor)

        private val lock = Any()
        private var state: State = State.Pending
        private val listeners = mutableListOf<Listener>()

        fun succeed() = complete(State.Succeeded)

        fun fail(error: Throwable) = complete(State.Failed(error))

        override fun addListener(listener: Runnable, executor: Executor) {
            val runImmediately = synchronized(lock) {
                if (state == State.Pending) {
                    listeners += Listener(listener, executor)
                    false
                } else {
                    true
                }
            }
            if (runImmediately) executor.execute(listener)
        }

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean =
            complete(State.Cancelled)

        override fun isCancelled(): Boolean = synchronized(lock) { state == State.Cancelled }

        override fun isDone(): Boolean = synchronized(lock) { state != State.Pending }

        override fun get(): Void? = result()

        override fun get(timeout: Long, unit: TimeUnit): Void? {
            if (!isDone) throw TimeoutException("Test future is still pending.")
            return result()
        }

        private fun complete(completedState: State): Boolean {
            val pendingListeners = synchronized(lock) {
                if (state != State.Pending) return false
                state = completedState
                listeners.toList().also { listeners.clear() }
            }
            pendingListeners.forEach { it.executor.execute(it.command) }
            return true
        }

        private fun result(): Void? = when (val current = synchronized(lock) { state }) {
            State.Pending -> throw IllegalStateException("Test future is still pending.")
            State.Succeeded -> null
            State.Cancelled -> throw CancellationException()
            is State.Failed -> throw ExecutionException(current.error)
        }
    }
}
