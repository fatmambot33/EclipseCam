package com.fatmambo33.eclipsecam.capture

import java.io.File

sealed interface CaptureServiceBootstrapResult {
    data object Missing : CaptureServiceBootstrapResult

    data class Ready(
        val plan: CapturePlan,
        val coordinator: CaptureSessionCoordinator,
        val initialState: CaptureServiceState,
    ) : CaptureServiceBootstrapResult

    data class Rejected(val reason: String) : CaptureServiceBootstrapResult
}

/**
 * Restores the durable capture bundle into a conservative foreground-service state.
 *
 * Active sessions are always restored paused. Process recreation must never silently resume camera
 * work; the user or an explicit service command must resume the session after recovery succeeds.
 */
class CaptureServiceRecoveryBootstrap(
    private val bundleLoader: CaptureRecoveryBundleLoader,
) {
    fun load(): CaptureServiceBootstrapResult = when (val bundle = bundleLoader.load()) {
        CaptureRecoveryBundleResult.Missing -> CaptureServiceBootstrapResult.Missing
        is CaptureRecoveryBundleResult.Rejected -> CaptureServiceBootstrapResult.Rejected(bundle.reason)
        is CaptureRecoveryBundleResult.Ready -> when (bundle.coordinator.snapshot().status) {
            CaptureSessionStatus.ARMED,
            CaptureSessionStatus.RUNNING,
            CaptureSessionStatus.PAUSED,
            -> CaptureServiceBootstrapResult.Ready(
                plan = bundle.plan,
                coordinator = bundle.coordinator,
                initialState = CaptureServiceState.PAUSED,
            )

            CaptureSessionStatus.COMPLETED ->
                CaptureServiceBootstrapResult.Rejected("Completed capture session is not resumable.")

            CaptureSessionStatus.FAILED ->
                CaptureServiceBootstrapResult.Rejected("Failed capture session is not resumable.")
        }
    }

    companion object {
        private const val RECOVERY_DIRECTORY = "capture"
        private const val PLAN_FILE = "plan.txt"
        private const val CHECKPOINT_FILE = "checkpoint.txt"

        fun fromFilesDirectory(filesDirectory: File): CaptureServiceRecoveryBootstrap {
            val recoveryDirectory = File(filesDirectory, RECOVERY_DIRECTORY)
            return CaptureServiceRecoveryBootstrap(
                CaptureRecoveryBundleLoader(
                    planStore = FileCapturePlanStore(File(recoveryDirectory, PLAN_FILE)),
                    checkpointStore = FileCaptureCheckpointStore(
                        File(recoveryDirectory, CHECKPOINT_FILE),
                    ),
                ),
            )
        }
    }
}
