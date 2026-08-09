package com.fatmambo33.eclipsecam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.fatmambo33.eclipsecam.camera.preview.CameraPreviewState
import com.fatmambo33.eclipsecam.camera.preview.CameraPreviewSurface
import com.fatmambo33.eclipsecam.camera.preview.PreviewLens
import com.fatmambo33.eclipsecam.device.location.AndroidLocationRepository
import com.fatmambo33.eclipsecam.device.location.ObserverGuidanceFlow
import com.fatmambo33.eclipsecam.device.location.ObserverGuidanceState
import com.fatmambo33.eclipsecam.map.EclipseMapScene
import com.fatmambo33.eclipsecam.map.EclipseMapScene2026
import com.fatmambo33.eclipsecam.map.GeoPoint
import com.fatmambo33.eclipsecam.map.ObserverEclipseMap
import com.fatmambo33.eclipsecam.media.LocalGalleryScreen
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

private val EclipseBackground = Color(0xFF070A12)
private val EclipseCard = Color(0xFF111827)
private val EclipseAccent = Color(0xFFFFC857)
private val EclipseReady = Color(0xFF4ADE80)
private val EclipseWarning = Color(0xFFFBBF24)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = EclipseAccent,
                    background = EclipseBackground,
                    surface = EclipseCard,
                ),
            ) {
                EclipseCamApp()
            }
        }
    }
}

private enum class AppTab(@StringRes val labelRes: Int, val icon: ImageVector) {
    Camera(R.string.tab_camera, Icons.Default.CameraAlt),
    Live(R.string.tab_live, Icons.Default.NightsStay),
    Position(R.string.tab_position, Icons.Default.LocationOn),
    Gallery(R.string.tab_gallery, Icons.Default.Collections),
}

@Composable
private fun EclipseCamApp() {
    var selectedTab by remember { mutableStateOf(AppTab.Camera) }
    Scaffold(
        containerColor = EclipseBackground,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0B1220)) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                        modifier = Modifier.testTag("tab-${tab.name.lowercase()}"),
                    )
                }
            }
        },
    ) { padding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding),
            color = EclipseBackground,
        ) {
            when (selectedTab) {
                AppTab.Camera -> CameraScreen()
                AppTab.Live -> LiveScreen()
                AppTab.Position -> PositionScreen()
                AppTab.Gallery -> GalleryScreen()
            }
        }
    }
}

@Composable
private fun CameraScreen() {
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var previewState by remember {
        mutableStateOf<CameraPreviewState>(
            if (cameraGranted) CameraPreviewState.Starting else CameraPreviewState.WaitingForPermission,
        )
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraGranted = it
    }

    ScreenColumn(stringResource(R.string.screen_prepare_phone)) {
        HeroCard(
            previewTitle(previewState),
            previewMessage(previewState),
            if (previewState is CameraPreviewState.Streaming) EclipseReady else EclipseWarning,
            if (previewState is CameraPreviewState.Streaming) {
                stringResource(R.string.status_ready)
            } else {
                stringResource(R.string.status_needs_attention)
            },
        )
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth().height(260.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
        ) {
            CameraPreviewSurface(
                permissionGranted = cameraGranted,
                modifier = Modifier.fillMaxSize(),
                onStateChanged = { previewState = it },
            )
        }
        Spacer(Modifier.height(16.dp))
        ReadinessRow(
            stringResource(R.string.camera_preview_label),
            previewState is CameraPreviewState.Streaming,
            previewDetail(previewState),
        )
        ReadinessRow(
            stringResource(R.string.tripod_label),
            false,
            stringResource(R.string.tripod_detail),
        )
        ReadinessRow(
            stringResource(R.string.solar_filter_label),
            false,
            stringResource(R.string.solar_filter_detail),
        )
        ReadinessRow(
            stringResource(R.string.scientific_model_label),
            true,
            stringResource(R.string.scientific_model_detail),
        )
        Spacer(Modifier.height(20.dp))
        if (!cameraGranted) {
            Button(
                onClick = { cameraPermission.launch(Manifest.permission.CAMERA) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("camera-primary-action"),
            ) { Text(stringResource(R.string.enable_camera)) }
        } else {
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("camera-primary-action"),
            ) {
                Text(stringResource(R.string.automatic_capture_not_armed))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.solar_safety_warning),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFCA5A5),
        )
    }
}

