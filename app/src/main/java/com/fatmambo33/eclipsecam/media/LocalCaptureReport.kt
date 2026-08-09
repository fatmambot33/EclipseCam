package com.fatmambo33.eclipsecam.media

import com.fatmambo33.eclipsecam.capture.CapturePhase
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

sealed interface LocalCaptureReportResult {
    data class Completed(val output: File) : LocalCaptureReportResult
    data class Failed(val reason: String) : LocalCaptureReportResult
}

/**
 * Generates a deterministic, local-only JSON summary for one capture session.
 *
 * Reports intentionally contain relative asset names rather than filesystem paths and never add
 * location, orientation, or other sensor metadata. Publication is replace-all: an existing complete
 * report remains untouched until the new temporary report has been fully written.
 */
class LocalCaptureReportGenerator {
    fun generate(session: LocalCaptureSession): LocalCaptureReportResult {
        val generatedDirectory = File(session.directory, GENERATED_DIRECTORY)
        if (!generatedDirectory.isDirectory && !generatedDirectory.mkdirs()) {
            return LocalCaptureReportResult.Failed(
                "Unable to create the local generated-media directory.",
            )
        }

        val temporary = File(generatedDirectory, TEMP_REPORT)
        val finalReport = File(generatedDirectory, FINAL_REPORT)
        temporary.delete()

        return try {
            temporary.writeText(encode(session), Charsets.UTF_8)
            if (!temporary.isFile || temporary.length() <= 0L) {
                temporary.delete()
                LocalCaptureReportResult.Failed("Capture report generation produced no output.")
            } else {
                publish(temporary, finalReport)
                LocalCaptureReportResult.Completed(finalReport)
            }
        } catch (error: Throwable) {
            temporary.delete()
            LocalCaptureReportResult.Failed(error.message ?: "Capture report generation failed.")
        }
    }

    internal fun encode(session: LocalCaptureSession): String {
        val captures = session.captures.sortedWith(ASSET_ORDER)
        val generatedMedia = session.generatedAssets
            .filter { it.kind != LocalSessionAssetKind.CAPTURE_REPORT }
            .sortedWith(compareBy<LocalSessionAsset> { it.kind.ordinal }.thenBy { it.file.name })
        val phases = session.phaseCounts.entries
            .sortedBy { it.key.ordinal }

        return buildString {
            appendLine("{")
            appendLine("  \"schemaVersion\": 1,")
            appendLine("  \"sessionId\": ${jsonString(session.sessionId)},")
            appendLine("  \"status\": ${jsonString(session.status.name)},")
            appendLine("  \"incomplete\": ${session.incomplete},")
            appendLine("  \"capturedAtUtc\": ${jsonString(session.capturedAtUtc.toString())},")
            appendLine("  \"modifiedAtUtc\": ${jsonString(session.modifiedAtUtc.toString())},")
            appendLine("  \"originalCaptureCount\": ${captures.size},")
            appendLine("  \"generatedMediaCount\": ${generatedMedia.size},")
            appendLine("  \"phaseCounts\": {")
            phases.forEachIndexed { index, entry ->
                val suffix = if (index == phases.lastIndex) "" else ","
                appendLine("    ${jsonString(entry.key.name)}: ${entry.value}$suffix")
            }
            appendLine("  },")
            appendLine("  \"captures\": [")
            captures.forEachIndexed { index, asset ->
                val suffix = if (index == captures.lastIndex) "" else ","
                appendLine("    {")
                appendLine("      \"filename\": ${jsonString(asset.file.name)},")
                appendLine("      \"sizeBytes\": ${asset.sizeBytes},")
                appendLine("      \"phase\": ${nullableJsonString(asset.phase?.name)},")
                appendLine("      \"instructionIndex\": ${asset.instructionIndex ?: "null"}")
                appendLine("    }$suffix")
            }
            appendLine("  ],")
            appendLine("  \"generatedMedia\": [")
            generatedMedia.forEachIndexed { index, asset ->
                val suffix = if (index == generatedMedia.lastIndex) "" else ","
                appendLine("    {")
                appendLine("      \"filename\": ${jsonString(asset.file.name)},")
                appendLine("      \"kind\": ${jsonString(asset.kind.name)},")
                appendLine("      \"sizeBytes\": ${asset.sizeBytes}")
                appendLine("    }$suffix")
            }
            appendLine("  ]")
            appendLine("}")
        }
    }

    private fun publish(temporary: File, finalReport: File) {
        try {
            Files.move(
                temporary.toPath(),
                finalReport.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporary.toPath(),
                finalReport.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun nullableJsonString(value: String?): String = value?.let(::jsonString) ?: "null"

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private companion object {
        val ASSET_ORDER = compareBy<LocalSessionAsset> { it.instructionIndex ?: Int.MAX_VALUE }
            .thenBy(LocalSessionAsset::modifiedAtUtc)
            .thenBy { it.file.name }
        const val GENERATED_DIRECTORY = "generated"
        const val TEMP_REPORT = "capture-report.rendering.json"
        const val FINAL_REPORT = "capture-report.json"
    }
}
