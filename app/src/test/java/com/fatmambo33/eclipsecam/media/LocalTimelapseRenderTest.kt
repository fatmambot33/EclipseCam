package com.fatmambo33.eclipsecam.media

import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTimelapseRenderTest {
    @Test
    fun selectorOrdersByDurableCaptureTimeThenInstructionIndexAndExcludesGeneratedAssets() {
        val root = Files.createTempDirectory("timelapse-selector").toFile()
        try {
            val sessionDirectory = File(root, "session").apply { mkdirs() }
            val later = asset(
                File(sessionDirectory, "000003_2026-08-12T17-00-02Z.jpg"),
                Instant.parse("2026-08-12T17:00:00Z"),
            )
            val sameTimeSecond = asset(
                File(sessionDirectory, "000002_2026-08-12T17-00-01Z.jpg"),
                Instant.parse("2026-08-12T17:00:00Z"),
            )
            val sameTimeFirst = asset(
                File(sessionDirectory, "000001_2026-08-12T17-00-01Z.jpg"),
                Instant.parse("2026-08-12T17:00:00Z"),
            )
            val generated = asset(
                File(sessionDirectory, "generated/timelapse.mp4"),
                Instant.parse("2026-08-12T16:00:00Z"),
                LocalSessionAssetKind.TIMELAPSE,
            )
            val session = session(sessionDirectory, listOf(later, generated, sameTimeSecond, sameTimeFirst))

            assertEquals(
                listOf(
                    "000001_2026-08-12T17-00-01Z.jpg",
                    "000002_2026-08-12T17-00-01Z.jpg",
                    "000003_2026-08-12T17-00-02Z.jpg",
                ),
                TimelapseFrameSelector.select(session).map { it.file.name },
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun selectorUsesPersistedModifiedTimeForLegacyFilenames() {
        val root = Files.createTempDirectory("timelapse-selector").toFile()
        try {
            val directory = File(root, "session").apply { mkdirs() }
            val newer = asset(File(directory, "legacy-b.jpg"), Instant.parse("2026-08-12T17:01:00Z"))
            val older = asset(File(directory, "legacy-a.jpg"), Instant.parse("2026-08-12T17:00:00Z"))

            assertEquals(
                listOf("legacy-a.jpg", "legacy-b.jpg"),
                TimelapseFrameSelector.select(session(directory, listOf(newer, older))).map { it.file.name },
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun successfulRenderFiltersUnreadableFramesPublishesAtomicallyAndPreservesOriginals() = runBlocking {
        val root = Files.createTempDirectory("timelapse-render").toFile()
        try {
            val directory = File(root, "session").apply { mkdirs() }
            val firstBytes = byteArrayOf(1, 2, 3)
            val secondBytes = byteArrayOf(4, 5, 6)
            val badBytes = byteArrayOf(9)
            val first = asset(File(directory, "000001_2026-08-12T17-00-01Z.jpg"), Instant.EPOCH, bytes = firstBytes)
            val bad = asset(File(directory, "000002_2026-08-12T17-00-02Z.jpg"), Instant.EPOCH, bytes = badBytes)
            val second = asset(File(directory, "000003_2026-08-12T17-00-03Z.jpg"), Instant.EPOCH, bytes = secondBytes)
            val generated = File(directory, "generated").apply { mkdirs() }
            val finalOutput = File(generated, "timelapse.mp4").apply { writeText("old") }
            val progress = mutableListOf<Int>()
            var encodedNames = emptyList<String>()
            val encoder = TimelapseVideoEncoder { frames, output, report ->
                encodedNames = frames.map { it.file.name }
                report(25)
                output.writeBytes(byteArrayOf(7, 8, 9, 10))
                report(75)
            }
            val generator = LocalTimelapseGenerator(
                encoder = encoder,
                frameProbe = TimelapseFrameProbe { file -> file.name != bad.file.name },
            )

            val result = generator.render(session(directory, listOf(second, bad, first)), progress::add)

            assertTrue(result is TimelapseRenderResult.Completed)
            result as TimelapseRenderResult.Completed
            assertEquals(2, result.frameCount)
            assertEquals(listOf(first.file.name, second.file.name), encodedNames)
            assertEquals(listOf(0, 25, 75, 100), progress)
            assertEquals(finalOutput.canonicalFile, result.output.canonicalFile)
            assertArrayEquals(byteArrayOf(7, 8, 9, 10), finalOutput.readBytes())
            assertFalse(File(generated, "timelapse.rendering.mp4").exists())
            assertArrayEquals(firstBytes, first.file.readBytes())
            assertArrayEquals(secondBytes, second.file.readBytes())
            assertArrayEquals(badBytes, bad.file.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun failedRenderDeletesTemporaryOutputAndKeepsPreviousCompleteVideo() = runBlocking {
        val root = Files.createTempDirectory("timelapse-render").toFile()
        try {
            val directory = File(root, "session").apply { mkdirs() }
            val original = asset(File(directory, "000001_2026-08-12T17-00-01Z.jpg"), Instant.EPOCH)
            val generated = File(directory, "generated").apply { mkdirs() }
            val previous = File(generated, "timelapse.mp4").apply { writeText("previous") }
            val generator = LocalTimelapseGenerator(
                encoder = TimelapseVideoEncoder { _, output, _ ->
                    output.writeText("partial")
                    error("encoder failed")
                },
                frameProbe = TimelapseFrameProbe { true },
            )

            val result = generator.render(session(directory, listOf(original)))

            assertTrue(result is TimelapseRenderResult.Failed)
            assertEquals("previous", previous.readText())
            assertFalse(File(generated, "timelapse.rendering.mp4").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cancelledRenderDeletesTemporaryOutputAndKeepsOriginals() = runBlocking {
        val root = Files.createTempDirectory("timelapse-render").toFile()
        try {
            val directory = File(root, "session").apply { mkdirs() }
            val originalBytes = byteArrayOf(3, 2, 1)
            val original = asset(
                File(directory, "000001_2026-08-12T17-00-01Z.jpg"),
                Instant.EPOCH,
                bytes = originalBytes,
            )
            val started = java.util.concurrent.atomic.AtomicBoolean(false)
            val generator = LocalTimelapseGenerator(
                encoder = TimelapseVideoEncoder { _, output, _ ->
                    output.parentFile?.mkdirs()
                    output.writeText("partial")
                    started.set(true)
                    awaitCancellation()
                },
                frameProbe = TimelapseFrameProbe { true },
            )

            val job = launch { generator.render(session(directory, listOf(original))) }
            while (!started.get()) yield()
            job.cancelAndJoin()

            assertFalse(File(directory, "generated/timelapse.rendering.mp4").exists())
            assertArrayEquals(originalBytes, original.file.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun unreadableOnlySessionReturnsNoFramesWithoutCreatingOutput() = runBlocking {
        val root = Files.createTempDirectory("timelapse-render").toFile()
        try {
            val directory = File(root, "session").apply { mkdirs() }
            val original = asset(File(directory, "000001_2026-08-12T17-00-01Z.jpg"), Instant.EPOCH)
            val generator = LocalTimelapseGenerator(
                encoder = TimelapseVideoEncoder { _, _, _ -> error("must not encode") },
                frameProbe = TimelapseFrameProbe { false },
            )

            val result = generator.render(session(directory, listOf(original)))

            assertTrue(result is TimelapseRenderResult.NoFrames)
            assertFalse(File(directory, "generated").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun asset(
        file: File,
        modifiedAt: Instant,
        kind: LocalSessionAssetKind = LocalSessionAssetKind.ORIGINAL_CAPTURE,
        bytes: ByteArray = byteArrayOf(1),
    ): LocalSessionAsset {
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return LocalSessionAsset(
            file = file,
            sizeBytes = file.length(),
            modifiedAtUtc = modifiedAt,
            kind = kind,
        )
    }

    private fun session(directory: File, assets: List<LocalSessionAsset>): LocalCaptureSession =
        LocalCaptureSession(
            sessionId = directory.name,
            directory = directory,
            assets = assets,
            capturedAtUtc = assets.minOfOrNull(LocalSessionAsset::modifiedAtUtc) ?: Instant.EPOCH,
            modifiedAtUtc = assets.maxOfOrNull(LocalSessionAsset::modifiedAtUtc) ?: Instant.EPOCH,
            status = LocalSessionStatus.COMPLETE,
            phaseCounts = emptyMap(),
        )
}
