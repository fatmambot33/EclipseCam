package com.fatmambo33.eclipsecam.media

import java.io.File
import java.nio.file.Files
import java.time.Instant
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMediaExportTest {
    @Test
    fun removePolicyUsesFailClosedJpegSanitizerWithoutMutatingOriginal() {
        val root = Files.createTempDirectory("export-stage").toFile()
        try {
            val sourceBytes = byteArrayOf(1, 2, 3, 4)
            val asset = asset(File(root, "capture.jpg"), sourceBytes)
            var sanitizerCalled = false
            val stager = LocalExportStager(
                JpegLocationMetadataSanitizer { source, destination ->
                    sanitizerCalled = true
                    assertEquals(asset.file, source)
                    destination.writeBytes(byteArrayOf(9, 8, 7))
                },
            )

            val result = stager.stage(asset, LocationMetadataPolicy.REMOVE, File(root, "staging"))

            assertTrue(result is LocalExportStageResult.Ready)
            result as LocalExportStageResult.Ready
            assertTrue(sanitizerCalled)
            assertEquals("image/jpeg", result.export.mimeType)
            assertEquals(LocationMetadataPolicy.REMOVE, result.export.locationMetadataPolicy)
            assertArrayEquals(byteArrayOf(9, 8, 7), result.export.file.readBytes())
            assertArrayEquals(sourceBytes, asset.file.readBytes())
            assertFalse(File(result.export.file.parentFile, ".capture.jpg.partial").exists())
            result.export.cleanup()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun preservePolicyCopiesOriginalBytesAndNeverCallsSanitizer() {
        val root = Files.createTempDirectory("export-stage").toFile()
        try {
            val sourceBytes = byteArrayOf(4, 3, 2, 1)
            val asset = asset(File(root, "capture.jpeg"), sourceBytes)
            val stager = LocalExportStager(
                JpegLocationMetadataSanitizer { _, _ -> error("sanitizer must not be called") },
            )

            val result = stager.stage(asset, LocationMetadataPolicy.PRESERVE, File(root, "staging"))

            assertTrue(result is LocalExportStageResult.Ready)
            result as LocalExportStageResult.Ready
            assertArrayEquals(sourceBytes, result.export.file.readBytes())
            assertArrayEquals(sourceBytes, asset.file.readBytes())
            result.export.cleanup()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun sanitizerFailureDeletesPrivatePartialArtifact() {
        val root = Files.createTempDirectory("export-stage").toFile()
        try {
            val asset = asset(File(root, "capture.jpg"), byteArrayOf(1))
            val staging = File(root, "staging")
            val stager = LocalExportStager(
                JpegLocationMetadataSanitizer { _, destination ->
                    destination.writeText("partial")
                    error("sanitizer failed")
                },
            )

            val result = stager.stage(asset, LocationMetadataPolicy.REMOVE, staging)

            assertTrue(result is LocalExportStageResult.Failed)
            assertTrue(staging.listFiles().isNullOrEmpty())
            assertTrue(asset.file.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun unsupportedOrEmptyAssetsFailBeforeCreatingPublishedStage() {
        val root = Files.createTempDirectory("export-stage").toFile()
        try {
            val unsupported = asset(File(root, "notes.txt"), byteArrayOf(1))
            val emptyFile = File(root, "empty.jpg").apply { createNewFile() }
            val empty = LocalSessionAsset(emptyFile, 0L, Instant.EPOCH)
            val staging = File(root, "staging")
            val stager = LocalExportStager(JpegLocationMetadataSanitizer { _, _ -> })

            assertTrue(stager.stage(unsupported, LocationMetadataPolicy.REMOVE, staging) is LocalExportStageResult.Failed)
            assertTrue(stager.stage(empty, LocationMetadataPolicy.REMOVE, staging) is LocalExportStageResult.Failed)
            assertFalse(staging.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun supportedMimeAndMediaStoreClassificationIsExplicit() {
        assertEquals("image/jpeg", localAssetMimeType(File("a.jpg")))
        assertEquals("image/png", localAssetMimeType(File("a.png")))
        assertEquals("video/mp4", localAssetMimeType(File("a.mp4")))
        assertEquals("application/json", localAssetMimeType(File("a.json")))
        assertEquals(null, localAssetMimeType(File("a.bin")))
        assertTrue(isMediaStoreExportable("image/jpeg"))
        assertTrue(isMediaStoreExportable("video/mp4"))
        assertFalse(isMediaStoreExportable("application/json"))
    }

    private fun asset(file: File, bytes: ByteArray): LocalSessionAsset {
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return LocalSessionAsset(
            file = file,
            sizeBytes = file.length(),
            modifiedAtUtc = Instant.EPOCH,
        )
    }
}
