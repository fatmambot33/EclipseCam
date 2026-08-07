package com.fatmambo33.eclipsecam.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatmambo33.eclipsecam.capture.CapturePhase
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val GalleryAccent = Color(0xFF60A5FA)
private val GalleryCard = Color(0xFF111827)
private val GalleryMuted = Color(0xFFCBD5E1)
private val GalleryWarning = Color(0xFFFBBF24)
private val GalleryFailed = Color(0xFFFCA5A5)
private val GalleryReady = Color(0xFF4ADE80)

private sealed interface GalleryUiState {
    data object Loading : GalleryUiState
    data class Ready(val sessions: List<LocalCaptureSession>) : GalleryUiState
    data class Error(val message: String) : GalleryUiState
}

/** Offline browser for app-private capture sessions and generated local media. */
@Composable
fun LocalGalleryScreen(rootDirectory: File? = null) {
    val context = LocalContext.current
    val root = remember(rootDirectory, context) {
        rootDirectory ?: File(context.filesDir, "captures")
    }
    var state by remember(root) { mutableStateOf<GalleryUiState>(GalleryUiState.Loading) }
    var selectedSessionId by remember(root) { mutableStateOf<String?>(null) }

    LaunchedEffect(root) {
        state = GalleryUiState.Loading
        state = withContext(Dispatchers.IO) {
            runCatching { LocalSessionIndex(root).listSessions() }
                .fold(
                    onSuccess = GalleryUiState::Ready,
                    onFailure = { error ->
                        GalleryUiState.Error(error.message ?: "Unable to read local eclipse sessions.")
                    },
                )
        }
    }

    val selected = (state as? GalleryUiState.Ready)
        ?.sessions
        ?.firstOrNull { it.sessionId == selectedSessionId }

    if (selected != null) {
        SessionDetail(
            session = selected,
            onBack = { selectedSessionId = null },
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text("EclipseCam", style = MaterialTheme.typography.labelLarge, color = GalleryAccent)
        Text(
            "Your eclipse sessions",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )

        when (val current = state) {
            GalleryUiState.Loading -> LoadingState()
            is GalleryUiState.Error -> ErrorState(current.message)
            is GalleryUiState.Ready -> if (current.sessions.isEmpty()) {
                EmptyState()
            } else {
                SessionList(
                    sessions = current.sessions,
                    onSelect = { selectedSessionId = it.sessionId },
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("gallery-loading"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text("Reading local sessions…", color = GalleryMuted)
    }
}

@Composable
private fun EmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("gallery-empty"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GalleryCard),
    ) {
        Column(Modifier.padding(22.dp)) {
            Text("Nothing captured yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Photos and generated media stay on this phone until you explicitly export or share them.",
                modifier = Modifier.padding(top = 12.dp),
                color = GalleryMuted,
            )
        }
    }
}

@Composable
private fun ErrorState(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("gallery-error"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GalleryCard),
    ) {
        Column(Modifier.padding(22.dp)) {
            Text("Gallery unavailable", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(message, modifier = Modifier.padding(top = 12.dp), color = GalleryFailed)
        }
    }
}

@Composable
private fun SessionList(
    sessions: List<LocalCaptureSession>,
    onSelect: (LocalCaptureSession) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("gallery-list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(sessions, key = LocalCaptureSession::sessionId) { session ->
            SessionCard(session = session, onClick = { onSelect(session) })
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SessionCard(session: LocalCaptureSession, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("gallery-session-${session.sessionId}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GalleryCard),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(session.sessionId, fontWeight = FontWeight.Bold)
                Text(
                    statusLabel(session.status),
                    color = statusColor(session.status),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                SESSION_DATE.format(session.capturedAtUtc),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = GalleryMuted,
            )
            Text(
                "${session.captures.size} capture${if (session.captures.size == 1) "" else "s"} • " +
                    "${session.generatedAssets.size} generated output${if (session.generatedAssets.size == 1) "" else "s"}",
                modifier = Modifier.padding(top = 10.dp),
            )
            phaseSummary(session)?.let { summary ->
                Text(summary, modifier = Modifier.padding(top = 6.dp), color = GalleryMuted)
            }
        }
    }
}

@Composable
private fun SessionDetail(session: LocalCaptureSession, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)
            .testTag("gallery-detail"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("EclipseCam", style = MaterialTheme.typography.labelLarge, color = GalleryAccent)
            Text(
                session.sessionId,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Button(onClick = onBack) { Text("Back to sessions") }
        }
        item {
            DetailCard(
                "Session",
                "${statusLabel(session.status)} • ${SESSION_DATE.format(session.capturedAtUtc)}\n" +
                    "${session.captures.size} captures • ${formatBytes(session.assets.sumOf(LocalSessionAsset::sizeBytes))}",
            )
        }
        if (session.phaseCounts.isNotEmpty()) {
            item {
                DetailCard(
                    "Captured phases",
                    CapturePhase.entries.joinToString("\n") { phase ->
                        "${phaseLabel(phase)}: ${session.phaseCounts[phase] ?: 0}"
                    },
                )
            }
        }
        item {
            DetailCard(
                "Generated outputs",
                if (session.generatedAssets.isEmpty()) {
                    "None yet. Originals remain available locally."
                } else {
                    session.generatedAssets.joinToString("\n") { asset ->
                        "${assetLabel(asset.kind)} • ${asset.file.name} • ${formatBytes(asset.sizeBytes)}"
                    }
                },
            )
        }
        item {
            DetailCard(
                "Original captures",
                if (session.captures.isEmpty()) {
                    "No readable JPEG captures found. The session remains visible for recovery and diagnostics."
                } else {
                    session.captures.take(12).joinToString("\n") { asset ->
                        "${asset.file.name} • ${formatBytes(asset.sizeBytes)}"
                    } + if (session.captures.size > 12) "\n+${session.captures.size - 12} more" else ""
                },
            )
        }
    }
}

@Composable
private fun DetailCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GalleryCard),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, modifier = Modifier.padding(top = 8.dp), color = GalleryMuted)
        }
    }
}

