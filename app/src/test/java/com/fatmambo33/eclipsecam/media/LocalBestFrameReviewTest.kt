package com.fatmambo33.eclipsecam.media

import com.fatmambo33.eclipsecam.capture.CapturePhase
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBestFrameReviewTest {
    private val assistant = LocalBestFrameReviewAssistant(maxCandidatesPerPhase = 3)

    @Test
    fun prioritizesTotalityThenContactThenPartial() {
        val session = session(
            asset("partial.jpg", CapturePhase.PARTIAL, 1),
            asset("contact.jpg", CapturePhase.CONTACT_BURST, 2),
            asset("totality.jpg", CapturePhase.TOTALITY, 3),
        )

        val candidates = assistant.shortlist(session)

        assertEquals(
            listOf(CapturePhase.TOTALITY, CapturePhase.CONTACT_BURST, CapturePhase.PARTIAL),
            candidates.map(BestFrameReviewCandidate::phase),
        )
        assertEquals(BestFrameReviewReason.TOTALITY, candidates.first().reason)
    }

    @Test
    fun selectsEvenlyDistributedRepresentativesWithinEachPhase() {
        val session = session(
            *List(7) { index ->
                asset("totality-$index.jpg", CapturePhase.TOTALITY, index)
            }.toTypedArray(),
        )

        val candidates = assistant.shortlist(session)

        assertEquals(listOf(0, 3, 6), candidates.map { it.asset.instructionIndex })
    }

    @Test
    fun ignoresCapturesWithoutPersistedPhaseOrInstructionMetadata() {
        val session = session(
            asset("known.jpg", CapturePhase.CONTACT_BURST, 8),
            asset("unknown-phase.jpg", null, 9),
            asset("unknown-instruction.jpg", CapturePhase.TOTALITY, null),
        )

        val candidates = assistant.shortlist(session)

        assertEquals(listOf("known.jpg"), candidates.map { it.asset.file.name })
    }

    @Test
    fun supportsIncompleteSessionsWithoutChangingOriginals() {
        val source = asset("partial.jpg", CapturePhase.PARTIAL, 4)
        val session = session(source, status = LocalSessionStatus.INTERRUPTED)

        val candidates = assistant.shortlist(session)

        assertEquals(1, candidates.size)
        assertEquals(source, candidates.single().asset)
        assertTrue(session.incomplete)
        assertEquals(listOf(source), session.captures)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyCandidateLimit() {
        LocalBestFrameReviewAssistant(maxCandidatesPerPhase = 0)
    }

    private fun asset(
        name: String,
        phase: CapturePhase?,
        instructionIndex: Int?,
    ) = LocalSessionAsset(
        file = File(name),
        sizeBytes = 1024,
        modifiedAtUtc = Instant.parse("2026-08-12T17:45:00Z"),
        phase = phase,
        instructionIndex = instructionIndex,
    )

    private fun session(
        vararg assets: LocalSessionAsset,
        status: LocalSessionStatus = LocalSessionStatus.COMPLETE,
    ) = LocalCaptureSession(
        sessionId = "test-session",
        directory = File("test-session"),
        assets = assets.toList(),
        capturedAtUtc = Instant.parse("2026-08-12T17:00:00Z"),
        modifiedAtUtc = Instant.parse("2026-08-12T18:00:00Z"),
        status = status,
        phaseCounts = assets.mapNotNull(LocalSessionAsset::phase).groupingBy { it }.eachCount(),
    )
}
