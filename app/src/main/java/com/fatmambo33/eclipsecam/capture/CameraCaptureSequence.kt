package com.fatmambo33.eclipsecam.capture

import java.time.Instant

sealed interface CaptureExposureStep {
    data class Compensation(val steps: Int) : CaptureExposureStep
    data class RelativeManualEv(val offset: Int) : CaptureExposureStep
}

data class CameraCaptureFrame(
    val ordinal: Int,
    val exposure: CaptureExposureStep,
    val output: CaptureOutput,
)

data class CameraCaptureSequence(
    val request: CameraCaptureRequest,
    val frames: List<CameraCaptureFrame>,
) {
    init {
        require(frames.isNotEmpty())
        require(frames.map(CameraCaptureFrame::ordinal) == frames.indices.toList())
    }
}

/**
 * Reserves every local output required by one camera request as a single operation.
 *
 * A bracket must never begin with only a subset of its destinations available. When any reservation
 * fails, all files already reserved for the sequence are released before the error is propagated.
 */
class CameraCaptureSequencePlanner(
    private val outputAllocator: CaptureOutputAllocator,
) {
    fun plan(
        sessionId: String,
        instructionIndex: Int,
        capturedAtUtc: Instant,
        request: CameraCaptureRequest,
    ): CameraCaptureSequence {
        val exposures = when (val program = request.exposureProgram) {
            is CaptureExposureProgram.ExposureCompensation ->
                program.steps.map(CaptureExposureStep::Compensation)
            is CaptureExposureProgram.MeteredManualBracket ->
                program.relativeEvOffsets.map(CaptureExposureStep::RelativeManualEv)
        }

        val reserved = mutableListOf<CaptureOutput>()
        return try {
            val frames = exposures.mapIndexed { ordinal, exposure ->
                val output = outputAllocator.reserve(sessionId, instructionIndex, capturedAtUtc)
                reserved += output
                CameraCaptureFrame(ordinal, exposure, output)
            }
            CameraCaptureSequence(request, frames)
        } catch (error: RuntimeException) {
            reserved.forEach(outputAllocator::release)
            throw error
        }
    }

    fun release(sequence: CameraCaptureSequence): Boolean =
        sequence.frames.map { outputAllocator.release(it.output) }.all { it }
}
