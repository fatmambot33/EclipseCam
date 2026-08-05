package com.fatmambo33.eclipsecam.capture

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureRecoveryBundleTest {
    private val start = Instant.parse("2026-08-12T17:00:00Z")
    private val plan = CapturePlan(
        startsAtUtc = start,
        endsAtUtc = start.plusSeconds(1),
        instructions = listOf(
            CaptureInstruction(start, CapturePhase.PARTIAL, ExposureStrategy.FILTERED_PARTIAL),
            CaptureInstruction(start.plusSeconds(1), CapturePhase.TOTALITY, ExposureStrategy.TOTALITY_BRACKET),
        ),
    )

    @Test
    fun bothMissingReturnsMissing() {
        val result = loader().load()

        assertTrue(result is CaptureRecoveryBundleResult.Missing)
    }

    @Test
    fun completeCompatibleBundleRestoresCoordinator() {
        val plans = MemoryPlanStore(plan)
        val checkpoints = MemoryCheckpointStore()
        val armed = CaptureSessionCoordinator.arm("session", plan, start.minusSeconds(5), checkpoints)
        armed.start(start.minusSeconds(1))
        armed.record(CaptureStepOutcome.CAPTURED, start)

        val result = CaptureRecoveryBundleLoader(plans, checkpoints).load()

        assertTrue(result is CaptureRecoveryBundleResult.Ready)
        result as CaptureRecoveryBundleResult.Ready
        assertEquals(plan, result.plan)
        assertEquals(1, result.coordinator.snapshot().nextInstructionIndex)
        assertEquals(1, result.coordinator.snapshot().capturedCount)
    }

    @Test
    fun rejectsCheckpointWithoutPlan() {
        val checkpoints = MemoryCheckpointStore()
        CaptureSessionCoordinator.arm("session", plan, start.minusSeconds(5), checkpoints)

        val result = loader(checkpoints = checkpoints).load()

        assertTrue(result is CaptureRecoveryBundleResult.Rejected)
        assertTrue((result as CaptureRecoveryBundleResult.Rejected).reason.contains("without its plan"))
    }

    @Test
    fun rejectsPlanWithoutCheckpoint() {
        val result = loader(plans = MemoryPlanStore(plan)).load()

        assertTrue(result is CaptureRecoveryBundleResult.Rejected)
        assertTrue((result as CaptureRecoveryBundleResult.Rejected).reason.contains("without its checkpoint"))
    }

    @Test
    fun rejectsCorruptPlanAndIncompatibleCheckpoint() {
        val corrupt = CaptureRecoveryBundleLoader(
            CorruptPlanStore,
            MemoryCheckpointStore(),
        ).load()
        assertTrue(corrupt is CaptureRecoveryBundleResult.Rejected)

        val checkpoints = MemoryCheckpointStore()
        CaptureSessionCoordinator.arm("session", plan, start.minusSeconds(5), checkpoints)
        val differentPlan = plan.copy(endsAtUtc = plan.endsAtUtc.plusSeconds(1))
        val incompatible = loader(MemoryPlanStore(differentPlan), checkpoints).load()
        assertTrue(incompatible is CaptureRecoveryBundleResult.Rejected)
    }

    @Test
    fun clearAttemptsBothStores() {
        val plans = MemoryPlanStore(plan)
        val checkpoints = MemoryCheckpointStore()
        CaptureSessionCoordinator.arm("session", plan, start.minusSeconds(5), checkpoints)

        assertTrue(CaptureRecoveryBundleLoader(plans, checkpoints).clear())
        assertTrue(plans.read() is CapturePlanReadResult.Missing)
        assertTrue(checkpoints.read() is CheckpointReadResult.Missing)
    }

    private fun loader(
        plans: CapturePlanStore = MemoryPlanStore(),
        checkpoints: CaptureCheckpointStore = MemoryCheckpointStore(),
    ) = CaptureRecoveryBundleLoader(plans, checkpoints)

    private class MemoryPlanStore(initial: CapturePlan? = null) : CapturePlanStore {
        private var plan = initial
        override fun write(plan: CapturePlan) { this.plan = plan }
        override fun read(): CapturePlanReadResult = plan
            ?.let(CapturePlanReadResult::Loaded)
            ?: CapturePlanReadResult.Missing
        override fun clear(): Boolean { plan = null; return true }
    }

    private class MemoryCheckpointStore : CaptureCheckpointStore {
        private var checkpoint: CaptureSessionCheckpoint? = null
        override fun write(checkpoint: CaptureSessionCheckpoint) { this.checkpoint = checkpoint }
        override fun read(): CheckpointReadResult = checkpoint
            ?.let(CheckpointReadResult::Loaded)
            ?: CheckpointReadResult.Missing
        override fun clear(): Boolean { checkpoint = null; return true }
    }

    private data object CorruptPlanStore : CapturePlanStore {
        override fun write(plan: CapturePlan) = Unit
        override fun read(): CapturePlanReadResult = CapturePlanReadResult.Corrupt("bad data")
        override fun clear(): Boolean = true
    }
}
