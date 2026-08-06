package com.fatmambo33.eclipsecam.capture

import androidx.camera.core.FocusMeteringResult
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CameraXFocusMeteringControlTest {
    @Test
    fun rejectsOutOfBoundsMeteringPoints() {
        assertThrows(IllegalArgumentException::class.java) {
            CameraXFocusMeteringRequest(normalizedX = -0.01f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CameraXFocusMeteringRequest(normalizedY = 1.01f)
        }
    }

    @Test
    fun forwardsMeteringPointAndAwaitsSuccess() = runBlocking {
        val port = RecordingPort(startFuture = CompletedFuture.success())
        val control = CameraXFocusMeteringControl(port)
        val request = CameraXFocusMeteringRequest(normalizedX = 0.4f, normalizedY = 0.6f)

        assertEquals(CameraXControlResult.Applied, control.start(request))
        assertEquals(request, port.startedRequest)
    }

    @Test
    fun rejectedMeteringFailsClosedWithOperationContext() = runBlocking {
        val port = RecordingPort(
            startFuture = CompletedFuture.failure(IllegalArgumentException("point unsupported")),
        )
        val control = CameraXFocusMeteringControl(port)

        val result = control.start() as CameraXControlResult.FatalFailure

        assertEquals(
            "Start focus and metering at 0.5,0.5: point unsupported",
            result.reason,
        )
    }

    @Test
    fun restoringContinuousAutofocusTreatsCancellationAsRecoverable() = runBlocking {
        val port = RecordingPort(cancelFuture = CompletedFuture.cancelled())
        val control = CameraXFocusMeteringControl(port)

        val result = control.restoreContinuousAutoFocus() as
            CameraXControlResult.RecoverableFailure

        assertEquals(
            "Restore continuous autofocus: CameraX cancelled the operation.",
            result.reason,
        )
        assertEquals(1, port.cancelCalls)
    }

    private class RecordingPort(
        private val startFuture: ListenableFuture<FocusMeteringResult> = CompletedFuture.success(),
        private val cancelFuture: ListenableFuture<Void> = CompletedFuture.success(),
    ) : CameraXFocusMeteringPort {
        var startedRequest: CameraXFocusMeteringRequest? = null
            private set
        var cancelCalls: Int = 0
            private set

        override fun start(
            request: CameraXFocusMeteringRequest,
        ): ListenableFuture<FocusMeteringResult> {
            startedRequest = request
            return startFuture
        }

        override fun cancel(): ListenableFuture<Void> {
            cancelCalls += 1
            return cancelFuture
        }
    }

    private class CompletedFuture<T> private constructor(
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
            return null as T
        }

        override fun get(timeout: Long, unit: TimeUnit): T = get()

        companion object {
            fun <T> success(): CompletedFuture<T> = CompletedFuture()

            fun <T> failure(error: Throwable): CompletedFuture<T> =
                CompletedFuture(failure = error)

            fun <T> cancelled(): CompletedFuture<T> = CompletedFuture(cancelled = true)
        }
    }
}
