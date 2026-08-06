package com.fatmambo33.eclipsecam.capture

import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Camera2ManualSensorExposureControlTest {
    @Test
    fun rejectsInvalidExposureRequests() {
        assertThrows(IllegalArgumentException::class.java) {
            Camera2ManualSensorExposureRequest(exposureTimeNanos = 0L, sensitivityIso = 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Camera2ManualSensorExposureRequest(exposureTimeNanos = 1_000_000L, sensitivityIso = 0)
        }
    }

    @Test
    fun appliesValidatedExposureAndAwaitsConfirmation() = runBlocking {
        val port = RecordingPort(CompletedFuture.success())
        val control = Camera2ManualSensorExposureControl(port)
        val request = Camera2ManualSensorExposureRequest(
            exposureTimeNanos = 2_000_000L,
            sensitivityIso = 200,
        )

        assertEquals(CameraXControlResult.Applied, control.apply(request))
        assertEquals(listOf(request), port.applied)
    }

    @Test
    fun restoresAutomaticExposureAndAwaitsConfirmation() = runBlocking {
        val port = RecordingPort(CompletedFuture.success())
        val control = Camera2ManualSensorExposureControl(port)

        assertEquals(CameraXControlResult.Applied, control.restoreAutoExposure())
        assertEquals(1, port.restoreCalls)
    }

    @Test
    fun rejectedExposureFailsClosedWithContext() = runBlocking {
        val port = RecordingPort(
            CompletedFuture.failure(UnsupportedOperationException("manual sensor unavailable")),
        )
        val control = Camera2ManualSensorExposureControl(port)
        val request = Camera2ManualSensorExposureRequest(2_000_000L, 200)

        val result = control.apply(request) as CameraXControlResult.FatalFailure

        assertEquals(
            "Apply manual exposure 2000000ns ISO 200: manual sensor unavailable",
            result.reason,
        )
    }

    @Test
    fun cameraCancellationIsRecoverable() = runBlocking {
        val port = RecordingPort(CompletedFuture.cancelled())
        val control = Camera2ManualSensorExposureControl(port)

        val result = control.restoreAutoExposure() as CameraXControlResult.RecoverableFailure

        assertEquals(
            "Restore automatic exposure: CameraX cancelled the operation.",
            result.reason,
        )
    }

    private class RecordingPort(
        private val future: ListenableFuture<Void>,
    ) : Camera2ManualSensorExposurePort {
        val applied = mutableListOf<Camera2ManualSensorExposureRequest>()
        var restoreCalls = 0

        override fun apply(request: Camera2ManualSensorExposureRequest): ListenableFuture<Void> {
            applied += request
            return future
        }

        override fun restoreAutoExposure(): ListenableFuture<Void> {
            restoreCalls += 1
            return future
        }
    }

    private class CompletedFuture private constructor(
        private val failure: Throwable? = null,
        private val cancelled: Boolean = false,
    ) : ListenableFuture<Void> {
        override fun addListener(listener: Runnable, executor: Executor) = executor.execute(listener)
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
