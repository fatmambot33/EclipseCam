package com.fatmambo33.eclipsecam.media

import android.content.ClipData
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fail-closed JPEG sanitizer that removes all container metadata by decoding and re-encoding pixels.
 *
 * EXIF orientation is applied to the pixels before re-encoding so the visible image orientation is
 * preserved while GPS, maker notes, timestamps, XMP, and other metadata are not copied.
 */
class AndroidJpegLocationMetadataSanitizer : JpegLocationMetadataSanitizer {
    override fun sanitize(source: File, destination: File) {
        val decoded = BitmapFactory.decodeFile(source.absolutePath)
            ?: error("Unable to decode the selected JPEG for metadata removal.")
        val oriented = try {
            orient(decoded, ExifInterface(source.absolutePath))
        } catch (error: Throwable) {
            decoded.recycle()
            throw error
        }
        try {
            destination.parentFile?.mkdirs()
            FileOutputStream(destination).use { output ->
                check(oriented.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    "Android failed to encode the metadata-free JPEG."
                }
            }
        } finally {
            if (oriented !== decoded) decoded.recycle()
            oriented.recycle()
        }
    }

    private fun orient(bitmap: Bitmap, exif: ExifInterface): Bitmap {
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        const val JPEG_QUALITY = 98
    }
}

sealed interface ExternalPublishResult {
    data class Completed(val uri: Uri) : ExternalPublishResult
    data class Failed(val reason: String) : ExternalPublishResult
}

/** Publishes an already-complete staging artifact to a user-selected SAF document URI. */
class AndroidSafLocalExporter(
    private val resolver: ContentResolver,
) {
    suspend fun publish(export: PreparedLocalExport, destination: Uri): ExternalPublishResult =
        withContext(Dispatchers.IO) {
            try {
                resolver.openOutputStream(destination, "w")?.use { output ->
                    export.file.inputStream().use { input -> input.copyTo(output) }
                } ?: error("The selected destination cannot be opened for writing.")
                ExternalPublishResult.Completed(destination)
            } catch (error: Throwable) {
                runCatching { resolver.delete(destination, null, null) }
                ExternalPublishResult.Failed(error.message ?: "Export to the selected destination failed.")
            }
        }
}

/** Pending-first MediaStore publisher used only after an explicit user Save-to-device action. */
class AndroidMediaStoreLocalExporter(
    private val resolver: ContentResolver,
) {
    suspend fun publish(export: PreparedLocalExport): ExternalPublishResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext ExternalPublishResult.Failed(
                "Direct device-library export requires Android 10 or newer; choose a destination instead.",
            )
        }
        val collection = when {
            export.mimeType.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            export.mimeType.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> return@withContext ExternalPublishResult.Failed(
                "This asset type is not supported by the device media library.",
            )
        }
        val relativePath = if (export.mimeType.startsWith("video/")) {
            "Movies/EclipseCam"
        } else {
            "Pictures/EclipseCam"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, export.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, export.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        var destination: Uri? = null
        try {
            destination = resolver.insert(collection, values)
                ?: error("Android did not create a media-library destination.")
            resolver.openOutputStream(destination, "w")?.use { output ->
                export.file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("The media-library destination cannot be opened for writing.")
            val publishValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            check(resolver.update(destination, publishValues, null, null) == 1) {
                "Android did not publish the completed media item."
            }
            ExternalPublishResult.Completed(destination)
        } catch (error: Throwable) {
            destination?.let { uri -> runCatching { resolver.delete(uri, null, null) } }
            ExternalPublishResult.Failed(error.message ?: "Device media-library export failed.")
        }
    }
}

object LocalExportIntents {
    fun createDocument(displayName: String, mimeType: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, displayName)
        }

    fun share(contentUri: Uri, displayName: String, mimeType: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            clipData = ClipData.newRawUri(displayName, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
}

fun PreparedLocalExport.fileProviderUri(context: Context): Uri = FileProvider.getUriForFile(
    context,
    "${context.packageName}.fileprovider",
    file,
)
