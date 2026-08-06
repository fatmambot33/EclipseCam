package com.fatmambo33.eclipsecam.capture

import androidx.camera.core.CameraControl
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Awaits a CameraX control future without blocking a thread.
 *
 * Coroutine cancellation cancels the pending CameraX future. Late completion is ignored. CameraX
 * operation cancellation is recoverable; invalid requests, permission failures, and unknown errors
 * fail closed so the capture engine cannot continue with unconfirmed camera state.
 */
class CameraXControlAwaiter {
    suspend fun await(
        operation: String,
        future: ListenableFuture<*>,
    ): CameraXControlResult = suspendCancellableCoroutine { continuation ->
        future.addListener(
            {
                if (!continuation.isActive) return@addListener
                val result = try {
                    future.get()
                    CameraXControlResult.Applied
                } catch (error: CancellationException) {
                    CameraXControlResult.RecoverableFailure(
                        failureReason(operation, error, "CameraX cancelled the operation."),
                    )
                } catch (error: ExecutionException) {
                    classify(operation, error.cause ?: error)
                } catch (error: RuntimeException) {
                    classify(operation, error)
                }
                if (continuation.isActive) continuation.resume(result)
            },
            DIRECT_EXECUTOR,
        )
        continuation.invokeOnCancellation { future.cancel(true) }
    }

    private fun classify(operation: String, error: Throwable): CameraXControlResult = when (error) {
        is CameraControl.OperationCanceledException ->
            CameraXControlResult.RecoverableFailure(
                failureReason(operation, error, "CameraX cancelled the operation."),
            )

        is IllegalArgumentException,
        is SecurityException,
        is UnsupportedOperationException ->
            CameraXControlResult.FatalFailure(
                failureReason(operation, error, "CameraX rejected the operation."),
            )

        else -> CameraXControlResult.FatalFailure(
            failureReason(operation, error, "CameraX control failed unexpectedly."),
        )
    }

    private fun failureReason(
        operation: String,
        error: Throwable,
        fallback: String,
    ): String {
        val detail = error.message?.trim().orEmpty().ifBlank { fallback }
        return "$operation: $detail"
    }

    private companion object {
        val DIRECT_EXECUTOR = Executor { command -> command.run() }
    }
}
