package com.fatmambo33.eclipsecam.ar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val OverlayAccent = Color(0xFFFFC857)
private val OverlayReady = Color(0xFF4ADE80)
private val OverlayWarning = Color(0xFFFBBF24)
private val OverlayUnavailable = Color(0xFFFCA5A5)

data class OverlayMarker(
    val id: String,
    val xPixels: Float,
    val yPixels: Float,
    val insideViewport: Boolean,
)

data class TrajectoryOverlayModel(
    val markers: List<OverlayMarker>,
    val fit: FrameFit,
    val message: String,
) {
    val semanticSnapshot: String = buildString {
        append("fit=")
        append(fit.name)
        append(";message=")
        append(message)
        markers.forEach { marker ->
            append(";")
            append(marker.id)
            append("=")
            append(marker.xPixels.toInt())
            append(",")
            append(marker.yPixels.toInt())
            append(",")
            append(if (marker.insideViewport) "inside" else "outside")
        }
    }
}

fun FramingAssessment.toOverlayModel(): TrajectoryOverlayModel = TrajectoryOverlayModel(
    markers = projectedSamples.mapNotNull { sample ->
        val visible = sample.result as? ProjectionResult.Visible ?: return@mapNotNull null
        OverlayMarker(
            id = sample.id,
            xPixels = visible.point.xPixels.toFloat(),
            yPixels = visible.point.yPixels.toFloat(),
            insideViewport = visible.point.insideViewport,
        )
    },
    fit = fit,
    message = message,
)

@Composable
fun EclipseTrajectoryOverlay(
    assessment: FramingAssessment?,
    modifier: Modifier = Modifier,
) {
    val model = assessment?.toOverlayModel() ?: TrajectoryOverlayModel(
        markers = emptyList(),
        fit = FrameFit.UNAVAILABLE,
        message = "Trajectory unavailable. Enable location and wait for a reliable orientation fix.",
    )
    val statusColor = when (model.fit) {
        FrameFit.FITS -> OverlayReady
        FrameFit.CLIPPED, FrameFit.BEHIND_CAMERA -> OverlayWarning
        FrameFit.UNAVAILABLE -> OverlayUnavailable
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = model.semanticSnapshot },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(size.width / 2f - 24f, size.height / 2f),
                end = Offset(size.width / 2f + 24f, size.height / 2f),
                strokeWidth = 2f,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(size.width / 2f, size.height / 2f - 24f),
                end = Offset(size.width / 2f, size.height / 2f + 24f),
                strokeWidth = 2f,
            )

            val visibleMarkers = model.markers.filter(OverlayMarker::insideViewport)
            if (visibleMarkers.size > 1) {
                val path = Path().apply {
                    moveTo(visibleMarkers.first().xPixels, visibleMarkers.first().yPixels)
                    visibleMarkers.drop(1).forEach { lineTo(it.xPixels, it.yPixels) }
                }
                drawPath(path, OverlayAccent, style = Stroke(width = 5f))
            }
            visibleMarkers.forEach { marker ->
                drawCircle(OverlayAccent, radius = if (marker.id == "MAX") 12f else 8f, center = Offset(marker.xPixels, marker.yPixels))
                drawCircle(Color.Black, radius = if (marker.id == "MAX") 5f else 3f, center = Offset(marker.xPixels, marker.yPixels))
            }
        }

        Text(
            text = model.message,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp),
            color = statusColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
