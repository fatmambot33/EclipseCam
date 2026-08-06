package com.fatmambo33.eclipsecam.media

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Controls whether a local JPEG export retains embedded application metadata. */
enum class JpegMetadataPolicy {
    PRESERVE,
    REMOVE,
}

/** Result of one explicit local JPEG export. */
data class JpegExportResult(
    val destination: File,
    val metadataRemoved: Boolean,
    val sizeBytes: Long,
)

/**
 * Creates a local JPEG export while applying the user's explicit metadata preference.
 *
 * [JpegMetadataPolicy.REMOVE] drops every JPEG APP1 segment before the image scan. EXIF GPS fields
 * live in APP1, so this conservatively removes location metadata without decoding or recompressing
 * image pixels. The exporter writes to a temporary sibling and only replaces the destination after
 * a complete, validated copy. It never uploads or shares the resulting file.
 */
class JpegPrivacyExporter {
    fun export(
        source: File,
        destination: File,
        metadataPolicy: JpegMetadataPolicy,
    ): JpegExportResult {
        require(source.isFile) { "JPEG source must be an existing file." }
        require(source.canonicalFile != destination.canonicalFile) {
            "JPEG export destination must differ from the source."
        }

        val parent = destination.absoluteFile.parentFile
            ?: error("JPEG export destination must have a parent directory.")
        check(parent.isDirectory || parent.mkdirs()) {
            "Unable to create JPEG export directory."
        }

        val temporary = File(parent, ".${destination.name}.partial")
        check(!temporary.exists() || temporary.delete()) {
            "Unable to clear stale JPEG export temporary file."
        }

        try {
            when (metadataPolicy) {
                JpegMetadataPolicy.PRESERVE -> source.copyTo(temporary, overwrite = false)
                JpegMetadataPolicy.REMOVE -> removeApp1Metadata(source, temporary)
            }
            replaceAtomically(temporary, destination)
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }

        return JpegExportResult(
            destination = destination,
            metadataRemoved = metadataPolicy == JpegMetadataPolicy.REMOVE,
            sizeBytes = destination.length(),
        )
    }

    private fun removeApp1Metadata(source: File, destination: File) {
        BufferedInputStream(FileInputStream(source)).use { input ->
            BufferedOutputStream(FileOutputStream(destination)).use { output ->
                val first = input.readRequired()
                val second = input.readRequired()
                require(first == MARKER_PREFIX && second == START_OF_IMAGE) {
                    "Source is not a JPEG file."
                }
                output.write(first)
                output.write(second)

                while (true) {
                    val markerPrefix = input.readRequired()
                    require(markerPrefix == MARKER_PREFIX) { "Malformed JPEG marker stream." }

                    var marker = input.readRequired()
                    while (marker == MARKER_PREFIX) marker = input.readRequired()
                    require(marker != 0x00) { "Unexpected stuffed byte before JPEG scan data." }

                    if (marker == END_OF_IMAGE) {
                        output.write(MARKER_PREFIX)
                        output.write(marker)
                        return
                    }
                    if (marker in RESTART_MARKER_RANGE || marker == TEMPORARY_MARKER) {
                        output.write(MARKER_PREFIX)
                        output.write(marker)
                        continue
                    }

                    val lengthHigh = input.readRequired()
                    val lengthLow = input.readRequired()
                    val segmentLength = (lengthHigh shl 8) or lengthLow
                    require(segmentLength >= 2) { "Invalid JPEG segment length." }
                    val payload = input.readExactly(segmentLength - 2)

                    if (marker != APP1_MARKER) {
                        output.write(MARKER_PREFIX)
                        output.write(marker)
                        output.write(lengthHigh)
                        output.write(lengthLow)
                        output.write(payload)
                    }

                    if (marker == START_OF_SCAN) {
                        input.copyTo(output)
                        return
                    }
                }
            }
        }
    }

    private fun replaceAtomically(temporary: File, destination: File) {
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
        }
    }

    private fun BufferedInputStream.readRequired(): Int =
        read().takeIf { it >= 0 } ?: throw EOFException("Unexpected end of JPEG file.")

    private fun BufferedInputStream.readExactly(size: Int): ByteArray {
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = read(bytes, offset, size - offset)
            if (count < 0) throw EOFException("Unexpected end of JPEG segment.")
            offset += count
        }
        return bytes
    }

    companion object {
        private const val MARKER_PREFIX = 0xFF
        private const val START_OF_IMAGE = 0xD8
        private const val END_OF_IMAGE = 0xD9
        private const val START_OF_SCAN = 0xDA
        private const val APP1_MARKER = 0xE1
        private const val TEMPORARY_MARKER = 0x01
        private val RESTART_MARKER_RANGE = 0xD0..0xD7
    }
}
