package com.fatmambo33.eclipsecam.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android decoder probe used before phase-aware montage selection. */
class AndroidJpegMontageFrameProbe : MontageFrameProbe {
    override fun isReadable(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth > 0 && options.outHeight > 0
    }
}

/**
 * Deterministic local JPEG renderer for the five-slot eclipse phase montage.
 *
 * Missing and user-excluded slots remain visible as labelled placeholders, making an interrupted
 * session honest in the exported image instead of silently duplicating neighbouring phases.
 */
class AndroidMontageImageRenderer : MontageImageRenderer {
    override suspend fun render(selection: MontageSelection, output: File) = withContext(Dispatchers.Default) {
        val canvasBitmap = Bitmap.createBitmap(CANVAS_WIDTH, CANVAS_HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(canvasBitmap)
            canvas.drawColor(BACKGROUND)
            drawHeader(canvas)
            selection.panels.forEachIndexed { index, panel ->
                drawPanel(canvas, panel, index)
            }
            output.parentFile?.mkdirs()
            FileOutputStream(output).use { stream ->
                check(canvasBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                    "Android failed to encode the montage JPEG."
                }
            }
        } finally {
            canvasBitmap.recycle()
        }
    }

    private fun drawHeader(canvas: Canvas) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MUTED
            textSize = 26f
        }
        canvas.drawText("Eclipse phase montage", LEFT_MARGIN.toFloat(), 72f, titlePaint)
        canvas.drawText(
            "Representative captures • missing phases are shown explicitly",
            LEFT_MARGIN.toFloat(),
            112f,
            subtitlePaint,
        )
    }

    private fun drawPanel(canvas: Canvas, panel: MontagePanel, index: Int) {
        val left = LEFT_MARGIN + index * (PANEL_WIDTH + PANEL_GAP)
        val imageRect = RectF(
            left.toFloat(),
            PANEL_TOP.toFloat(),
            (left + PANEL_WIDTH).toFloat(),
            (PANEL_TOP + IMAGE_HEIGHT).toFloat(),
        )
        val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PANEL_BACKGROUND
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            RectF(
                imageRect.left,
                imageRect.top,
                imageRect.right,
                (PANEL_TOP + PANEL_HEIGHT).toFloat(),
            ),
            18f,
            18f,
            panelPaint,
        )

        when (panel.state) {
            MontagePanelState.SELECTED -> {
                val source = checkNotNull(panel.asset) { "Selected montage panel requires a source asset." }
                val bitmap = decodeSampled(source.file)
                    ?: error("Unable to decode selected montage frame ${source.file.name}.")
                try {
                    drawCenterCrop(canvas, bitmap, imageRect)
                } finally {
                    bitmap.recycle()
                }
            }
            MontagePanelState.MISSING -> drawPlaceholder(canvas, imageRect, "Not captured")
            MontagePanelState.EXCLUDED -> drawPlaceholder(canvas, imageRect, "Not selected")
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 25f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MUTED
            textSize = 21f
        }
        canvas.drawText(
            slotLabel(panel.slot),
            left.toFloat() + 16f,
            (PANEL_TOP + IMAGE_HEIGHT + 42).toFloat(),
            labelPaint,
        )
        val detail = when (panel.state) {
            MontagePanelState.SELECTED -> panel.asset?.file?.name ?: "Selected"
            MontagePanelState.MISSING -> "No classified capture available"
            MontagePanelState.EXCLUDED -> "Excluded before generation"
        }
        canvas.drawText(
            ellipsize(detail, MAX_DETAIL_CHARS),
            left.toFloat() + 16f,
            (PANEL_TOP + IMAGE_HEIGHT + 76).toFloat(),
            detailPaint,
        )
    }

    private fun drawPlaceholder(canvas: Canvas, destination: RectF, message: String) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PLACEHOLDER }
        canvas.drawRect(destination, fill)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MUTED
            textSize = 28f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(message, destination.centerX(), destination.centerY(), text)
    }

    private fun drawCenterCrop(canvas: Canvas, bitmap: Bitmap, destination: RectF) {
        val sourceRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val destinationRatio = destination.width() / destination.height()
        val source = if (sourceRatio > destinationRatio) {
            val width = (bitmap.height * destinationRatio).toInt().coerceAtLeast(1)
            val left = ((bitmap.width - width) / 2).coerceAtLeast(0)
            Rect(left, 0, (left + width).coerceAtMost(bitmap.width), bitmap.height)
        } else {
            val height = (bitmap.width / destinationRatio).toInt().coerceAtLeast(1)
            val top = ((bitmap.height - height) / 2).coerceAtLeast(0)
            Rect(0, top, bitmap.width, (top + height).coerceAtMost(bitmap.height))
        }
        canvas.drawBitmap(bitmap, source, destination, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun decodeSampled(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > PANEL_WIDTH * 2 || bounds.outHeight / sample > IMAGE_HEIGHT * 2) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun slotLabel(slot: MontageSlot): String = when (slot) {
        MontageSlot.PARTIAL_EARLY -> "Partial • early"
        MontageSlot.CONTACT_EARLY -> "Contact burst • early"
        MontageSlot.TOTALITY -> "Totality representative"
        MontageSlot.CONTACT_LATE -> "Contact burst • late"
        MontageSlot.PARTIAL_LATE -> "Partial • late"
    }

    private fun ellipsize(value: String, maxChars: Int): String =
        if (value.length <= maxChars) value else value.take(maxChars - 1) + "…"

    private companion object {
        const val CANVAS_WIDTH = 1800
        const val CANVAS_HEIGHT = 800
        const val LEFT_MARGIN = 72
        const val PANEL_TOP = 150
        const val PANEL_WIDTH = 312
        const val IMAGE_HEIGHT = 480
        const val PANEL_HEIGHT = 590
        const val PANEL_GAP = 24
        const val JPEG_QUALITY = 92
        const val MAX_DETAIL_CHARS = 32
        val BACKGROUND: Int = Color.rgb(7, 10, 18)
        val PANEL_BACKGROUND: Int = Color.rgb(17, 24, 39)
        val PLACEHOLDER: Int = Color.rgb(31, 41, 55)
        val MUTED: Int = Color.rgb(203, 213, 225)
    }
}
