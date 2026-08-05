package com.fatmambo33.eclipsecam.capture

sealed interface CaptureRecoveryBundleResult {
    data object Missing : CaptureRecoveryBundleResult
    data class Ready(
        val plan: CapturePlan,
        val coordinator: CaptureSessionCoordinator,
    ) : CaptureRecoveryBundleResult

    data class Rejected(val reason: String) : CaptureRecoveryBundleResult
}

/**
 * Restores a capture plan and checkpoint as one validated recovery unit.
 *
 * The service must never regenerate or guess a plan for an existing checkpoint. A partial bundle,
 * corrupt plan, corrupt checkpoint, or incompatible pair is rejected before execution can resume.
 */
class CaptureRecoveryBundleLoader(
    private val planStore: CapturePlanStore,
    private val checkpointStore: CaptureCheckpointStore,
) {
    fun load(): CaptureRecoveryBundleResult = when (val storedPlan = planStore.read()) {
        CapturePlanReadResult.Missing -> when (checkpointStore.read()) {
            CheckpointReadResult.Missing -> CaptureRecoveryBundleResult.Missing
            else -> CaptureRecoveryBundleResult.Rejected("Capture checkpoint exists without its plan.")
        }

        is CapturePlanReadResult.Corrupt ->
            CaptureRecoveryBundleResult.Rejected("Capture plan is corrupt: ${storedPlan.reason}")

        is CapturePlanReadResult.Loaded -> when (
            val restored = CaptureSessionCoordinator.restore(storedPlan.plan, checkpointStore)
        ) {
            CaptureSessionRestoreResult.Missing ->
                CaptureRecoveryBundleResult.Rejected("Capture plan exists without its checkpoint.")

            is CaptureSessionRestoreResult.Rejected ->
                CaptureRecoveryBundleResult.Rejected(restored.reason)

            is CaptureSessionRestoreResult.Ready ->
                CaptureRecoveryBundleResult.Ready(storedPlan.plan, restored.coordinator)
        }
    }

    fun clear(): Boolean {
        val checkpointCleared = checkpointStore.clear()
        val planCleared = planStore.clear()
        return checkpointCleared && planCleared
    }
}
