package com.fatmambo33.eclipsecam.capture

import com.google.common.util.concurrent.SettableFuture
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
        val future = SettableFuture.create<Void>()
        future.set(null)

        assertEquals(
            CameraXControlResult.Applied,
            awaiter.await("Set exposure compensation", future),
        )
    }

    @Test
    fun rejectedOperationFailsClosedWithContext() = runBlocking {
        val future = SettableFuture.create<Void>()
        future.setException(IllegalArgumentException("outside supported range"))

        val result = awaiter.await("Set exposure compensation", future)
            as CameraXControlResult.FatalFailure

        assertEquals(
            "Set exposure compensation: outside supported range",
            result.reason,
        )
    }

    @Test
    fun unknownOperationFailureIsFatal() = runBlocking {
        val future = SettableFuture.create<Void>()
        future.setException(IllegalStateException())

        val result = awaiter.await("Lock white balance", future)
            as CameraXControlResult.FatalFailure

        assertEquals(
            "Lock white balance: CameraX control failed unexpectedly.",
            result.reason,
        )
    }

    @Test
    fun cameraCancellationIsRecoverable() = runBlocking {
        val future = SettableFuture.create<Void>()
        future.cancel(false)

        val result = awaiter.await("Start focus and metering", future)
            as CameraXControlResult.RecoverableFailure

        assertEquals(
            "Start focus and metering: CameraX cancelled the operation.",
            result.reason,
        )
    }

    @Test
    fun coroutineCancellationCancelsPendingCameraFuture() = runBlocking {
        val future = SettableFuture.create<Void>()
        val job = launch {
            awaiter.await("Bind camera", future)
        }
        yield()

        job.cancelAndJoin()

        assertTrue(future.isCancelled)
    }
}