private fun phaseSummary(session: LocalCaptureSession): String? {
    if (session.phaseCounts.isEmpty()) return null
    return CapturePhase.entries
        .mapNotNull { phase -> session.phaseCounts[phase]?.let { count -> "${phaseLabel(phase)} $count" } }
        .joinToString(" • ")
}

private fun phaseLabel(phase: CapturePhase): String = when (phase) {
    CapturePhase.PARTIAL -> "Partial"
    CapturePhase.CONTACT_BURST -> "Contact burst"
    CapturePhase.TOTALITY -> "Totality"
}

private fun statusLabel(status: LocalSessionStatus): String = when (status) {
    LocalSessionStatus.COMPLETE -> "Complete"
    LocalSessionStatus.PAUSED -> "Paused"
    LocalSessionStatus.FAILED -> "Failed"
    LocalSessionStatus.INTERRUPTED -> "Interrupted"
}

private fun statusColor(status: LocalSessionStatus): Color = when (status) {
    LocalSessionStatus.COMPLETE -> GalleryReady
    LocalSessionStatus.PAUSED -> GalleryWarning
    LocalSessionStatus.FAILED -> GalleryFailed
    LocalSessionStatus.INTERRUPTED -> GalleryWarning
}

private fun assetLabel(kind: LocalSessionAssetKind): String = when (kind) {
    LocalSessionAssetKind.ORIGINAL_CAPTURE -> "Original"
    LocalSessionAssetKind.TIMELAPSE -> "Timelapse"
    LocalSessionAssetKind.MONTAGE -> "Montage"
    LocalSessionAssetKind.CAPTURE_REPORT -> "Capture report"
    LocalSessionAssetKind.GENERATED -> "Generated"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes.toDouble() / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes.toDouble() / 1024.0)
    else -> "$bytes B"
}

private val SESSION_DATE: DateTimeFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm")
    .withZone(ZoneId.systemDefault())

@Suppress("unused")
private fun formatInstant(instant: Instant): String = SESSION_DATE.format(instant)
