package com.fatmambo33.eclipsecam.capture

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureServiceRecoveryBootstrapTest {
    private val start = Instant.parse("2026-08-12T17:00:00Z")
    private val plan = CapturePlan(
        startsAtUtc = start,
        endsAtUtc = start.plusSeconds(60),
        instructions = listOf(
            CaptureInstruction(
                instantUtc = start,
                phase = CapturePhase.PARTIAL,
                exposureStrategy = ExposureStrategy.FILTERED_PARTIAL,
            ),
        ),
    )

    @Test
    fun missingBundleDoesNotCreateServiceState() {
        val result = bootstrap(InMemoryPlanStore(), InMemoryCheckpointStore()).load()

        assertTrue(result is CaptureServiceBootstrapResult.Missing)
    }

    @Test
    fun armedRunningAndPausedSessionsRecoverPaused() {
        listOf(CaptureSessionStatus.ARMED, CaptureSessionStatus.RUNNING, CaptureSessionStatus.PAUSED)
            .forEach { status ->
                val planStore = InMemoryPlanStore(plan)
                val checkpointStore = InMemoryCheckpointStore(checkpoint(status))

                val result = bootstrap(planStore, checkpointStore).load()
                    as CaptureServiceBootstrapResult.Ready

                assertEquals(CaptureServiceState.PAUSED, result.initialState)
                assertEquals(status, result.coordinator.snapshot().status)
                assertEquals(plan, result.plan)
            }
    }

    @Test
    fun partialOrIncompatibleBundleIsRejected() {
        val missingPlan = bootstrap(
            InMemoryPlanStore(),
            InMemoryCheckpointStore(checkpoint(CaptureSessionStatus.PAUSED)),
        ).load()
        val incompatible = bootstrap(
            InMemoryPlanStore(plan),
            InMemoryCheckpointStore(
                checkpoint(CaptureSessionStatus.PAUSED).copy(
                    planEndsAtUtc = plan.endsAtUtc.plusSeconds(1),
                ),
            ),
        ).load()

        assertTrue(missingPlan is CaptureServiceBootstrapResult.Rejected)
        assertTrue(incompatible is CaptureServiceBootstrapResult.Rejected)
    }

    @Test
    fun terminalSessionsAreNotResumable() {
        val completed = checkpoint(CaptureSessionStatus.COMPLETED).copy(
            nextInstructionIndex = 1,
            capturedCount = 1,
        )
        val failed = CaptureSessionCheckpoint(
            sessionId = "session",
            planStartsAtUtc = plan.startsAtUtc,
            planEndsAtUtc = plan.endsAtUtc,
            nextInstructionIndex = 0,
            capturedCount = 0,
            skippedCount = 0,
            status = CaptureSessionStatus.FAILED,
            updatedAtUtc = start,
            failureReason = "Camera failed",
        )

        listOf(completed, failed).forEach { terminalCheckpoint ->
            val result = bootstrap(
                InMemoryPlanStore(plan),
                InMemoryCheckpointStore(terminalCheckpoint),
            ).load()

            assertTrue(result is CaptureServiceBootstrapResult.Rejected)
        }
    }

    private fun checkpoint(status: CaptureSessionStatus) = CaptureSessionCheckpoint(
        sessionId = "session",
        planStartsAtUtc = plan.startsAtUtc,
        planEndsAtUtc = plan.endsAtUtc,
        nextInstructionIndex = 0,
        capturedCount = 0,
        skippedCount = 0,
        status = status,
        updatedAtUtc = start,
    )

    private fun bootstrap(
        planStore: CapturePlanStore,
        checkpointStore: CaptureCheckpointStore,
    ) = CaptureServiceRecoveryBootstrap(
        CaptureRecoveryBundleLoader(planStore, checkpointStore),
    )

    private class InMemoryPlanStore(
        private var plan: CapturePlan? = null,
    ) : CapturePlanStore {
        override fun write(plan: CapturePlan) {
            this.plan = plan
        }

        override fun read(): CapturePlanReadResult = plan
            ?.let(CapturePlanReadResult::Loaded)
            ?: CapturePlanReadResult.Missing

        override fun clear(): Boolean {
            plan = null
            return true
        }
    }

    private class InMemoryCheckpointStore(
        private var checkpoint: CaptureSessionCheckpoint? = null,
    ) : CaptureCheckpointStore {
        override fun write(checkpoint: CaptureSessionCheckpoint) {
            this.checkpoint = checkpoint
        }

        override fun read(): CheckpointReadResult = checkpoint
            ?.let(CheckpointReadResult::Loaded)
            ?: CheckpointReadResult.Missing

        override fun clear(): Boolean {
            checkpoint = null
            return true
        }
    }
}
