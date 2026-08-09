package com.fatmambo33.eclipsecam.media

import com.fatmambo33.eclipsecam.capture.CapturePhase
import java.io.File
import java.nio.file.Files
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCaptureReportTest {
    @Test
    fun publishesDeterministicPrivacyBoundedSessionSummary() {
        val root = Files.createTempDirectory("capture-report").toFile()
        try {
            val directory = File(root, "session-private").apply { mkdirs() }
            val early = File(directory, "000001_early.jpg").apply { writeBytes(byteArrayOf(1, 2)) }
            val totality = File(directory, "000003_totality.jpg").apply { writeBytes(byteArrayOf(3, 4, 5)) }
            val generated = File(directory, "generated").apply { mkdirs() }
            val montage = File(generated, "montage.jpg").apply { writeBytes(byteArrayOf(6)) }
            File(generated, "capture-report.json").writeText("old report")
            val t0 = Instant.parse("2026-08-12T17:00:00Z")
            val session = LocalCaptureSession(
                sessionId = "session-\"private\"",
                directory = directory,
                assets = listOf(
                    totality.toAsset(
                        kind = LocalSessionAssetKind.ORIGINAL_CAPTURE,
                        phase = CapturePhase.TOTALITY,
                        instructionIndex = 3,
                    ),
                    early.toAsset(
                        kind = LocalSessionAssetKind.ORIGINAL_CAPTURE,
                        phase = CapturePhase.PARTIAL,
                        instructionIndex = 1,
                    ),
                    montage.toAsset(kind = LocalSessionAssetKind.MONTAGE),
                    File(generated, "capture-report.json").toAsset(
                        kind = LocalSessionAssetKind.CAPTURE_REPORT,
                    ),
                ),
                capturedAtUtc = t0,
                modifiedAtUtc = t0.plusSeconds(30),
                status = LocalSessionStatus.COMPLETE,
                phaseCounts = mapOf(
                    CapturePhase.TOTALITY to 1,
                    CapturePhase.PARTIAL to 1,
                ),
            )

            val result = LocalCaptureReportGenerator().generate(session)

            assertTrue(result is LocalCaptureReportResult.Completed)
            val output = (result as LocalCaptureReportResult.Completed).output
            val report = output.readText()
            assertEquals(File(generated, "capture-report.json").canonicalPath, output.canonicalPath)
            assertTrue(report.contains("\"schemaVersion\": 1"))
            assertTrue(report.contains("\"sessionId\": \"session-\\\"private\\\"\""))
            assertTrue(report.contains("\"status\": \"COMPLETE\""))
            assertTrue(report.contains("\"originalCaptureCount\": 2"))
            assertTrue(report.contains("\"generatedMediaCount\": 1"))
            assertTrue(report.indexOf("000001_early.jpg") < report.indexOf("000003_totality.jpg"))
            assertTrue(report.indexOf("\"PARTIAL\"") < report.indexOf("\"TOTALITY\""))
            assertTrue(report.contains("\"kind\": \"MONTAGE\""))
            assertFalse(report.contains(root.canonicalPath))
            assertFalse(report.contains("capture-report.rendering.json"))
            assertFalse(File(generated, "capture-report.rendering.json").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun encodesIncompleteSessionAndUnknownCaptureMetadataWithoutInventingValues() {
        val root = Files.createTempDirectory("capture-report-incomplete").toFile()
        try {
            val directory = File(root, "interrupted").apply { mkdirs() }
            val capture = File(directory, "unindexed.jpg").apply { writeBytes(byteArrayOf(9)) }
            val t0 = Instant.parse("2026-08-12T17:00:00Z")
            val session = LocalCaptureSession(
                sessionId = "interrupted",
                directory = directory,
                assets = listOf(capture.toAsset(kind = LocalSessionAssetKind.ORIGINAL_CAPTURE)),
                capturedAtUtc = t0,
                modifiedAtUtc = t0,
                status = LocalSessionStatus.INTERRUPTED,
                phaseCounts = emptyMap(),
            )

            val report = LocalCaptureReportGenerator().encode(session)

            assertTrue(report.contains("\"incomplete\": true"))
            assertTrue(report.contains("\"phase\": null"))
            assertTrue(report.contains("\"instructionIndex\": null"))
            assertTrue(report.contains("\"phaseCounts\": {\n  }"))
        } finally {
            root.deleteRecursively()
        }
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
}
