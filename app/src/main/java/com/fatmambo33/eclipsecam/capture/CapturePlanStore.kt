package com.fatmambo33.eclipsecam.capture

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

sealed interface CapturePlanReadResult {
    data object Missing : CapturePlanReadResult
    data class Loaded(val plan: CapturePlan) : CapturePlanReadResult
    data class Corrupt(val reason: String) : CapturePlanReadResult
}

interface CapturePlanStore {
    fun write(plan: CapturePlan)
    fun read(): CapturePlanReadResult
    fun clear(): Boolean
}

object CapturePlanCodec {
    private const val VERSION = "1"

    fun encode(plan: CapturePlan): String = buildString {
        appendLine("version=$VERSION")
        appendLine("startsAtUtc=${plan.startsAtUtc}")
        appendLine("endsAtUtc=${plan.endsAtUtc}")
        appendLine("instructionCount=${plan.instructions.size}")
        plan.instructions.forEachIndexed { index, instruction ->
            appendLine("instruction.$index=${instruction.instantUtc}|${instruction.phase.name}|${instruction.exposureStrategy.name}")
        }
    }

    fun decode(content: String): CapturePlanReadResult = try {
        val values = content.lineSequence().filter(String::isNotBlank).associate { line ->
            val separator = line.indexOf('=')
            require(separator > 0) { "Malformed capture plan line." }
            line.substring(0, separator) to line.substring(separator + 1)
        }
        require(values["version"] == VERSION) { "Unsupported capture plan version." }
        val count = values.required("instructionCount").toInt()
        require(count > 0) { "Capture plan must contain instructions." }
        val instructions = (0 until count).map { index ->
            val parts = values.required("instruction.$index").split('|')
            require(parts.size == 3) { "Malformed capture instruction: $index" }
            CaptureInstruction(
                instantUtc = Instant.parse(parts[0]),
                phase = CapturePhase.valueOf(parts[1]),
                exposureStrategy = ExposureStrategy.valueOf(parts[2]),
            )
        }
        require(values.keys.none { it.startsWith("instruction.") && it.removePrefix("instruction.").toIntOrNull()?.let { index -> index >= count } == true }) {
            "Capture plan contains unexpected instructions."
        }
        CapturePlanReadResult.Loaded(
            CapturePlan(
                startsAtUtc = Instant.parse(values.required("startsAtUtc")),
                endsAtUtc = Instant.parse(values.required("endsAtUtc")),
                instructions = instructions,
            ),
        )
    } catch (error: RuntimeException) {
        CapturePlanReadResult.Corrupt(error.message ?: "Invalid capture plan.")
    }

    private fun Map<String, String>.required(key: String): String =
        get(key) ?: throw IllegalArgumentException("Missing capture plan field: $key")
}

class FileCapturePlanStore(private val planFile: File) : CapturePlanStore {
    override fun write(plan: CapturePlan) {
        val parent = planFile.absoluteFile.parentFile
            ?: throw IllegalStateException("Capture plan has no parent directory.")
        check(parent.exists() || parent.mkdirs()) { "Unable to create capture plan directory." }
        val temporaryFile = File(parent, "${planFile.name}.tmp")
        temporaryFile.writeText(CapturePlanCodec.encode(plan), Charsets.UTF_8)
        try {
            Files.move(
                temporaryFile.toPath(),
                planFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporaryFile.toPath(),
                planFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            temporaryFile.delete()
        }
    }

    override fun read(): CapturePlanReadResult {
        if (!planFile.exists()) return CapturePlanReadResult.Missing
        return try {
            CapturePlanCodec.decode(planFile.readText(Charsets.UTF_8))
        } catch (error: RuntimeException) {
            CapturePlanReadResult.Corrupt(error.message ?: "Unable to read capture plan.")
        }
    }

    override fun clear(): Boolean = !planFile.exists() || planFile.delete()
}
