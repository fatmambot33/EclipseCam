package com.fatmambo33.eclipsecam.capture

import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A reserved local destination for one captured JPEG. */
data class CaptureOutput(
    val sessionDirectory: File,
    val imageFile: File,
)

/**
 * Allocates app-private, collision-safe JPEG destinations without embedding location data.
 *
 * The allocator creates one directory per session and reserves each filename atomically. CameraX
 * must write only to a successfully reserved file. Failed captures should call [release] so empty
 * placeholders do not appear in later gallery indexing.
 */
class CaptureOutputStore(
    private val rootDirectory: File,
) {
    fun reserve(
        sessionId: String,
        instructionIndex: Int,
        capturedAtUtc: Instant,
    ): CaptureOutput {
        require(sessionId.isNotBlank()) { "Session ID must not be blank." }
        require(instructionIndex >= 0) { "Instruction index must be non-negative." }

        val safeSessionId = sessionId.replace(UNSAFE_FILENAME, "_").trim('_')
        require(safeSessionId.isNotBlank()) { "Session ID must contain a filename-safe character." }

        val sessionDirectory = File(rootDirectory, safeSessionId)
        check(sessionDirectory.isDirectory || sessionDirectory.mkdirs()) {
            "Unable to create capture session directory."
        }

        val timestamp = FILE_TIMESTAMP.format(capturedAtUtc)
        val stem = "%06d_%s".format(instructionIndex, timestamp)
        for (suffix in 0 until MAX_COLLISION_ATTEMPTS) {
            val filename = if (suffix == 0) "$stem.jpg" else "${stem}_$suffix.jpg"
            val candidate = File(sessionDirectory, filename)
            if (candidate.createNewFile()) {
                return CaptureOutput(sessionDirectory, candidate)
            }
        }
        error("Unable to reserve a unique capture output file.")
    }

    fun release(output: CaptureOutput): Boolean =
        !output.imageFile.exists() || output.imageFile.delete()

    companion object {
        private val UNSAFE_FILENAME = Regex("[^A-Za-z0-9._-]")
        private val FILE_TIMESTAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'").withZone(ZoneOffset.UTC)
        private const val MAX_COLLISION_ATTEMPTS = 1_000
    }
}
