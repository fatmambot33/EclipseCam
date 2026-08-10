package com.fatmambo33.eclipsecam.media

import com.fatmambo33.eclipsecam.capture.CapturePhase

/** Why a capture was selected for manual best-frame review. */
enum class BestFrameReviewReason {
    TOTALITY,
    CONTACT_BURST,
    PARTIAL_PHASE,
}

/** One deterministic original-capture candidate for local manual review. */
data class BestFrameReviewCandidate(
    val asset: LocalSessionAsset,
    val phase: CapturePhase,
    val reason: BestFrameReviewReason,
)

/**
 * Produces a small phase-balanced shortlist of original captures for manual best-frame review.
 *
 * This helper deliberately does not claim image-quality scoring. It uses only persisted capture-plan
 * phase and instruction metadata, works for incomplete sessions, and never reads, uploads, or
 * modifies image bytes. Candidates are ordered with totality first, then contact bursts, then
 * partial-phase captures. Within each phase, evenly distributed captures are selected so the user
 * can inspect representative points instead of scrolling the entire session.
 */
class LocalBestFrameReviewAssistant(
    private val maxCandidatesPerPhase: Int = 3,
) {
    init {
        require(maxCandidatesPerPhase > 0) { "maxCandidatesPerPhase must be positive" }
    }

    fun shortlist(session: LocalCaptureSession): List<BestFrameReviewCandidate> =
        PHASE_PRIORITY.flatMap { phase ->
            val captures = session.captures
                .filter { it.phase == phase && it.instructionIndex != null }
                .sortedWith(
                    compareBy<LocalSessionAsset> { it.instructionIndex }
                        .thenBy { it.file.name },
                )

            selectRepresentativeCaptures(captures).map { asset ->
                BestFrameReviewCandidate(
                    asset = asset,
                    phase = phase,
                    reason = phase.toReviewReason(),
                )
            }
        }

    private fun selectRepresentativeCaptures(
        captures: List<LocalSessionAsset>,
    ): List<LocalSessionAsset> {
        if (captures.size <= maxCandidatesPerPhase) return captures
        if (maxCandidatesPerPhase == 1) return listOf(captures[captures.lastIndex / 2])

        return (0 until maxCandidatesPerPhase)
            .map { slot ->
                val index = ((slot.toDouble() * captures.lastIndex) / (maxCandidatesPerPhase - 1))
                    .toInt()
                captures[index]
            }
            .distinctBy { it.file.path }
    }

    private companion object {
        val PHASE_PRIORITY = listOf(
            CapturePhase.TOTALITY,
            CapturePhase.CONTACT_BURST,
            CapturePhase.PARTIAL,
        )
    }
}

private fun CapturePhase.toReviewReason(): BestFrameReviewReason = when (this) {
    CapturePhase.TOTALITY -> BestFrameReviewReason.TOTALITY
    CapturePhase.CONTACT_BURST -> BestFrameReviewReason.CONTACT_BURST
    CapturePhase.PARTIAL -> BestFrameReviewReason.PARTIAL_PHASE
}
