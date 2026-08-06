package com.fatmambo33.eclipsecam.capture

import kotlinx.coroutines.runBlocking

/** Suspending indexed camera boundary used by the foreground capture worker. */
fun interface IndexedCameraCapturePort {
    suspend fun capture(
        instructionIndex: Int,
        instruction: CaptureInstruction,
    ): CameraCaptureResult
}

/**
 * Resolves the durable capture index before entering the suspending CameraX sequence pipeline.
 *
 * Output reservation and filenames depend on the instruction index. This adapter reads that index
 * from the same persisted coordinator used by the execution engine and verifies that the supplied
 * instruction is still the plan entry at that index. Any mismatch fails closed before camera or
 * filesystem access. The adapter is intended for the dedicated foreground-capture worker thread,
 * never the Android main thread.
 */
class CheckpointIndexedCaptureInstructionExecutor(
    private val plan: CapturePlan,
    private val coordinator: CaptureSessionCoordinator,
    private val indexedCapture: IndexedCameraCapturePort,
) : CaptureInstructionExecutor {
    constructor(
        plan: CapturePlan,
        coordinator: CaptureSessionCoordinator,
        executor: CameraInstructionSequenceExecutor,
    ) : this(
        plan = plan,
        coordinator = coordinator,
        indexedCapture = IndexedCameraCapturePort(executor::capture),
    )

    override fun capture(instruction: CaptureInstruction): CameraCaptureResult {
        val instructionIndex = coordinator.snapshot().nextInstructionIndex
        val expected = plan.instructions.getOrNull(instructionIndex)
            ?: return CameraCaptureResult.FatalError(
                "Capture checkpoint has no matching plan instruction at index $instructionIndex.",
            )
        if (expected != instruction) {
            return CameraCaptureResult.FatalError(
                "Capture instruction changed while resolving durable index $instructionIndex.",
            )
        }

        return runBlocking {
            indexedCapture.capture(instructionIndex, instruction)
        }
    }
}
