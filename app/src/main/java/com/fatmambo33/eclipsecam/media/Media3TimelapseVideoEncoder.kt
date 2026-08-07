package com.fatmambo33.eclipsecam.media

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Cheap Android decoder probe used to skip missing or corrupt JPEG inputs before export. */
class AndroidJpegTimelapseFrameProbe : TimelapseFrameProbe {
    override fun isReadable(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth > 0 && options.outHeight > 0
    }
}

/**
 * Media3 Transformer encoder for a silent H.264/MP4 eclipse timelapse.
 *
 * Each source JPEG contributes one 100 ms image clip at 10 fps. Media3 performs decoding,
 * composition, scaling and hardware/software video encoding off the application thread while its
 * control surface remains on the main looper as required by Transformer.
 */
@UnstableApi
class Media3TimelapseVideoEncoder(
    context: Context,
    private val frameRate: Int = DEFAULT_FRAME_RATE,
) : TimelapseVideoEncoder {
    private val applicationContext = context.applicationContext
    private val frameDurationMs: Long

    init {
        require(frameRate in 1..60) { "Timelapse frame rate must be between 1 and 60 fps." }
        frameDurationMs = 1_000L / frameRate
    }

    override suspend fun encode(
        frames: List<TimelapseFrame>,
        output: File,
        onProgress: (Int) -> Unit,
    ) = withContext(Dispatchers.Main.immediate) {
        require(frames.isNotEmpty()) { "At least one timelapse frame is required." }
        require(!output.exists()) { "Temporary timelapse output must not already exist." }

        val composition = Composition.Builder(
            listOf(
                EditedMediaItemSequence.withVideoFrom(
                    frames.map { frame ->
                        val mediaItem = MediaItem.Builder()
                            .setUri(Uri.fromFile(frame.file))
                            .setImageDurationMs(frameDurationMs)
                            .build()
                        EditedMediaItem.Builder(mediaItem)
                            .setFrameRate(frameRate)
                            .build()
                    },
                ),
            ),
        ).build()
        val handler = Handler(Looper.getMainLooper())

        suspendCancellableCoroutine { continuation ->
            val progressHolder = ProgressHolder()
            lateinit var transformer: Transformer
            lateinit var progressPoller: Runnable

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    handler.removeCallbacks(progressPoller)
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    handler.removeCallbacks(progressPoller)
                    if (continuation.isActive) continuation.resumeWithException(exportException)
                }
            }

            transformer = Transformer.Builder(applicationContext)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(listener)
                .build()

            progressPoller = object : Runnable {
                override fun run() {
                    if (!continuation.isActive) return
                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(progressHolder.progress)
                    }
                    if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                        handler.postDelayed(this, PROGRESS_POLL_MS)
                    }
                }
            }

            continuation.invokeOnCancellation {
                handler.post {
                    handler.removeCallbacks(progressPoller)
                    transformer.cancel()
                    output.delete()
                }
            }

            transformer.start(composition, output.absolutePath)
            handler.post(progressPoller)
        }
    }

    private companion object {
        const val DEFAULT_FRAME_RATE = 10
        const val PROGRESS_POLL_MS = 250L
    }
}
