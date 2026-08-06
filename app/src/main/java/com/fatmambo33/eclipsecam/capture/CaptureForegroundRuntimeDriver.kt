package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.device.health.DeviceHealthDecision
import java.time.Instant

/** Testable boundary around [CaptureRuntimeLoop] for foreground-service integration. */
interface CaptureRuntimePort {
    val state: CaptureServiceState

    fun command(command: CaptureServiceCommand, nowUtc: Instant): CaptureRuntimeCommandResult

    fun tick(nowUtc: Instant, health: DeviceHealthDecision): CaptureRuntimeTickResult
}

/** Production [CaptureRuntimePort] backed by the deterministic capture runtime loop. */
class CaptureRuntimeLoopPort(
    private val loop: CaptureRuntimeLoop,
) : CaptureRuntimePort {
    override val state: CaptureServiceState
        get() = loop.state

    override fun command(
        command: CaptureServiceCommand,
        nowUtc: Instant,
    ): CaptureRuntimeCommandResult = loop.command(command, nowUtc)

    override fun tick(
        nowUtc: Instant,
        health: DeviceHealthDecision,
    ): CaptureRuntimeTickResult = loop.tick(nowUtc, health)
}

/** Supplies one fresh local device-health decision immediately before each capture tick. */
fun interface CaptureRuntimeHealthProvider {
    fun current(): DeviceHealthDecision
}

/** Applies one replace-all wake-up directive to the Android service scheduler. */
interface CaptureRuntimeWakeupPort {
    fun runImmediately()

    fun scheduleAt(instantUtc: Instant)

    fun cancel()
}

/**
 * Connects service commands and wake-ups to capture execution without duplicating runtime rules.
 *
 * Every command replaces pending wake-ups. Every tick samples device health immediately before
 * execution and then replaces the previous wake-up with the returned directive. Pause, stop,
 * safeguard pause, failure, completion, and inactive states always cancel future work.
 */
class CaptureForegroundRuntimeDriver(
    private val runtime: CaptureRuntimePort,
    private val healthProvider: CaptureRuntimeHealthProvider,
    private val wakeups: CaptureRuntimeWakeupPort,
) {
    val state: CaptureServiceState
        get() = runtime.state

    fun command(
        command: CaptureServiceCommand,
        nowUtc: Instant,
    ): CaptureRuntimeCommandResult = runtime.command(command, nowUtc).also { result ->
        apply(result.nextDirective)
    }

    fun tick(nowUtc: Instant): CaptureRuntimeTickResult =
        runtime.tick(nowUtc, healthProvider.current()).also { result ->
            apply(result.nextDirective)
        }

    fun shutdown() {
        wakeups.cancel()
    }

    private fun apply(directive: CaptureTickDirective) {
        when (directive) {
            CaptureTickDirective.RunImmediately -> wakeups.runImmediately()
            is CaptureTickDirective.ScheduleAt -> wakeups.scheduleAt(directive.instantUtc)
            CaptureTickDirective.Stop -> wakeups.cancel()
        }
    }
}
