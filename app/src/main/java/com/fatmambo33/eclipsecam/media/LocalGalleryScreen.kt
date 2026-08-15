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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatmambo33.eclipsecam.R
import com.fatmambo33.eclipsecam.capture.CapturePhase
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
    data object Error : GalleryUiState
}

private sealed interface TimelapseUiState {
    data object Idle : TimelapseUiState
    data class Rendering(val progress: Int) : TimelapseUiState
    data class Complete(val frameCount: Int) : TimelapseUiState
    data class Failed(val reason: String) : TimelapseUiState
    data object Cancelled : TimelapseUiState
}

/** Offline browser for app-private capture sessions and generated local media. */
@Composable
fun LocalGalleryScreen(rootDirectory: File? = null) {
    val context = LocalContext.current
    val root = remember(rootDirectory, context) {
        rootDirectory ?: File(context.filesDir, "captures")
    }
    val scope = rememberCoroutineScope()
    val timelapseGenerator = remember(context) {
        LocalTimelapseGenerator(
            encoder = Media3TimelapseVideoEncoder(context.applicationContext),
            frameProbe = AndroidJpegTimelapseFrameProbe(),
        )
    }
    var state by remember(root) { mutableStateOf<GalleryUiState>(GalleryUiState.Loading) }
    var selectedSessionId by remember(root) { mutableStateOf<String?>(null) }
    var refreshToken by remember(root) { mutableStateOf(0) }
    var timelapseState by remember(root) { mutableStateOf<TimelapseUiState>(TimelapseUiState.Idle) }
    var timelapseJob by remember(root) { mutableStateOf<Job?>(null) }

    LaunchedEffect(root, refreshToken) {
        state = GalleryUiState.Loading
        state = withContext(Dispatchers.IO) {
            runCatching { LocalSessionIndex(root).listSessions() }
                .fold(
                    onSuccess = GalleryUiState::Ready,
                    onFailure = { GalleryUiState.Error },
                )
        }
    }

    val selected = (state as? GalleryUiState.Ready)
        ?.sessions
        ?.firstOrNull { it.sessionId == selectedSessionId }

    if (selected != null) {
        SessionDetail(
            session = selected,
            timelapseState = timelapseState,
            onBack = { selectedSessionId = null },
            onStartTimelapse = {
                if (timelapseJob?.isActive != true) {
                    timelapseState = TimelapseUiState.Rendering(0)
                    timelapseJob = scope.launch {
                        try {
                            when (
                                val result = timelapseGenerator.render(selected) { progress ->
                                    timelapseState = TimelapseUiState.Rendering(progress)
                                }
                            ) {
                                is TimelapseRenderResult.Completed -> {
                                    timelapseState = TimelapseUiState.Complete(result.frameCount)
                                    refreshToken += 1
                                }
                                is TimelapseRenderResult.NoFrames -> {
                                    timelapseState = TimelapseUiState.Failed(result.reason)
                                }
                                is TimelapseRenderResult.Failed -> {
                                    timelapseState = TimelapseUiState.Failed(result.reason)
                                }
                            }
                        } catch (_: CancellationException) {
                            timelapseState = TimelapseUiState.Cancelled
                        }
                    }
                }
            },
            onCancelTimelapse = { timelapseJob?.cancel() },
            onMontageGenerated = { refreshToken += 1 },
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge, color = GalleryAccent)
        Text(
            stringResource(R.string.gallery_sessions_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )

        when (val current = state) {
            GalleryUiState.Loading -> LoadingState()
            GalleryUiState.Error -> ErrorState()
            is GalleryUiState.Ready -> if (current.sessions.isEmpty()) {
                EmptyState()
            } else {
                SessionList(
                    sessions = current.sessions,
                    onSelect = {
                        selectedSessionId = it.sessionId
                        timelapseState = TimelapseUiState.Idle
                    },
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
        Text(stringResource(R.string.gallery_sessions_loading), color = GalleryMuted)
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
            Text(
                stringResource(R.string.gallery_sessions_empty_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.gallery_sessions_empty_body),
                modifier = Modifier.padding(top = 12.dp),
                color = GalleryMuted,
            )
        }
    }
}

@Composable
private fun ErrorState() {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("gallery-error"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = GalleryCard),
    ) {
        Column(Modifier.padding(22.dp)) {
            Text(
                stringResource(R.string.gallery_sessions_error_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.gallery_sessions_error_body),
                modifier = Modifier.padding(top = 12.dp),
                color = GalleryFailed,
            )
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
                formatSessionDate(session.capturedAtUtc),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = GalleryMuted,
            )
            Text(
                stringResource(
                    R.string.gallery_session_counts_format,
                    session.captures.size,
                    session.generatedAssets.size,
                ),
                modifier = Modifier.padding(top = 10.dp),
            )
            phaseSummary(session)?.let { summary ->
                Text(summary, modifier = Modifier.padding(top = 6.dp), color = GalleryMuted)
            }
        }
    }
}

@Composable
private fun SessionDetail(
    session: LocalCaptureSession,
    timelapseState: TimelapseUiState,
    onBack: () -> Unit,
    onStartTimelapse: () -> Unit,
    onCancelTimelapse: () -> Unit,
    onMontageGenerated: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)
            .testTag("gallery-detail"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge, color = GalleryAccent)
            Text(
                session.sessionId,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Button(onClick = onBack) { Text(stringResource(R.string.gallery_back_to_sessions)) }
        }
        item {
            DetailCard(
                stringResource(R.string.gallery_session_detail_title),
                stringResource(
                    R.string.gallery_session_detail_format,
                    statusLabel(session.status),
                    formatSessionDate(session.capturedAtUtc),
                    session.captures.size,
                    formatBytes(session.assets.sumOf(LocalSessionAsset::sizeBytes)),
                ),
            )
        }
        item {
            TimelapseCard(
                session = session,
                state = timelapseState,
                onStart = onStartTimelapse,
                onCancel = onCancelTimelapse,
            )
        }
        item {
            LocalMontageCard(
                session = session,
                onGenerated = onMontageGenerated,
            )
        }
        if (session.phaseCounts.isNotEmpty()) {
            item {
                DetailCard(
                    stringResource(R.string.gallery_captured_phases_title),
                    capturedPhasesBody(session),
                )
            }
        }
        item {
            DetailCard(
                stringResource(R.string.gallery_generated_outputs_title),
                generatedOutputsBody(session),
            )
        }
        item {
            DetailCard(
                stringResource(R.string.gallery_original_captures_title),
                originalCapturesBody(session),
            )
        }
    }
}

