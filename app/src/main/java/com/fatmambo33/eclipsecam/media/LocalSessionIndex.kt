package com.fatmambo33.eclipsecam.media

import com.fatmambo33.eclipsecam.capture.CapturePhase
import com.fatmambo33.eclipsecam.capture.CapturePlan
import com.fatmambo33.eclipsecam.capture.CaptureSessionCheckpoint
import com.fatmambo33.eclipsecam.capture.CaptureSessionJournal
import com.fatmambo33.eclipsecam.capture.CaptureSessionStatus
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

enum class LocalSessionStatus { COMPLETE, PAUSED, FAILED, INTERRUPTED }

enum class LocalSessionAssetKind {
    ORIGINAL_CAPTURE,
    TIMELAPSE,
    MONTAGE,
    CAPTURE_REPORT,
    GENERATED,
}

/** One valid local asset owned by a capture session. */
data class LocalSessionAsset(
    val file: File,
    val sizeBytes: Long,
    val modifiedAtUtc: Instant,
    val kind: LocalSessionAssetKind = LocalSessionAssetKind.ORIGINAL_CAPTURE,
    val phase: CapturePhase? = null,
    val instructionIndex: Int? = null,
)

/** A locally stored capture session, including interrupted or incomplete output. */
data class LocalCaptureSession(
    val sessionId: String,
    val directory: File,
    val assets: List<LocalSessionAsset>,
    val capturedAtUtc: Instant,
    val modifiedAtUtc: Instant,
    val status: LocalSessionStatus,
    val phaseCounts: Map<CapturePhase, Int>,
) {
    val captures: List<LocalSessionAsset>
        get() = assets.filter { it.kind == LocalSessionAssetKind.ORIGINAL_CAPTURE }

    val generatedAssets: List<LocalSessionAsset>
        get() = assets.filter { it.kind != LocalSessionAssetKind.ORIGINAL_CAPTURE }

    val incomplete: Boolean
        get() = status != LocalSessionStatus.COMPLETE
}

/**
 * Persists Gallery-only session metadata beside app-private capture output.
 *
 * The durable capture checkpoint remains the source of truth for capture recovery. This journal is
 * a restart-safe projection for historical Gallery browsing, so a failure to update it must never
 * compromise the capture checkpoint or camera execution path.
 */
class FileLocalCaptureSessionJournal(
    private val rootDirectory: File,
) : CaptureSessionJournal {
    override fun record(plan: CapturePlan, checkpoint: CaptureSessionCheckpoint) {
        runCatching {
            val directory = File(rootDirectory, safeSessionId(checkpoint.sessionId))
            check(directory.isDirectory || directory.mkdirs()) {
                "Unable to create local Gallery session directory."
            }
            val planFile = File(directory, PLAN_INDEX_FILE)
            if (!planFile.isFile) {
                atomicWrite(planFile, encodePlan(plan))
            }
            atomicWrite(File(directory, STATE_FILE), encodeState(checkpoint))

            val completionMarker = File(directory, COMPLETE_MARKER)
            if (checkpoint.status == CaptureSessionStatus.COMPLETED) {
                if (!completionMarker.exists()) completionMarker.writeText("complete\n")
            } else if (completionMarker.exists()) {
                completionMarker.delete()
            }
        }
    }

    private fun encodePlan(plan: CapturePlan): String = buildString {
        appendLine("version=1")
        appendLine("startsAtUtc=${plan.startsAtUtc}")
        appendLine("endsAtUtc=${plan.endsAtUtc}")
        plan.instructions.forEachIndexed { index, instruction ->
            appendLine("instruction.$index=${instruction.phase.name}")
        }
    }

    private fun encodeState(checkpoint: CaptureSessionCheckpoint): String = buildString {
        appendLine("version=1")
        appendLine("status=${checkpoint.status.name}")
        appendLine("updatedAtUtc=${checkpoint.updatedAtUtc}")
        appendLine("capturedCount=${checkpoint.capturedCount}")
        appendLine("skippedCount=${checkpoint.skippedCount}")
    }

    private fun atomicWrite(destination: File, content: String) {
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        temporary.writeText(content, Charsets.UTF_8)
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            temporary.delete()
        }
    }

    private fun safeSessionId(sessionId: String): String {
        val safe = sessionId.replace(UNSAFE_FILENAME, "_").trim('_')
        require(safe.isNotBlank()) { "Session ID must contain a filename-safe character." }
        return safe
    }

    private companion object {
        val UNSAFE_FILENAME = Regex("[^A-Za-z0-9._-]")
    }
}

