package com.fatmambo33.eclipsecam.media

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.provider.OpenableColumns
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidLocalMediaExportInstrumentationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root: File
        get() = File(context.cacheDir, "export-instrumentation")

    @Before
    fun cleanFixture() {
        root.deleteRecursively()
        root.mkdirs()
    }

    @After
    fun removeFixture() {
        root.deleteRecursively()
    }

    @Test
    fun jpegRemovePolicyStripsGpsWhilePreserveKeepsExactMetadataAndOriginal() {
        val source = File(root, "capture.jpg")
        writeJpeg(source, Color.RED)
        ExifInterface(source.absolutePath).apply {
            setLatLong(48.123, -1.456)
            setAttribute(ExifInterface.TAG_DATETIME, "2026:08:12 17:00:00")
            saveAttributes()
        }
        val sourceBytes = source.readBytes()
        val asset = LocalSessionAsset(
            file = source,
            sizeBytes = source.length(),
            modifiedAtUtc = Instant.EPOCH,
        )
        val stager = LocalExportStager(AndroidJpegLocationMetadataSanitizer())

        val preserved = stager.stage(
            asset,
            LocationMetadataPolicy.PRESERVE,
            File(root, "preserve-stage"),
        ) as LocalExportStageResult.Ready
        val removed = stager.stage(
            asset,
            LocationMetadataPolicy.REMOVE,
            File(root, "remove-stage"),
        ) as LocalExportStageResult.Ready

        assertArrayEquals(sourceBytes, preserved.export.file.readBytes())
        assertTrue(hasGps(ExifInterface(preserved.export.file.absolutePath)))
        assertFalse(hasGps(ExifInterface(removed.export.file.absolutePath)))
        assertEquals(null, ExifInterface(removed.export.file.absolutePath).getAttribute(ExifInterface.TAG_DATETIME))
        assertArrayEquals(sourceBytes, source.readBytes())

        val originalBitmap = BitmapFactory.decodeFile(source.absolutePath)
        val sanitizedBitmap = BitmapFactory.decodeFile(removed.export.file.absolutePath)
        assertNotNull(originalBitmap)
        assertNotNull(sanitizedBitmap)
        requireNotNull(originalBitmap)
        requireNotNull(sanitizedBitmap)
        try {
            assertEquals(originalBitmap.width, sanitizedBitmap.width)
            assertEquals(originalBitmap.height, sanitizedBitmap.height)
        } finally {
            originalBitmap.recycle()
            sanitizedBitmap.recycle()
        }

        preserved.export.cleanup()
        removed.export.cleanup()
    }

    @Test
    fun mediaStorePublisherWritesCompleteImageAndCanBeRemoved() = runBlocking {
        val source = File(root, "media-store.jpg")
        writeJpeg(source, Color.BLUE)
        val staged = LocalExportStager(AndroidJpegLocationMetadataSanitizer()).stage(
            LocalSessionAsset(source, source.length(), Instant.EPOCH),
            LocationMetadataPolicy.REMOVE,
            File(root, "media-store-stage"),
        ) as LocalExportStageResult.Ready

        val result = AndroidMediaStoreLocalExporter(context.contentResolver).publish(staged.export)

        assertTrue(result is ExternalPublishResult.Completed)
        result as ExternalPublishResult.Completed
        val size = context.contentResolver.query(
            result.uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        } ?: 0L
        assertTrue(size > 0L)
        context.contentResolver.openInputStream(result.uri)?.use { input ->
            assertTrue(input.readBytes().isNotEmpty())
        } ?: error("Published MediaStore item was not readable.")

        assertEquals(1, context.contentResolver.delete(result.uri, null, null))
        staged.export.cleanup()
    }

    @Test
    fun fileProviderAndExportIntentsExposeOnlyExplicitCompleteStage() {
        val source = File(root, "share.jpg")
        writeJpeg(source, Color.GREEN)
        val staged = LocalExportStager(AndroidJpegLocationMetadataSanitizer()).stage(
            LocalSessionAsset(source, source.length(), Instant.EPOCH),
            LocationMetadataPolicy.REMOVE,
            File(context.cacheDir, "shared-exports"),
        ) as LocalExportStageResult.Ready
        val uri = staged.export.fileProviderUri(context)

        assertEquals("content", uri.scheme)
        assertTrue(uri.authority?.endsWith(".fileprovider") == true)

        val createDocument = LocalExportIntents.createDocument(
            staged.export.displayName,
            staged.export.mimeType,
        )
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, createDocument.action)
        assertTrue(createDocument.categories?.contains(Intent.CATEGORY_OPENABLE) == true)
        assertEquals("image/jpeg", createDocument.type)
        assertEquals(staged.export.displayName, createDocument.getStringExtra(Intent.EXTRA_TITLE))

        val share = LocalExportIntents.share(uri, staged.export.displayName, staged.export.mimeType)
        assertEquals(Intent.ACTION_SEND, share.action)
        assertEquals("image/jpeg", share.type)
        assertEquals(uri, share.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java))
        assertTrue(share.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(uri, share.clipData?.getItemAt(0)?.uri)

        staged.export.cleanup()
    }

    private fun hasGps(exif: ExifInterface): Boolean {
        val coordinates = FloatArray(2)
        return exif.getLatLong(coordinates)
    }

    private fun writeJpeg(file: File, color: Int) {
        file.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
        }
        bitmap.recycle()
    }
}