@Composable
private fun previewTitle(state: CameraPreviewState): String = when (state) {
    CameraPreviewState.WaitingForPermission -> stringResource(R.string.camera_access_needed)
    CameraPreviewState.Starting -> stringResource(R.string.camera_starting)
    is CameraPreviewState.Streaming -> stringResource(R.string.camera_preview_ready)
    is CameraPreviewState.Unavailable -> stringResource(R.string.camera_unavailable)
}

@Composable
private fun previewMessage(state: CameraPreviewState): String = when (state) {
    CameraPreviewState.WaitingForPermission -> stringResource(R.string.camera_permission_message)
    CameraPreviewState.Starting -> stringResource(R.string.camera_starting_message)
    is CameraPreviewState.Streaming -> stringResource(R.string.camera_streaming_message)
    is CameraPreviewState.Unavailable -> state.reason
}

@Composable
private fun previewDetail(state: CameraPreviewState): String = when (state) {
    CameraPreviewState.WaitingForPermission -> stringResource(R.string.camera_permission_required)
    CameraPreviewState.Starting -> stringResource(R.string.camera_binding)
    is CameraPreviewState.Streaming -> when (state.lens) {
        PreviewLens.BACK -> stringResource(R.string.camera_back_streaming)
        PreviewLens.FRONT -> stringResource(R.string.camera_front_fallback_streaming)
    }
    is CameraPreviewState.Unavailable -> state.reason
}

@Composable
private fun LiveScreen() {
    var nowEpochSeconds by remember { mutableStateOf(Instant.now().epochSecond) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            nowEpochSeconds = Instant.now().epochSecond
        }
    }
    val referenceEvent = Instant.parse("2026-08-12T17:46:00Z")
    val remaining = Duration.between(Instant.ofEpochSecond(nowEpochSeconds), referenceEvent)

    ScreenColumn(stringResource(R.string.screen_live_status)) {
        HeroCard(
            stringResource(R.string.reference_eclipse_date),
            if (remaining.isNegative) {
                stringResource(R.string.reference_event_passed)
            } else {
                formatDuration(remaining)
            },
            EclipseAccent,
            stringResource(R.string.status_reference_countdown),
        )
        Spacer(Modifier.height(16.dp))
        InfoCard(
            stringResource(R.string.local_circumstances_title),
            stringResource(R.string.local_circumstances_body),
        )
        Spacer(Modifier.height(12.dp))
        InfoCard(
            stringResource(R.string.no_network_required_title),
            stringResource(R.string.no_network_required_body),
        )
    }
}