@Composable
private fun capturedPhasesBody(session: LocalCaptureSession): String {
    val lines = mutableListOf<String>()
    for (phase in CapturePhase.entries) {
        lines += stringResource(
            R.string.gallery_phase_count_format,
            phaseLabel(phase),
            session.phaseCounts[phase] ?: 0,
        )
    }
    return lines.joinToString("\n")
}

@Composable
private fun generatedOutputsBody(session: LocalCaptureSession): String {
    if (session.generatedAssets.isEmpty()) {
        return stringResource(R.string.gallery_generated_outputs_empty)
    }
    val lines = mutableListOf<String>()
    for (asset in session.generatedAssets) {
        lines += stringResource(
            R.string.gallery_generated_asset_format,
            assetLabel(asset.kind),
            asset.file.name,
            formatBytes(asset.sizeBytes),
        )
    }
    return lines.joinToString("\n")
}

@Composable
private fun originalCapturesBody(session: LocalCaptureSession): String {
    if (session.captures.isEmpty()) {
        return stringResource(R.string.gallery_original_captures_empty)
    }
    val lines = mutableListOf<String>()
    for (asset in session.captures.take(12)) {
        lines += stringResource(
            R.string.gallery_original_asset_format,
            asset.file.name,
            formatBytes(asset.sizeBytes),
        )
    }
    if (session.captures.size > 12) {
        lines += stringResource(R.string.gallery_more_captures_format, session.captures.size - 12)
    }
    return lines.joinToString("\n")
}

