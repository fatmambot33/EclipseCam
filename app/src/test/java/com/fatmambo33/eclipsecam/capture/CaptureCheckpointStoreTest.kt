package com.fatmambo33.eclipsecam.capture

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CaptureCheckpointStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val checkpoint = CaptureSessionCheckpoint(
        sessionId = "session/with spaces",
        planStartsAtUtc = Instant.parse("2026-08-12T17:00:00Z"),
        planEndsAtUtc = Instant.parse("2026-08-12T18:00:00Z"),
        nextInstructionIndex = 3,
        capturedCount = 2,
        skippedCount = 1,
        status = CaptureSessionStatus.PAUSED,
        updatedAtUtc = Instant.parse("2026-08-12T17:15:00Z"),
    )

    @Test
    fun codecRoundTripsCheckpoint() {
        val result = CaptureCheckpointCodec.decode(CaptureCheckpointCodec.encode(checkpoint))

        assertEquals(checkpoint, (result as CheckpointReadResult.Loaded).checkpoint)
    }

    @Test
    fun codecRoundTripsFailureReason() {
        val failed = checkpoint.copy(
            status = CaptureSessionStatus.FAILED,
            failureReason = "Camera unavailable: rear/telephoto",
        )

        val result = CaptureCheckpointCodec.decode(CaptureCheckpointCodec.encode(failed))

        assertEquals(failed, (result as CheckpointReadResult.Loaded).checkpoint)
    }

    @Test
    fun corruptOrUnsupportedCheckpointIsRejected() {
        assertTrue(CaptureCheckpointCodec.decode("version=99\n") is CheckpointReadResult.Corrupt)
        assertTrue(CaptureCheckpointCodec.decode("not-a-checkpoint") is CheckpointReadResult.Corrupt)
    }

    @Test
    fun fileStoreWritesReadsOverwritesAndClears() {
        val file = temporaryFolder.newFolder("capture").resolve("checkpoint.txt")
        val store = FileCaptureCheckpointStore(file)

        assertTrue(store.read() is CheckpointReadResult.Missing)
        store.write(checkpoint)
        assertEquals(checkpoint, (store.read() as CheckpointReadResult.Loaded).checkpoint)

        val updated = checkpoint.copy(
            nextInstructionIndex = 4,
            capturedCount = 3,
            updatedAtUtc = checkpoint.updatedAtUtc.plusSeconds(1),
        )
        store.write(updated)
        assertEquals(updated, (store.read() as CheckpointReadResult.Loaded).checkpoint)

        assertTrue(store.clear())
        assertTrue(store.read() is CheckpointReadResult.Missing)
    }

    @Test
    fun fileStoreReportsCorruptContentWithoutDeletingIt() {
        val file = temporaryFolder.newFile("checkpoint.txt")
        file.writeText("version=1\nsessionId=%%%")
        val store = FileCaptureCheckpointStore(file)

        assertTrue(store.read() is CheckpointReadResult.Corrupt)
        assertTrue(file.exists())
    }
}
