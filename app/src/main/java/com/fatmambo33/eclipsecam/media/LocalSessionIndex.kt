package com.fatmambo33.eclipsecam.media

import java.io.File
import java.time.Instant

/** One local JPEG owned by a capture session. */
data class LocalSessionAsset(
    val file: File,
    val sizeBytes: Long,
    val modifiedAtUtc: Instant,
)

/** A locally stored capture session, including interrupted or incomplete output. */
data class LocalCaptureSession(
    val sessionId: String,
    val directory: File,
    val assets: List<LocalSessionAsset>,
    val modifiedAtUtc: Instant,
    val incomplete: Boolean,
)

/**
 * Builds a deterministic, local-only index from app-private capture output directories.
 *
 * Empty placeholder files are ignored because capture reservations may exist briefly before CameraX
 * writes them. Unknown files are ignored. A session is incomplete when it contains no readable JPEG
 * assets or when a caller-provided completion marker is absent.
 */
class LocalSessionIndex(
    private val rootDirectory: File,
    private val completionMarkerName: String = "session.complete",
) {
    init {
        require(completionMarkerName.isNotBlank()) { "Completion marker name must not be blank." }
    }

    fun listSessions(): List<LocalCaptureSession> {
        val directories = rootDirectory.listFiles()
            ?.asSequence()
            ?.filter(File::isDirectory)
            ?.toList()
            .orEmpty()

        return directories.mapNotNull(::indexSession)
            .sortedWith(
                compareByDescending<LocalCaptureSession> { it.modifiedAtUtc }
                    .thenBy { it.sessionId },
            )
    }

    private fun indexSession(directory: File): LocalCaptureSession? {
        val sessionId = directory.name.trim()
        if (sessionId.isBlank()) return null

        val assets = directory.listFiles()
            ?.asSequence()
            ?.filter { file ->
                file.isFile &&
                    file.length() > 0L &&
                    file.extension.equals("jpg", ignoreCase = true)
            }
            ?.map { file ->
                LocalSessionAsset(
                    file = file,
                    sizeBytes = file.length(),
                    modifiedAtUtc = Instant.ofEpochMilli(file.lastModified()),
                )
            }
            ?.sortedWith(compareBy<LocalSessionAsset> { it.file.name }.thenBy { it.modifiedAtUtc })
            ?.toList()
            .orEmpty()

        val directoryModified = Instant.ofEpochMilli(directory.lastModified())
        val modifiedAt = assets.maxOfOrNull(LocalSessionAsset::modifiedAtUtc) ?: directoryModified
        val complete = File(directory, completionMarkerName).isFile

        return LocalCaptureSession(
            sessionId = sessionId,
            directory = directory,
            assets = assets,
            modifiedAtUtc = modifiedAt,
            incomplete = !complete || assets.isEmpty(),
        )
    }
}