@Composable
private fun TimelapseCard(
    session: LocalCaptureSession,
    state: TimelapseUiState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    val hasTimelapse = session.generatedAssets.any { it.kind == LocalSessionAssetKind.TIMELAPSE }
    Card(
        modifier = Modifier.fillMaxWidth().testTag("timelapse-card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GalleryCard),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(stringResource(R.string.gallery_timelapse_title), fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.gallery_timelapse_body),
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                color = GalleryMuted,
            )
            when (state) {
                TimelapseUiState.Idle -> Text(
                    stringResource(
                        if (hasTimelapse) R.string.gallery_timelapse_available else R.string.gallery_timelapse_ready,
                    ),
                    color = GalleryMuted,
                    modifier = Modifier.testTag("timelapse-status"),
                )
                is TimelapseUiState.Rendering -> Text(
                    stringResource(R.string.gallery_timelapse_rendering_format, state.progress),
                    color = GalleryAccent,
                    modifier = Modifier.testTag("timelapse-status"),
                )
                is TimelapseUiState.Complete -> Text(
                    stringResource(R.string.gallery_timelapse_complete_format, state.frameCount),
                    color = GalleryReady,
                    modifier = Modifier.testTag("timelapse-status"),
                )
                is TimelapseUiState.Failed -> Text(
                    state.reason,
                    color = GalleryFailed,
                    modifier = Modifier.testTag("timelapse-status"),
                )
                TimelapseUiState.Cancelled -> Text(
                    stringResource(R.string.gallery_timelapse_cancelled),
                    color = GalleryWarning,
                    modifier = Modifier.testTag("timelapse-status"),
                )
            }
            Spacer(Modifier.height(12.dp))
            if (state is TimelapseUiState.Rendering) {
                Button(onClick = onCancel, modifier = Modifier.testTag("timelapse-cancel")) {
                    Text(stringResource(R.string.gallery_timelapse_cancel))
                }
            } else {
                Button(
                    onClick = onStart,
                    enabled = session.captures.isNotEmpty(),
                    modifier = Modifier.testTag("timelapse-start"),
                ) {
                    Text(
                        stringResource(
                            if (hasTimelapse) {
                                R.string.gallery_timelapse_regenerate
                            } else {
                                R.string.gallery_timelapse_generate
                            },
                        ),
                    )
                }
            }
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

@Composable
private fun phaseSummary(session: LocalCaptureSession): String? {
    if (session.phaseCounts.isEmpty()) return null
    val parts = mutableListOf<String>()
    for (phase in CapturePhase.entries) {
        val count = session.phaseCounts[phase] ?: continue
        parts += stringResource(R.string.gallery_phase_summary_item_format, phaseLabel(phase), count)
    }
    return parts.joinToString(" • ")
}

@Composable
private fun phaseLabel(phase: CapturePhase): String = stringResource(
    when (phase) {
        CapturePhase.PARTIAL -> R.string.gallery_phase_partial
        CapturePhase.CONTACT_BURST -> R.string.gallery_phase_contact_burst
        CapturePhase.TOTALITY -> R.string.gallery_phase_totality
    },
)

@Composable
private fun statusLabel(status: LocalSessionStatus): String = stringResource(
    when (status) {
        LocalSessionStatus.COMPLETE -> R.string.gallery_status_complete
        LocalSessionStatus.PAUSED -> R.string.gallery_status_paused
        LocalSessionStatus.FAILED -> R.string.gallery_status_failed
        LocalSessionStatus.INTERRUPTED -> R.string.gallery_status_interrupted
    },
)

private fun statusColor(status: LocalSessionStatus): Color = when (status) {
    LocalSessionStatus.COMPLETE -> GalleryReady
    LocalSessionStatus.PAUSED -> GalleryWarning
    LocalSessionStatus.FAILED -> GalleryFailed
    LocalSessionStatus.INTERRUPTED -> GalleryWarning
}

@Composable
private fun assetLabel(kind: LocalSessionAssetKind): String = stringResource(
    when (kind) {
        LocalSessionAssetKind.ORIGINAL_CAPTURE -> R.string.gallery_asset_original
        LocalSessionAssetKind.TIMELAPSE -> R.string.gallery_asset_timelapse
        LocalSessionAssetKind.MONTAGE -> R.string.gallery_asset_montage
        LocalSessionAssetKind.CAPTURE_REPORT -> R.string.gallery_asset_report
        LocalSessionAssetKind.GENERATED -> R.string.gallery_asset_generated
    },
)

@Composable
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> stringResource(
        R.string.gallery_size_megabytes_format,
        bytes.toDouble() / (1024.0 * 1024.0),
    )
    bytes >= 1024L -> stringResource(
        R.string.gallery_size_kilobytes_format,
        bytes.toDouble() / 1024.0,
    )
    else -> stringResource(R.string.gallery_size_bytes_format, bytes)
}

@Composable
private fun formatSessionDate(instant: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(ZoneId.systemDefault())
    }
    return formatter.format(instant)
}
