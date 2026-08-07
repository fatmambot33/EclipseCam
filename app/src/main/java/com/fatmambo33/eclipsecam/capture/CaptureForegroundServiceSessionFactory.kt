package com.fatmambo33.eclipsecam.capture

import android.content.Context
import com.fatmambo33.eclipsecam.media.FileLocalCaptureSessionJournal
import java.io.File
import java.time.Instant

fun interface CaptureIndexedCameraFactory {
    fun create(recovery: CaptureServiceBootstrapResult.Ready): IndexedCameraCapturePort
}

fun interface CaptureRuntimeHealthProviderFactory {
    fun create(): CaptureRuntimeHealthProvider
}

fun interface CaptureWakeupSchedulerFactory {
    fun create(): CaptureWakeupTaskScheduler
}

/**
 * Production composition boundary for one recovered foreground capture session.
 *
 * The factory deliberately requires a concrete indexed camera factory. It never installs a no-op or
 * placeholder camera implementation: if CameraX construction is unavailable, callers must fail the
 * service startup before a running checkpoint can be resumed.
 */
class CaptureForegroundServiceSessionFactory(
    private val indexedCameraFactory: CaptureIndexedCameraFactory,
    private val healthProviderFactory: CaptureRuntimeHealthProviderFactory,
    private val schedulerFactory: CaptureWakeupSchedulerFactory =
        CaptureWakeupSchedulerFactory(::ExecutorCaptureWakeupTaskScheduler),
    private val nowUtc: () -> Instant = Instant::now,
    private val sessionJournal: CaptureSessionJournal = CaptureSessionJournal { _, _ -> },
) {
    constructor(
        context: Context,
        indexedCameraFactory: CaptureIndexedCameraFactory,
        schedulerFactory: CaptureWakeupSchedulerFactory =
            CaptureWakeupSchedulerFactory(::ExecutorCaptureWakeupTaskScheduler),
        nowUtc: () -> Instant = Instant::now,
    ) : this(
        indexedCameraFactory = indexedCameraFactory,
        healthProviderFactory = CaptureRuntimeHealthProviderFactory {
            AndroidCaptureRuntimeHealthProvider(context.applicationContext)
        },
        schedulerFactory = schedulerFactory,
        nowUtc = nowUtc,
        sessionJournal = FileLocalCaptureSessionJournal(
            File(context.applicationContext.filesDir, CAPTURE_OUTPUT_DIRECTORY),
        ),
    )

    fun create(recovery: CaptureServiceBootstrapResult.Ready): CaptureForegroundServiceSession {
        val instructionExecutor = CheckpointIndexedCaptureInstructionExecutor(
            plan = recovery.plan,
            coordinator = recovery.coordinator,
            indexedCapture = indexedCameraFactory.create(recovery),
        )
        return CaptureForegroundServiceSession(
            recovery = recovery,
            instructionExecutor = instructionExecutor,
            healthProvider = healthProviderFactory.create(),
            nowUtc = nowUtc,
            scheduler = schedulerFactory.create(),
            sessionJournal = sessionJournal,
        )
    }

    private companion object {
        const val CAPTURE_OUTPUT_DIRECTORY = "captures"
    }
}
