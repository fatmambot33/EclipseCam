package com.fatmambo33.eclipsecam.media

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

enum class LocationMetadataPolicy { REMOVE, PRESERVE }

fun interface JpegLocationMetadataSanitizer {
    fun sanitize(source: File, destination: File)
}

data class PreparedLocalExport(
    val file: File,
    val displayName: String,
    val mimeType: String,
    val locationMetadataPolicy: LocationMetadataPolicy,
) {
    fun cleanup() {
        file.parentFile?.deleteRecursively()
    }
}

sealed interface LocalExportStageResult {
    data class Ready(val export: PreparedLocalExport) : LocalExportStageResult
    data class Failed(val reason: String) : LocalExportStageResult
}

/**
 * Creates a private, complete staging artifact before any external export/share flow starts.
 *
 * JPEG location removal is delegated to a fail-closed sanitizer. Other files are copied byte-for-
 * byte. The staged file is atomically published inside app-private storage and originals are never
 * modified. Callers may then copy the complete staging artifact to SAF/MediaStore or expose it via
 * FileProvider after an explicit user action.
 */
class LocalExportStager(
    private val jpegSanitizer: JpegLocationMetadataSanitizer,
) {
    fun stage(
        asset: LocalSessionAsset,
        policy: LocationMetadataPolicy,
        stagingRoot: File,
    ): LocalExportStageResult {
        val source = asset.file
        if (!source.isFile || source.length() <= 0L) {
            return LocalExportStageResult.Failed("The selected local asset is missing or empty.")
        }
        val mimeType = localAssetMimeType(source)
            ?: return LocalExportStageResult.Failed("The selected local asset type is not exportable.")
        val directory = File(stagingRoot, UUID.randomUUID().toString())
        if (!directory.mkdirs()) {
            return LocalExportStageResult.Failed("Unable to create private export staging storage.")
        }
        val temporary = File(directory, ".${source.name}.partial")
        val finalStage = File(directory, source.name)

        return try {
            if (policy == LocationMetadataPolicy.REMOVE && mimeType == "image/jpeg") {
                jpegSanitizer.sanitize(source, temporary)
            } else {
                source.copyTo(temporary, overwrite = true)
            }
            if (!temporary.isFile || temporary.length() <= 0L) {
                error("Export staging produced no output.")
            }
            publish(temporary, finalStage)
            LocalExportStageResult.Ready(
                PreparedLocalExport(
                    file = finalStage,
                    displayName = source.name,
                    mimeType = mimeType,
                    locationMetadataPolicy = policy,
                ),
            )
        } catch (error: Throwable) {
            directory.deleteRecursively()
            LocalExportStageResult.Failed(error.message ?: "Unable to prepare the selected local asset.")
        }
    }

    private fun publish(temporary: File, finalStage: File) {
        try {
            Files.move(
                temporary.toPath(),
                finalStage.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporary.toPath(),
                finalStage.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}

fun localAssetMimeType(file: File): String? = when (file.extension.lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "mp4" -> "video/mp4"
    "json" -> "application/json"
    else -> null
}

fun isMediaStoreExportable(mimeType: String): Boolean =
    mimeType.startsWith("image/") || mimeType.startsWith("video/")
