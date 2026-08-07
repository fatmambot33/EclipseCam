package com.fatmambo33.eclipsecam.media

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One original capture selected for deterministic timelapse rendering. */
data class TimelapseFrame(
    val file: File,
    val captureTimeUtc: Instant,
    val instructionIndex: Int,
)

/** Lightweight boundary for rejecting corrupt/unreadable image inputs before export starts. */
fun interface TimelapseFrameProbe {
    fun isReadable(file: File): Boolean
}

/** Platform encoder boundary; implementations write only to the supplied temporary output. */
fun interface TimelapseVideoEncoder {
    suspend fun encode(
        frames: List<TimelapseFrame>,
        output: File,
        onProgress: (Int) -> Unit,
    )
}

sealed interface TimelapseRenderResult {
    data class Completed(
        val output: File,
        val frameCount: Int,
    ) : TimelapseRenderResult

    data class NoFrames(val reason: String) : TimelapseRenderResult
    data class Failed(val reason: String) : TimelapseRenderResult
}

/**
 * Selects original captures using durable capture time and instruction index.
 *
 * Production capture filenames encode both values. Legacy or copied assets fall back to their
 * persisted modified time and a maximal instruction index while retaining a stable filename tie
 * break. Generated outputs are never considered inputs.
 */
object TimelapseFrameSelector {
    fun select(session: LocalCaptureSession): List<TimelapseFrame> = session.captures
        .asSequence()
        .filter { asset ->
            asset.file.isFile &&
                asset.sizeBytes > 0L &&
                (asset.file.extension.equals("jpg", ignoreCase = true) ||
                    asset.file.extension.equals("jpeg", ignoreCase = true))
        }
        .map { asset ->
            TimelapseFrame(
                file = asset.file,
                captureTimeUtc = captureTimeFromFilename(asset.file.name) ?: asset.modifiedAtUtc,
                instructionIndex = instructionIndex(asset.file.name) ?: Int.MAX_VALUE,
            )
        }
        .sortedWith(
            compareBy<TimelapseFrame>(TimelapseFrame::captureTimeUtc)
                .thenBy(TimelapseFrame::instructionIndex)
                .thenBy { it.file.name },
        )
        .toList()

    private fun instructionIndex(filename: String): Int? =
        CAPTURE_INDEX.find(filename)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun captureTimeFromFilename(filename: String): Instant? {
        val match = CAPTURE_TIME.find(filename) ?: return null
        return runCatching {
            Instant.parse(
                "${match.groupValues[2]}:${match.groupValues[3]}:${match.groupValues[4]}Z",
            )
        }.getOrNull()
    }

    private val CAPTURE_INDEX = Regex("^(\\d{6})_")
    private val CAPTURE_TIME = Regex(
        "^(\\d{6})_(\\d{4}-\\d{2}-\\d{2}T\\d{2})-(\\d{2})-(\\d{2}(?:\\.\\d+)?)Z",
    )
}

/**
 * Orchestrates local timelapse rendering without ever modifying original captures.
 *
 * A render is written to `generated/timelapse.rendering.mp4` and published as
 * `generated/timelapse.mp4` only after the encoder completes and produces a non-empty file. A
 * previous complete output remains intact until replacement succeeds. Failed and cancelled renders
 * remove only their temporary output.
 */
class LocalTimelapseGenerator(
    private val encoder: TimelapseVideoEncoder,
    private val frameProbe: TimelapseFrameProbe,
) {
    suspend fun render(
        session: LocalCaptureSession,
        onProgress: (Int) -> Unit = {},
    ): TimelapseRenderResult {
        val frames = withContext(Dispatchers.IO) {
            TimelapseFrameSelector.select(session).filter { frameProbe.isReadable(it.file) }
        }
        if (frames.isEmpty()) {
            return TimelapseRenderResult.NoFrames("No readable original JPEG captures are available.")
        }

        val generatedDirectory = File(session.directory, GENERATED_DIRECTORY)
        if (!generatedDirectory.isDirectory && !generatedDirectory.mkdirs()) {
            return TimelapseRenderResult.Failed("Unable to create the local generated-media directory.")
        }
        val temporary = File(generatedDirectory, TEMP_OUTPUT)
        val finalOutput = File(generatedDirectory, FINAL_OUTPUT)
        temporary.delete()

        return try {
            onProgress(0)
            encoder.encode(frames, temporary) { progress ->
                onProgress(progress.coerceIn(0, 99))
            }
            if (!temporary.isFile || temporary.length() <= 0L) {
                temporary.delete()
                TimelapseRenderResult.Failed("The video encoder completed without a playable output file.")
            } else {
                publish(temporary, finalOutput)
                onProgress(100)
                TimelapseRenderResult.Completed(finalOutput, frames.size)
            }
        } catch (cancelled: CancellationException) {
            temporary.delete()
            throw cancelled
        } catch (error: Throwable) {
            temporary.delete()
            TimelapseRenderResult.Failed(error.message ?: "Local timelapse rendering failed.")
        }
    }

    private fun publish(temporary: File, finalOutput: File) {
        try {
            Files.move(
                temporary.toPath(),
                finalOutput.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporary.toPath(),
                finalOutput.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private companion object {
        const val GENERATED_DIRECTORY = "generated"
        const val TEMP_OUTPUT = "timelapse.rendering.mp4"
        const val FINAL_OUTPUT = "timelapse.mp4"
    }
}
