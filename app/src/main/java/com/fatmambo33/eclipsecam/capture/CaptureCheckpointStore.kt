package com.fatmambo33.eclipsecam.capture

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.Base64

sealed interface CheckpointReadResult {
    data object Missing : CheckpointReadResult
    data class Loaded(val checkpoint: CaptureSessionCheckpoint) : CheckpointReadResult
    data class Corrupt(val reason: String) : CheckpointReadResult
}

interface CaptureCheckpointStore {
    fun write(checkpoint: CaptureSessionCheckpoint)
    fun read(): CheckpointReadResult
    fun clear(): Boolean
}

object CaptureCheckpointCodec {
    private const val VERSION = "1"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(checkpoint: CaptureSessionCheckpoint): String = buildString {
        appendLine("version=$VERSION")
        appendLine("sessionId=${encodeText(checkpoint.sessionId)}")
        appendLine("planStartsAtUtc=${checkpoint.planStartsAtUtc}")
        appendLine("planEndsAtUtc=${checkpoint.planEndsAtUtc}")
        appendLine("nextInstructionIndex=${checkpoint.nextInstructionIndex}")
        appendLine("capturedCount=${checkpoint.capturedCount}")
        appendLine("skippedCount=${checkpoint.skippedCount}")
        appendLine("status=${checkpoint.status.name}")
        appendLine("updatedAtUtc=${checkpoint.updatedAtUtc}")
        appendLine("failureReason=${checkpoint.failureReason?.let(::encodeText).orEmpty()}")
    }

    fun decode(content: String): CheckpointReadResult {
        return try {
            val values = content.lineSequence()
                .filter(String::isNotBlank)
                .associate { line ->
                    val separator = line.indexOf('=')
                    require(separator > 0) { "Malformed checkpoint line." }
                    line.substring(0, separator) to line.substring(separator + 1)
                }
            require(values["version"] == VERSION) { "Unsupported checkpoint version." }

            val failureReason = values.required("failureReason")
                .takeIf(String::isNotEmpty)
                ?.let(::decodeText)

            CheckpointReadResult.Loaded(
                CaptureSessionCheckpoint(
                    sessionId = decodeText(values.required("sessionId")),
                    planStartsAtUtc = Instant.parse(values.required("planStartsAtUtc")),
                    planEndsAtUtc = Instant.parse(values.required("planEndsAtUtc")),
                    nextInstructionIndex = values.required("nextInstructionIndex").toInt(),
                    capturedCount = values.required("capturedCount").toInt(),
                    skippedCount = values.required("skippedCount").toInt(),
                    status = CaptureSessionStatus.valueOf(values.required("status")),
                    updatedAtUtc = Instant.parse(values.required("updatedAtUtc")),
                    failureReason = failureReason,
                ),
            )
        } catch (error: RuntimeException) {
            CheckpointReadResult.Corrupt(error.message ?: "Invalid capture checkpoint.")
        }
    }

    private fun Map<String, String>.required(key: String): String =
        get(key) ?: throw IllegalArgumentException("Missing checkpoint field: $key")

    private fun encodeText(value: String): String =
        encoder.encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decodeText(value: String): String =
        decoder.decode(value).toString(Charsets.UTF_8)
}

class FileCaptureCheckpointStore(
    private val checkpointFile: File,
) : CaptureCheckpointStore {
    override fun write(checkpoint: CaptureSessionCheckpoint) {
        val parent = checkpointFile.absoluteFile.parentFile
            ?: throw IllegalStateException("Capture checkpoint has no parent directory.")
        check(parent.exists() || parent.mkdirs()) { "Unable to create capture checkpoint directory." }

        val temporaryFile = File(parent, "${checkpointFile.name}.tmp")
        temporaryFile.writeText(CaptureCheckpointCodec.encode(checkpoint), Charsets.UTF_8)
        try {
            Files.move(
                temporaryFile.toPath(),
                checkpointFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporaryFile.toPath(),
                checkpointFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            temporaryFile.delete()
        }
    }

    override fun read(): CheckpointReadResult {
        if (!checkpointFile.exists()) return CheckpointReadResult.Missing
        return try {
            CaptureCheckpointCodec.decode(checkpointFile.readText(Charsets.UTF_8))
        } catch (error: RuntimeException) {
            CheckpointReadResult.Corrupt(error.message ?: "Unable to read capture checkpoint.")
        }
    }

    override fun clear(): Boolean = !checkpointFile.exists() || checkpointFile.delete()
}