@Composable
private fun PositionScreen() {
    val context = LocalContext.current
    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val locationRepository = remember(context) { AndroidLocationRepository(context.applicationContext) }
    val guidanceFlow = remember(locationGranted, locationRepository) {
        if (locationGranted) {
            ObserverGuidanceFlow(locationRepository).observe()
        } else {
            flowOf(ObserverGuidanceState.PermissionRequired)
        }
    }
    val guidanceState by guidanceFlow.collectAsState(
        initial = if (locationGranted) ObserverGuidanceState.Acquiring else ObserverGuidanceState.PermissionRequired,
    )
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locationGranted = it[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            it[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    val ready = guidanceState as? ObserverGuidanceState.Ready
    val scene = EclipseMapScene(
        overlay = EclipseMapScene2026.overlay,
        observer = ready?.location?.point?.let { GeoPoint(it.latitude, it.longitude) },
        observerAccuracyMeters = ready?.location?.accuracyMeters?.takeIf(Double::isFinite),
    )

    ScreenColumn(stringResource(R.string.screen_position)) {
        HeroCard(
            when (guidanceState) {
                is ObserverGuidanceState.Ready -> stringResource(R.string.location_ready)
                ObserverGuidanceState.Acquiring -> stringResource(R.string.location_acquiring_title)
                ObserverGuidanceState.Unavailable -> stringResource(R.string.location_unavailable_title)
                is ObserverGuidanceState.Error -> stringResource(R.string.location_error_title)
                ObserverGuidanceState.PermissionRequired -> stringResource(R.string.use_your_location)
            },
            when (guidanceState) {
                is ObserverGuidanceState.Ready -> stringResource(R.string.location_ready_message)
                ObserverGuidanceState.Acquiring -> stringResource(R.string.location_acquiring_body)
                ObserverGuidanceState.Unavailable -> stringResource(R.string.location_unavailable_body)
                is ObserverGuidanceState.Error -> (guidanceState as ObserverGuidanceState.Error).message
                ObserverGuidanceState.PermissionRequired -> stringResource(R.string.location_permission_message)
            },
            if (guidanceState is ObserverGuidanceState.Ready) EclipseReady else EclipseWarning,
            if (guidanceState is ObserverGuidanceState.Ready) {
                stringResource(R.string.status_ready)
            } else {
                stringResource(R.string.status_action_required)
            },
        )
        Spacer(Modifier.height(16.dp))
        if (!locationGranted) {
            Button(
                onClick = {
                    locationPermission.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("position-primary-action"),
            ) { Text(stringResource(R.string.enable_location)) }
        }
        Spacer(Modifier.height(12.dp))
        ObserverEclipseMap(
            scene = scene,
            modifier = Modifier.fillMaxWidth().height(300.dp),
        )
        Spacer(Modifier.height(12.dp))
        when (val state = guidanceState) {
            is ObserverGuidanceState.Ready -> {
                val guidance = state.guidance
                val qualityWarnings = buildList {
                    add(
                        stringResource(
                            if (guidance.insideReferencePath) {
                                R.string.position_inside_path
                            } else {
                                R.string.position_outside_path
                            },
                        ),
                    )
                    if (state.stale) add(stringResource(R.string.position_location_stale))
                    if (state.lowAccuracy) add(stringResource(R.string.position_location_low_accuracy))
                }
                InfoCard(
                    stringResource(R.string.position_guidance_title),
                    stringResource(
                        R.string.position_guidance_format,
                        guidance.distanceKm,
                        guidance.bearingDegrees,
                        guidance.referenceDurationSeconds,
                        state.location.accuracyMeters,
                        guidance.boundaryUncertaintyKm,
                    ) + "\n" + qualityWarnings.joinToString(" "),
                )
            }
            ObserverGuidanceState.Acquiring -> InfoCard(
                stringResource(R.string.location_acquiring_title),
                stringResource(R.string.location_acquiring_body),
            )
            ObserverGuidanceState.Unavailable -> InfoCard(
                stringResource(R.string.location_unavailable_title),
                stringResource(R.string.location_unavailable_body),
            )
            is ObserverGuidanceState.Error -> InfoCard(
                stringResource(R.string.location_error_title),
                state.message,
            )
            ObserverGuidanceState.PermissionRequired -> InfoCard(
                stringResource(R.string.observer_map_title),
                stringResource(R.string.observer_map_body),
            )
        }
    }
}

@Composable
private fun GalleryScreen() {
    LocalGalleryScreen()
}

@Composable
private fun ScreenColumn(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge, color = EclipseAccent)
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )
        content()
    }
}

@Composable
private fun HeroCard(
    title: String,
    message: String,
    statusColor: Color,
    statusDescription: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hero-status")
            .semantics { stateDescription = statusDescription },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = EclipseCard),
    ) {
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(statusColor, CircleShape)
                        .clearAndSetSemantics {},
                )
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(message, Modifier.padding(top = 14.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ReadinessRow(label: String, ready: Boolean, detail: String) {
    val status = if (ready) {
        stringResource(R.string.status_ready)
    } else {
        stringResource(R.string.status_needs_attention)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics { stateDescription = status },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(if (ready) EclipseReady else EclipseWarning, CircleShape)
                .clearAndSetSemantics {},
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Color(0xFFCBD5E1))
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                body,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCBD5E1),
            )
        }
    }
}

@Composable
private fun formatDuration(duration: Duration): String {
    val days = duration.toDays()
    val hours = duration.minusDays(days).toHours()
    val minutes = duration.minusDays(days).minusHours(hours).toMinutes()
    val seconds = duration.minusDays(days).minusHours(hours).minusMinutes(minutes).seconds
    return stringResource(
        R.string.reference_countdown_format,
        days,
        hours,
        minutes,
        seconds,
    )
}
