package com.fatmambo33.eclipsecam.media

import com.fatmambo33.eclipsecam.capture.CapturePhase
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

class LocalMontageRenderTest {
    @Test
    fun selectsChronologicalRepresentativesFromPersistedPhaseMetadata() {
        val root = Files.createTempDirectory("montage-selection").toFile()
        try {
            val captures = listOf(
                asset(root, 0, CapturePhase.PARTIAL),
                asset(root, 1, CapturePhase.CONTACT_BURST),
                asset(root, 2, CapturePhase.TOTALITY),
                asset(root, 3, CapturePhase.TOTALITY),
                asset(root, 4, CapturePhase.TOTALITY),
                asset(root, 5, CapturePhase.CONTACT_BURST),
                asset(root, 6, CapturePhase.PARTIAL),
            ).shuffled()

            val selection = MontageFrameSelector.select(captures)

            assertEquals(
                listOf(0, 1, 3, 5, 6),
                selection.panels.mapNotNull { it.asset?.instructionIndex },
            )
            assertTrue(selection.missingSlots.isEmpty())
            assertEquals(5, selection.selectedAssets.map { it.file.canonicalPath }.distinct().size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingPhasesStayMissingAndSingletonsAreNeverDuplicated() {
        val root = Files.createTempDirectory("montage-selection").toFile()
        try {
            val partial = asset(root, 0, CapturePhase.PARTIAL)
            val contact = asset(root, 1, CapturePhase.CONTACT_BURST)

            val selection = MontageFrameSelector.select(listOf(contact, partial))

            assertEquals(partial, selection.panels[0].asset)
            assertEquals(contact, selection.panels[1].asset)
            assertEquals(null, selection.panels[2].asset)
            assertEquals(null, selection.panels[3].asset)
            assertEquals(null, selection.panels[4].asset)
            assertEquals(
                listOf(MontageSlot.TOTALITY, MontageSlot.CONTACT_LATE, MontageSlot.PARTIAL_LATE),
                selection.missingSlots,
            )
            assertEquals(2, selection.selectedAssets.distinct().size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun unclassifiedCapturesAreNotInventedIntoMontagePhases() {
        val root = Files.createTempDirectory("montage-selection").toFile()
        try {
            val unclassified = asset(root, 0, null)

            val selection = MontageFrameSelector.select(listOf(unclassified))

            assertTrue(selection.selectedAssets.isEmpty())
            assertEquals(MontageSlot.entries, selection.missingSlots)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun userCanExcludeAnAvailableRepresentativeWithoutChangingOtherSlots() {
        val root = Files.createTempDirectory("montage-selection").toFile()
        try {
            val first = asset(root, 0, CapturePhase.PARTIAL)
            val last = asset(root, 1, CapturePhase.PARTIAL)

            val selection = MontageFrameSelector.select(
                listOf(first, last),
                includedSlots = MontageSlot.entries.toSet() - MontageSlot.PARTIAL_LATE,
            )

            assertEquals(first, selection.panels[0].asset)
            assertEquals(MontagePanelState.EXCLUDED, selection.panels[4].state)
            assertEquals(null, selection.panels[4].asset)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun interruptedSessionRendersAndAtomicallyReplacesPreviousMontageWithoutMutatingOriginals() = runBlocking {
        val root = Files.createTempDirectory("montage-render").toFile()
        try {
            val directory = File(root, "session").apply { mkdirs() }
            val firstBytes = byteArrayOf(1, 2, 3)
            val secondBytes = byteArrayOf(4, 5, 6)
            val first = asset(directory, 0, CapturePhase.PARTIAL, firstBytes)
            val second = asset(directory, 1, CapturePhase.TOTALITY, secondBytes)
            val generated = File(directory, "generated").apply { mkdirs() }
            val previous = File(generated, "montage.jpg").apply { writeText("old") }
            var renderedSelection: MontageSelection? = null
            val generator = LocalMontageGenerator(
                renderer = MontageImageRenderer { selection, output ->
                    renderedSelection = selection
                    output.writeBytes(byteArrayOf(9, 8, 7))
                },
                frameProbe = MontageFrameProbe { true },
            )

            val result = generator.render(
                session(directory, listOf(second, first), LocalSessionStatus.INTERRUPTED),
            )

            assertTrue(result is MontageRenderResult.Completed)
            result as MontageRenderResult.Completed
            assertEquals(2, result.selectedFrameCount)
            assertEquals(listOf(first, second), renderedSelection?.selectedAssets)
            assertArrayEquals(byteArrayOf(9, 8, 7), previous.readBytes())
            assertFalse(File(generated, "montage.rendering.jpg").exists())
            assertArrayEquals(firstBytes, first.file.readBytes())
            assertArrayEquals(secondBytes, second.file.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun failedRenderRemovesTemporaryFileAndKeepsPreviousCompleteMontage() = runBlocking {
        val root = Files.createTempDirectory("montage-render").toFile()
        try {
            val directory = File(root, "session").apply { mkdirs() }
            val capture = asset(directory, 0, CapturePhase.PARTIAL)
            val generated = File(directory, "generated").apply { mkdirs() }
            val previous = File(generated, "montage.jpg").apply { writeText("previous") }
            val generator = LocalMontageGenerator(
                renderer = MontageImageRenderer { _, output ->
                    output.writeText("partial")
                    error("render failed")
                },
                frameProbe = MontageFrameProbe { true },
            )

            val result = generator.render(session(directory, listOf(capture)))

            assertTrue(result is MontageRenderResult.Failed)
            assertEquals("previous", previous.readText())
            assertFalse(File(generated, "montage.rendering.jpg").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cancelledRenderRemovesTemporaryFileAndPreservesOriginal() = runBlocking {
        val root = Files.createTempDirectory("montage-render").toFile()
        try {
            val directory = File(root, "session").apply { mkdirs() }
            val originalBytes = byteArrayOf(7, 7, 7)
            val capture = asset(directory, 0, CapturePhase.PARTIAL, originalBytes)
            val started = java.util.concurrent.atomic.AtomicBoolean(false)
            val generator = LocalMontageGenerator(
                renderer = MontageImageRenderer { _, output ->
                    output.parentFile?.mkdirs()
                    output.writeText("partial")
                    started.set(true)
                    awaitCancellation()
                },
                frameProbe = MontageFrameProbe { true },
            )

            val job = launch { generator.render(session(directory, listOf(capture))) }
            while (!started.get()) yield()
            job.cancelAndJoin()

            assertFalse(File(directory, "generated/montage.rendering.jpg").exists())
            assertFalse(File(directory, "generated/montage.jpg").exists())
            assertArrayEquals(originalBytes, capture.file.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun unreadableOrUnclassifiedOnlySessionReturnsNoFramesWithoutOutputDirectory() = runBlocking {
        val root = Files.createTempDirectory("montage-render").toFile()
        try {
            val directory = File(root, "session").apply { mkdirs() }
            val classified = asset(directory, 0, CapturePhase.PARTIAL)
            val unclassified = asset(directory, 1, null)
            val generator = LocalMontageGenerator(
                renderer = MontageImageRenderer { _, _ -> error("must not render") },
                frameProbe = MontageFrameProbe { file -> file != classified.file },
            )

            val result = generator.render(session(directory, listOf(classified, unclassified)))

            assertTrue(result is MontageRenderResult.NoFrames)
            assertFalse(File(directory, "generated").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun asset(
        directory: File,
        instructionIndex: Int,
        phase: CapturePhase?,
        bytes: ByteArray = byteArrayOf(1),
    ): LocalSessionAsset {
        directory.mkdirs()
        val file = File(directory, "%06d_capture.jpg".format(instructionIndex)).apply { writeBytes(bytes) }
        return LocalSessionAsset(
            file = file,
            sizeBytes = file.length(),
            modifiedAtUtc = Instant.ofEpochSecond(instructionIndex.toLong()),
            phase = phase,
            instructionIndex = instructionIndex,
        )
    }

    private fun session(
        directory: File,
        assets: List<LocalSessionAsset>,
        status: LocalSessionStatus = LocalSessionStatus.COMPLETE,
    ): LocalCaptureSession = LocalCaptureSession(
        sessionId = directory.name,
        directory = directory,
        assets = assets,
        capturedAtUtc = assets.minOfOrNull(LocalSessionAsset::modifiedAtUtc) ?: Instant.EPOCH,
        modifiedAtUtc = assets.maxOfOrNull(LocalSessionAsset::modifiedAtUtc) ?: Instant.EPOCH,
        status = status,
        phaseCounts = assets.mapNotNull(LocalSessionAsset::phase).groupingBy { it }.eachCount(),
    )
}
