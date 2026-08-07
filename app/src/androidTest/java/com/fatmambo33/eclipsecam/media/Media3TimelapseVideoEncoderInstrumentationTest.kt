package com.fatmambo33.eclipsecam.media

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Media3TimelapseVideoEncoderInstrumentationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root: File
        get() = File(context.filesDir, "timelapse-instrumentation")

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
    fun rendersH264Mp4ThatAndroidCanDecodeAndGalleryCanRediscover() = runBlocking {
        val directory = File(root, "render-complete").apply { mkdirs() }
        writeJpeg(File(directory, "000001_2026-08-12T17-00-01Z.jpg"), Color.RED)
        writeJpeg(File(directory, "000002_2026-08-12T17-00-02Z.jpg"), Color.BLUE)
        File(directory, "session.complete").writeText("complete\n")
        val session = LocalSessionIndex(root).listSessions().single()
        val progress = mutableListOf<Int>()
        val generator = LocalTimelapseGenerator(
            encoder = Media3TimelapseVideoEncoder(context),
            frameProbe = AndroidJpegTimelapseFrameProbe(),
        )

        val result = generator.render(session, progress::add)

        assertTrue(result is TimelapseRenderResult.Completed)
        result as TimelapseRenderResult.Completed
        assertEquals(2, result.frameCount)
        assertTrue(result.output.isFile)
        assertTrue(result.output.length() > 0L)
        assertEquals(0, progress.first())
        assertEquals(100, progress.last())

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(result.output.absolutePath)
            val videoMime = (0 until extractor.trackCount)
                .map { index -> extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME) }
                .firstOrNull { mime -> mime?.startsWith("video/") == true }
            assertEquals("video/avc", videoMime)
        } finally {
            extractor.release()
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(result.output.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            assertTrue(durationMs > 0L)
            assertNotNull(retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC))
        } finally {
            retriever.release()
        }

        val reindexed = LocalSessionIndex(root).listSessions().single()
        assertTrue(reindexed.generatedAssets.any { it.kind == LocalSessionAssetKind.TIMELAPSE })
        assertFalse(File(directory, "generated/timelapse.rendering.mp4").exists())
    }

    @Test
    fun cancellationRemovesOnlyTemporaryOutputOnAndroid() = runBlocking {
        val directory = File(root, "render-cancelled").apply { mkdirs() }
        val original = File(directory, "000001_2026-08-12T17-00-01Z.jpg")
        writeJpeg(original, Color.WHITE)
        File(directory, "session.complete").writeText("complete\n")
        val originalBytes = original.readBytes()
        val session = LocalSessionIndex(root).listSessions().single()
        val started = AtomicBoolean(false)
        val generator = LocalTimelapseGenerator(
            encoder = TimelapseVideoEncoder { _, output, _ ->
                output.writeText("partial")
                started.set(true)
                awaitCancellation()
            },
            frameProbe = AndroidJpegTimelapseFrameProbe(),
        )

        val job = launch { generator.render(session) }
        while (!started.get()) yield()
        job.cancelAndJoin()

        assertFalse(File(directory, "generated/timelapse.rendering.mp4").exists())
        assertFalse(File(directory, "generated/timelapse.mp4").exists())
        assertTrue(originalBytes.contentEquals(original.readBytes()))
    }

    private fun writeJpeg(file: File, color: Int) {
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
        }
        bitmap.recycle()
        file.setLastModified(Instant.now().toEpochMilli())
    }
}
