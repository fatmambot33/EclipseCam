package com.fatmambo33.eclipsecam.capture

/**
 * Production composition boundary for one recovered foreground capture session.
 *
 * The Android service supplies the concrete camera executor, fresh health provider, and wake-up
 * adapter. This factory joins them to the recovered plan and coordinator without duplicating
 * orchestration rules in lifecycle code.
 */
object CaptureForegroundRuntimeComposition {
    fun create(
        recovery: CaptureServiceBootstrapResult.Ready,
        instructionExecutor: CaptureInstructionExecutor,
        healthProvider: CaptureRuntimeHealthProvider,
        wakeups: CaptureRuntimeWakeupPort,
    ): CaptureForegroundRuntimeDriver {
        val executionEngine = CaptureExecutionEngine(
            plan = recovery.plan,
            coordinator = recovery.coordinator,
            executor = instructionExecutor,
        )
        val orchestrator = CaptureServiceOrchestrator(
            coordinator = recovery.coordinator,
            executionEngine = executionEngine,
            initialState = recovery.initialState,
        )
        return CaptureForegroundRuntimeDriver(
            runtime = CaptureRuntimeLoopPort(CaptureRuntimeLoop(orchestrator)),
            healthProvider = healthProvider,
            wakeups = wakeups,
        )
    }
}