/**
 * Builds a deterministic, local-only index from app-private capture output directories.
 *
 * Empty placeholders and unsupported generated files are ignored. Corrupt metadata falls back to
 * observable disk state instead of hiding a session or crashing the Gallery. No path outside the
 * configured root is scanned.
 */
class LocalSessionIndex(
    private val rootDirectory: File,
) {
    fun listSessions(): List<LocalCaptureSession> {
        if (!rootDirectory.exists()) return emptyList()
        require(rootDirectory.isDirectory) { "Capture session root is not a directory." }

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

        val planMetadata = readPlanMetadata(directory)
        val stateMetadata = readStateMetadata(directory)
        val originalAssets = directory.listFiles()
            ?.asSequence()
            ?.filter(::isReadableJpeg)
            ?.map { file ->
                val instructionIndex = captureInstructionIndex(file.name)
                file.toAsset(
                    kind = LocalSessionAssetKind.ORIGINAL_CAPTURE,
                    phase = instructionIndex?.let { planMetadata?.instructionPhases?.get(it) },
                    instructionIndex = instructionIndex,
                )
            }
            ?.sortedBy { it.file.name }
            ?.toList()
            .orEmpty()
        val generatedAssets = File(directory, GENERATED_DIRECTORY).listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.length() > 0L }
            ?.mapNotNull { file ->
                generatedKind(file)?.let { kind -> file.toAsset(kind = kind) }
            }
            ?.sortedBy { it.file.name }
            ?.toList()
            .orEmpty()
        val assets = originalAssets + generatedAssets

        val directoryModifiedAt = Instant.ofEpochMilli(directory.lastModified())
        val capturedAtUtc = planMetadata?.startsAtUtc
            ?: originalAssets.minOfOrNull(LocalSessionAsset::modifiedAtUtc)
            ?: generatedAssets.minOfOrNull(LocalSessionAsset::modifiedAtUtc)
            ?: directoryModifiedAt
        val modifiedAtUtc = buildList {
            addAll(assets.map(LocalSessionAsset::modifiedAtUtc))
            stateMetadata?.updatedAtUtc?.let(::add)
        }.maxOrNull() ?: directoryModifiedAt

        val status = stateMetadata?.status?.toLocalStatus()
            ?: if (File(directory, COMPLETE_MARKER).isFile) {
                LocalSessionStatus.COMPLETE
            } else {
                LocalSessionStatus.INTERRUPTED
            }
        val phaseCounts = originalAssets
            .mapNotNull(LocalSessionAsset::phase)
            .groupingBy { it }
            .eachCount()
            .toSortedMap(compareBy(CapturePhase::ordinal))

        return LocalCaptureSession(
            sessionId = sessionId,
            directory = directory,
            assets = assets,
            capturedAtUtc = capturedAtUtc,
            modifiedAtUtc = modifiedAtUtc,
            status = status,
            phaseCounts = phaseCounts,
        )
    }

    private fun readPlanMetadata(directory: File): PlanMetadata? = runCatching {
        val values = readValues(File(directory, PLAN_INDEX_FILE)) ?: return@runCatching null
        require(values["version"] == "1")
        val phases = values.mapNotNull { (key, value) ->
            if (!key.startsWith(INSTRUCTION_PREFIX)) return@mapNotNull null
            val index = key.removePrefix(INSTRUCTION_PREFIX).toIntOrNull() ?: return@mapNotNull null
            val phase = runCatching { CapturePhase.valueOf(value) }.getOrNull() ?: return@mapNotNull null
            index to phase
        }.toMap()
        PlanMetadata(
            startsAtUtc = Instant.parse(values.getValue("startsAtUtc")),
            instructionPhases = phases,
        )
    }.getOrNull()

    private fun readStateMetadata(directory: File): StateMetadata? = runCatching {
        val values = readValues(File(directory, STATE_FILE)) ?: return@runCatching null
        require(values["version"] == "1")
        StateMetadata(
            status = CaptureSessionStatus.valueOf(values.getValue("status")),
            updatedAtUtc = Instant.parse(values.getValue("updatedAtUtc")),
        )
    }.getOrNull()

    private fun readValues(file: File): Map<String, String>? {
        if (!file.isFile) return null
        return file.readLines(Charsets.UTF_8)
            .asSequence()
            .filter(String::isNotBlank)
            .associate { line ->
                val separator = line.indexOf('=')
                require(separator > 0) { "Malformed local session metadata." }
                line.substring(0, separator) to line.substring(separator + 1)
            }
    }

    private fun isReadableJpeg(file: File): Boolean =
        file.isFile &&
            file.length() > 0L &&
            (file.extension.equals("jpg", ignoreCase = true) ||
                file.extension.equals("jpeg", ignoreCase = true))

    private fun generatedKind(file: File): LocalSessionAssetKind? = when {
        file.extension.equals("mp4", ignoreCase = true) &&
            file.nameWithoutExtension.contains("timelapse", ignoreCase = true) ->
            LocalSessionAssetKind.TIMELAPSE
        file.extension.equals("jpg", ignoreCase = true) &&
            file.nameWithoutExtension.contains("montage", ignoreCase = true) ->
            LocalSessionAssetKind.MONTAGE
        file.extension.equals("jpeg", ignoreCase = true) &&
            file.nameWithoutExtension.contains("montage", ignoreCase = true) ->
            LocalSessionAssetKind.MONTAGE
        file.extension.equals("json", ignoreCase = true) &&
            file.nameWithoutExtension.contains("report", ignoreCase = true) ->
            LocalSessionAssetKind.CAPTURE_REPORT
        file.extension.lowercase() in GENERATED_EXTENSIONS -> LocalSessionAssetKind.GENERATED
        else -> null
    }

    private fun File.toAsset(
        kind: LocalSessionAssetKind,
        phase: CapturePhase? = null,
        instructionIndex: Int? = null,
    ): LocalSessionAsset = LocalSessionAsset(
        file = this,
        sizeBytes = length(),
        modifiedAtUtc = Instant.ofEpochMilli(lastModified()),
        kind = kind,
        phase = phase,
        instructionIndex = instructionIndex,
    )

    private fun captureInstructionIndex(filename: String): Int? =
        CAPTURE_INDEX.find(filename)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun CaptureSessionStatus.toLocalStatus(): LocalSessionStatus = when (this) {
        CaptureSessionStatus.COMPLETED -> LocalSessionStatus.COMPLETE
        CaptureSessionStatus.PAUSED -> LocalSessionStatus.PAUSED
        CaptureSessionStatus.FAILED -> LocalSessionStatus.FAILED
        CaptureSessionStatus.ARMED,
        CaptureSessionStatus.RUNNING,
        -> LocalSessionStatus.INTERRUPTED
    }

    private data class PlanMetadata(
        val startsAtUtc: Instant,
        val instructionPhases: Map<Int, CapturePhase>,
    )

    private data class StateMetadata(
        val status: CaptureSessionStatus,
        val updatedAtUtc: Instant,
    )

    private companion object {
        val CAPTURE_INDEX = Regex("^(\\d{6})_")
        const val INSTRUCTION_PREFIX = "instruction."
        val GENERATED_EXTENSIONS = setOf("jpg", "jpeg", "png", "mp4", "json")
    }
}

private const val COMPLETE_MARKER = "session.complete"
private const val PLAN_INDEX_FILE = "session.plan-index"
private const val STATE_FILE = "session.state"
private const val GENERATED_DIRECTORY = "generated"
