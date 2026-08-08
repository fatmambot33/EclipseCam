package com.fatmambo33.eclipsecam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatmambo33.eclipsecam.camera.preview.CameraPreviewState
import com.fatmambo33.eclipsecam.camera.preview.CameraPreviewSurface
import com.fatmambo33.eclipsecam.camera.preview.PreviewLens
import com.fatmambo33.eclipsecam.media.LocalGalleryScreen
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

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

private enum class AppTab(val label: String, val icon: ImageVector) {
    Camera("Camera", Icons.Default.CameraAlt),
    Live("Live", Icons.Default.NightsStay),
    Position("Position", Icons.Default.LocationOn),
    Gallery("Gallery", Icons.Default.Collections),
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
                        label = { Text(tab.label) },
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

    ScreenColumn("Prepare your phone") {
        HeroCard(
            previewTitle(previewState),
            previewMessage(previewState),
            if (previewState is CameraPreviewState.Streaming) EclipseReady else EclipseWarning,
            if (previewState is CameraPreviewState.Streaming) "Ready" else "Needs attention",
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
            "Camera preview",
            previewState is CameraPreviewState.Streaming,
            previewDetail(previewState),
        )
        ReadinessRow("Tripod", false, "Confirm before arming")
        ReadinessRow("Solar filter", false, "Required during partial phases")
        ReadinessRow("Scientific model", true, "Validated local circumstances available")
        Spacer(Modifier.height(20.dp))
        if (!cameraGranted) {
            Button(
                onClick = { cameraPermission.launch(Manifest.permission.CAMERA) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("camera-primary-action"),
            ) { Text("Enable camera") }
        } else {
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("camera-primary-action"),
            ) {
                Text("Automatic capture not armed")
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Never look directly at the Sun without certified eclipse eye protection. Use an appropriate solar filter on the phone camera during partial phases.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFCA5A5),
        )
    }
}

private fun previewTitle(state: CameraPreviewState): String = when (state) {
    CameraPreviewState.WaitingForPermission -> "Camera access needed"
    CameraPreviewState.Starting -> "Starting camera"
    is CameraPreviewState.Streaming -> "Live preview ready"
    is CameraPreviewState.Unavailable -> "Camera unavailable"
}

private fun previewMessage(state: CameraPreviewState): String = when (state) {
    CameraPreviewState.WaitingForPermission ->
        "EclipseCam needs camera access for local alignment guidance and capture."
    CameraPreviewState.Starting -> "Connecting the preview to this screen and lifecycle."
    is CameraPreviewState.Streaming ->
        "Mount the phone securely. The preview stops automatically when this screen leaves the lifecycle."
    is CameraPreviewState.Unavailable -> state.reason
}

private fun previewDetail(state: CameraPreviewState): String = when (state) {
    CameraPreviewState.WaitingForPermission -> "Permission required"
    CameraPreviewState.Starting -> "Binding CameraX"
    is CameraPreviewState.Streaming -> when (state.lens) {
        PreviewLens.BACK -> "Back camera streaming"
        PreviewLens.FRONT -> "Front-camera fallback streaming"
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

    ScreenColumn("Live eclipse status") {
        HeroCard(
            "12 August 2026",
            if (remaining.isNegative) {
                "Reference event time has passed. Local circumstances still require validated GPS-based calculation."
            } else {
                formatDuration(remaining)
            },
            EclipseAccent,
            "Reference countdown",
        )
        Spacer(Modifier.height(16.dp))
        InfoCard(
            "Local circumstances",
            "Exact contacts, magnitude, obscuration, Sun altitude, and totality duration will be calculated locally from GPS after the validated Besselian engine is integrated.",
        )
        Spacer(Modifier.height(12.dp))
        InfoCard(
            "No network required",
            "The astronomy engine, countdowns, sensor processing, capture plan, and media remain on the phone. Google Maps is only an optional online basemap.",
        )
    }
}

@Composable
private fun PositionScreen() {
    val context = LocalContext.current
    var locationGranted by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locationGranted = it[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            it[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    ScreenColumn("Position against the eclipse") {
        HeroCard(
            if (locationGranted) "Location ready" else "Use your location",
            if (locationGranted) {
                "EclipseCam can calculate your relationship to the path locally. The bold centreline and path limits will appear after scientific validation."
            } else {
                "Allow location so EclipseCam can tell you where to stand and how much totality you can gain by moving."
            },
            if (locationGranted) EclipseReady else EclipseWarning,
            if (locationGranted) "Ready" else "Action required",
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
            ) { Text("Enable location") }
        }
        Spacer(Modifier.height(12.dp))
        InfoCard(
            "Observer-centred map",
            "The finished map will prioritise you, the bold eclipse centreline, northern and southern limits, moving shadow, GPS uncertainty, and the best nearby position.",
        )
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
        Text("EclipseCam", style = MaterialTheme.typography.labelLarge, color = EclipseAccent)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics { stateDescription = if (ready) "Ready" else "Needs attention" },
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

private fun formatDuration(duration: Duration): String {
    val days = duration.toDays()
    val hours = duration.minusDays(days).toHours()
    val minutes = duration.minusDays(days).minusHours(hours).toMinutes()
    val seconds = duration.minusDays(days).minusHours(hours).minusMinutes(minutes).seconds
    return "Reference countdown\n${days}d ${hours}h ${minutes}m ${seconds}s"
}
