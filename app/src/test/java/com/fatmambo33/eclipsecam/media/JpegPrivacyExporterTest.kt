package com.fatmambo33.eclipsecam.media

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JpegPrivacyExporterTest {
    @Test
    fun preservePolicyCopiesTheOriginalBytesExactly() {
        val root = Files.createTempDirectory("jpeg-preserve").toFile()
        val source = File(root, "source.jpg").apply { writeBytes(jpegWithExif()) }
        val destination = File(root, "export/preserved.jpg")

        val result = JpegPrivacyExporter().export(
            source = source,
            destination = destination,
            metadataPolicy = JpegMetadataPolicy.PRESERVE,
        )

        assertFalse(result.metadataRemoved)
        assertEquals(destination.length(), result.sizeBytes)
        assertArrayEquals(source.readBytes(), destination.readBytes())
    }

    @Test
    fun removePolicyDropsApp1ExifWithoutRecompressingImageData() {
        val root = Files.createTempDirectory("jpeg-remove").toFile()
        val source = File(root, "source.jpg").apply { writeBytes(jpegWithExif()) }
        val destination = File(root, "export/private.jpg")

        val result = JpegPrivacyExporter().export(
            source = source,
            destination = destination,
            metadataPolicy = JpegMetadataPolicy.REMOVE,
        )

        assertTrue(result.metadataRemoved)
        assertArrayEquals(jpegWithoutExif(), destination.readBytes())
        assertFalse(destination.readBytes().containsSequence(EXIF_PAYLOAD))
    }

    @Test
    fun malformedInputFailsClosedAndLeavesNoDestination() {
        val root = Files.createTempDirectory("jpeg-invalid").toFile()
        val source = File(root, "source.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val destination = File(root, "export/private.jpg")

        val failure = runCatching {
            JpegPrivacyExporter().export(
                source = source,
                destination = destination,
                metadataPolicy = JpegMetadataPolicy.REMOVE,
            )
        }

        assertTrue(failure.isFailure)
        assertFalse(destination.exists())
        assertFalse(File(destination.parentFile, ".${destination.name}.partial").exists())
    }

    @Test
    fun sourceCannotBeOverwrittenInPlace() {
        val root = Files.createTempDirectory("jpeg-in-place").toFile()
        val source = File(root, "source.jpg").apply { writeBytes(jpegWithExif()) }

        val failure = runCatching {
            JpegPrivacyExporter().export(source, source, JpegMetadataPolicy.REMOVE)
        }

        assertTrue(failure.isFailure)
        assertArrayEquals(jpegWithExif(), source.readBytes())
    }

    private fun ByteArray.containsSequence(sequence: ByteArray): Boolean =
        indices.any { start ->
            start + sequence.size <= size &&
                sequence.indices.all { offset -> this[start + offset] == sequence[offset] }
        }

    private fun jpegWithExif(): ByteArray = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(),
        0xFF.toByte(), 0xE0.toByte(), 0x00, 0x04, 0x11, 0x22,
        0xFF.toByte(), 0xE1.toByte(), 0x00, 0x08,
        *EXIF_PAYLOAD,
        0xFF.toByte(), 0xDA.toByte(), 0x00, 0x04, 0x33, 0x44,
        0x55, 0x66, 0xFF.toByte(), 0xD9.toByte(),
    )

    private fun jpegWithoutExif(): ByteArray = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(),
        0xFF.toByte(), 0xE0.toByte(), 0x00, 0x04, 0x11, 0x22,
        0xFF.toByte(), 0xDA.toByte(), 0x00, 0x04, 0x33, 0x44,
        0x55, 0x66, 0xFF.toByte(), 0xD9.toByte(),
    )

    companion object {
        private val EXIF_PAYLOAD = byteArrayOf(
            'E'.code.toByte(),
            'x'.code.toByte(),
            'i'.code.toByte(),
            'f'.code.toByte(),
            0x00,
            0x00,
        )
    }
}
