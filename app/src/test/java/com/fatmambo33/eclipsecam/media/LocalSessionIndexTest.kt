package com.fatmambo33.eclipsecam.media

import com.fatmambo33.eclipsecam.capture.CaptureInstruction
import com.fatmambo33.eclipsecam.capture.CapturePhase
import com.fatmambo33.eclipsecam.capture.CapturePlan
import com.fatmambo33.eclipsecam.capture.CaptureSessionCheckpoint
import com.fatmambo33.eclipsecam.capture.CaptureSessionStatus
import com.fatmambo33.eclipsecam.capture.ExposureStrategy
import java.io.File
import java.nio.file.Files
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSessionIndexTest {
    @Test
    fun listsReadableJpegsAndIgnoresPlaceholdersAndUnknownFiles() {
        val root = Files.createTempDirectory("session-index").toFile()
        try {
            val session = File(root, "session-a").apply { mkdirs() }
            File(session, "000001_frame.jpg").writeBytes(byteArrayOf(1, 2, 3))
            File(session, "000002_pending.jpg").createNewFile()
            File(session, "notes.txt").writeText("ignored")
            File(session, "session.complete").writeText("ok")

            val indexed = LocalSessionIndex(root).listSessions().single()

            assertEquals("session-a", indexed.sessionId)
            assertEquals(listOf("000001_frame.jpg"), indexed.assets.map { it.file.name })
            assertEquals(3L, indexed.assets.single().sizeBytes)
            assertEquals(LocalSessionStatus.COMPLETE, indexed.status)
            assertFalse(indexed.incomplete)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun preservesInterruptedAndEmptySessions() {
        val root = Files.createTempDirectory("session-index").toFile()
        try {
            val interrupted = File(root, "interrupted").apply { mkdirs() }
            File(interrupted, "000001_frame.JPG").writeBytes(byteArrayOf(7))
            File(root, "empty").mkdirs()

            val indexed = LocalSessionIndex(root).listSessions().associateBy { it.sessionId }

            assertEquals(setOf("interrupted", "empty"), indexed.keys)
            assertEquals(LocalSessionStatus.INTERRUPTED, indexed.getValue("interrupted").status)
            assertEquals(LocalSessionStatus.INTERRUPTED, indexed.getValue("empty").status)
            assertTrue(indexed.getValue("interrupted").incomplete)
            assertTrue(indexed.getValue("empty").incomplete)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun journalProjectsStatusPhaseCountsAndGeneratedOutputs() {
        val root = Files.createTempDirectory("session-index").toFile()
        try {
            val t0 = Instant.parse("2026-08-12T17:00:00Z")
            val plan = CapturePlan(
                startsAtUtc = t0,
                endsAtUtc = t0.plusSeconds(2),
                instructions = listOf(
                    CaptureInstruction(t0, CapturePhase.PARTIAL, ExposureStrategy.FILTERED_PARTIAL),
                    CaptureInstruction(t0.plusSeconds(1), CapturePhase.CONTACT_BURST, ExposureStrategy.CONTACT_BRACKET),
                    CaptureInstruction(t0.plusSeconds(2), CapturePhase.TOTALITY, ExposureStrategy.TOTALITY_BRACKET),
                ),
            )
            val checkpoint = CaptureSessionCheckpoint(
                sessionId = "gallery-session",
                planStartsAtUtc = plan.startsAtUtc,
                planEndsAtUtc = plan.endsAtUtc,
                nextInstructionIndex = 3,
                capturedCount = 3,
                skippedCount = 0,
                status = CaptureSessionStatus.COMPLETED,
                updatedAtUtc = t0.plusSeconds(3),
            )
            FileLocalCaptureSessionJournal(root).record(plan, checkpoint)
            val session = File(root, "gallery-session")
            File(session, "000000_first.jpg").writeBytes(byteArrayOf(1))
            File(session, "000001_contact.jpg").writeBytes(byteArrayOf(2))
            File(session, "000002_totality.jpg").writeBytes(byteArrayOf(3))
            val generated = File(session, "generated").apply { mkdirs() }
            File(generated, "timelapse.mp4").writeBytes(byteArrayOf(4))
            File(generated, "montage.jpg").writeBytes(byteArrayOf(5))
            File(generated, "capture-report.json").writeText("{}")
            File(generated, "partial.tmp").writeBytes(byteArrayOf(6))

            val indexed = LocalSessionIndex(root).listSessions().single()

            assertEquals(LocalSessionStatus.COMPLETE, indexed.status)
            assertEquals(t0, indexed.capturedAtUtc)
            assertEquals(3, indexed.captures.size)
            assertEquals(
                mapOf(
                    CapturePhase.PARTIAL to 1,
                    CapturePhase.CONTACT_BURST to 1,
                    CapturePhase.TOTALITY to 1,
                ),
                indexed.phaseCounts,
            )
            assertEquals(
                setOf(
                    LocalSessionAssetKind.TIMELAPSE,
                    LocalSessionAssetKind.MONTAGE,
                    LocalSessionAssetKind.CAPTURE_REPORT,
                ),
                indexed.generatedAssets.map(LocalSessionAsset::kind).toSet(),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun readsPausedAndFailedSessionStates() {
        val root = Files.createTempDirectory("session-index").toFile()
        try {
            val paused = File(root, "paused").apply { mkdirs() }
            File(paused, "session.state").writeText(
                "version=1\nstatus=PAUSED\nupdatedAtUtc=2026-08-12T17:00:00Z\n",
            )
            val failed = File(root, "failed").apply { mkdirs() }
            File(failed, "session.state").writeText(
                "version=1\nstatus=FAILED\nupdatedAtUtc=2026-08-12T17:01:00Z\n",
            )

            val indexed = LocalSessionIndex(root).listSessions().associateBy { it.sessionId }

            assertEquals(LocalSessionStatus.PAUSED, indexed.getValue("paused").status)
            assertEquals(LocalSessionStatus.FAILED, indexed.getValue("failed").status)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun corruptMetadataFallsBackToInterruptedWithoutCrashing() {
        val root = Files.createTempDirectory("session-index").toFile()
        try {
            val session = File(root, "corrupt").apply { mkdirs() }
            File(session, "000000_frame.jpg").writeBytes(byteArrayOf(1))
            File(session, "session.state").writeText("not metadata")
            File(session, "session.plan-index").writeText("version=broken")

            val indexed = LocalSessionIndex(root).listSessions().single()

            assertEquals(LocalSessionStatus.INTERRUPTED, indexed.status)
            assertTrue(indexed.phaseCounts.isEmpty())
            assertEquals(1, indexed.captures.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun sortsNewestSessionFirstWithStableIdTieBreak() {
        val root = Files.createTempDirectory("session-index").toFile()
        try {
            val older = File(root, "older").apply { mkdirs() }
            val newer = File(root, "newer").apply { mkdirs() }
            File(older, "frame.jpg").apply {
                writeBytes(byteArrayOf(1))
                setLastModified(1_000L)
            }
            File(newer, "frame.jpg").apply {
                writeBytes(byteArrayOf(2))
                setLastModified(2_000L)
            }

            assertEquals(
                listOf("newer", "older"),
                LocalSessionIndex(root).listSessions().map { it.sessionId },
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingRootReturnsEmptyIndex() {
        val missing = File("build/test-missing-${System.nanoTime()}")
        assertTrue(LocalSessionIndex(missing).listSessions().isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonDirectoryRootIsReportedAsError() {
        val root = Files.createTempFile("session-index", ".txt").toFile()
        try {
            LocalSessionIndex(root).listSessions()
        } finally {
            root.delete()
        }
    }
}
