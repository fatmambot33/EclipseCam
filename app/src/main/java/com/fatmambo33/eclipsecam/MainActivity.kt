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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatmambo33.eclipsecam.camera.preview.CameraPreview
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

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
            ) { EclipseCamApp() }
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
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
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
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var previewError by remember { mutableStateOf<String?>(null) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraGranted = it
        previewError = null
    }

    ScreenColumn("Prepare your phone") {
        HeroCard(
            if (cameraGranted) "Live camera ready" else "Camera access needed",
            when {
                !cameraGranted -> "EclipseCam needs camera access for alignment guidance and automatic capture."
                previewError != null -> "The camera could not start: $previewError"
                else -> "Mount the phone securely and confirm the complete eclipse trajectory stays in frame."
            },
            if (cameraGranted && previewError == null) EclipseReady else EclipseWarning,
        )
        Spacer(Modifier.height(16.dp))
        if (cameraGranted) {
            CameraPreview(onError = { previewError = it.message ?: it::class.java.simpleName })
            Spacer(Modifier.height(16.dp))
        }
        ReadinessRow("Camera", cameraGranted && previewError == null, if (cameraGranted) "CameraX preview" else "Permission required")
        ReadinessRow("Tripod", false, "Confirm before arming")
        ReadinessRow("Solar filter", false, "Required during partial phases")
        ReadinessRow("Scientific model", true, "Local solver available")
        Spacer(Modifier.height(20.dp))
        if (!cameraGranted) {
            Button(
                onClick = { cameraPermission.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Enable camera") }
        } else {
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("Alignment overlay is next")
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

@Composable
private fun LiveScreen() {
    var nowEpochSeconds by remember { mutableStateOf(Instant.now().epochSecond) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            nowEpochSeconds = Instant.now().epochSecond
        }
    }
    val referenceEvent = Instant.parse("2026-08-12T17:45:51Z")
    val remaining = Duration.between(Instant.ofEpochSecond(nowEpochSeconds), referenceEvent)

    ScreenColumn("Live eclipse status") {
        HeroCard(
            "12 August 2026",
            if (remaining.isNegative) "Reference event time has passed." else formatDuration(remaining),
            EclipseAccent,
        )
        Spacer(Modifier.height(16.dp))
        InfoCard(
            "Local circumstances",
            "Contact times, magnitude, obscuration, Sun altitude, totality duration, and uncertainty are computed locally from observer coordinates.",
        )
        Spacer(Modifier.height(12.dp))
        InfoCard(
            "No network required",
            "Astronomy, countdowns, sensors, capture planning, and media remain on the phone.",
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
                "EclipseCam can calculate your relationship to the path locally."
            } else {
                "Allow location so EclipseCam can tell you where to stand and how much totality you can gain by moving."
            },
            if (locationGranted) EclipseReady else EclipseWarning,
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
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Enable location") }
        }
        Spacer(Modifier.height(12.dp))
        InfoCard(
            "Observer-centred map",
            "The map prioritises you, the bold eclipse centreline, path limits, GPS uncertainty, and the best nearby position.",
        )
    }
}

@Composable
private fun GalleryScreen() {
    ScreenColumn("Your eclipse sessions") {
        HeroCard(
            "Nothing captured yet",
            "Photos, timelapses, montages, and capture reports stay on this phone until you explicitly share them.",
            Color(0xFF60A5FA),
        )
        Spacer(Modifier.height(16.dp))
        InfoCard(
            "Privacy by default",
            "No account, automatic upload, advertising, or behavioural analytics.",
        )
    }
}

@Composable
private fun ScreenColumn(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp),
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
private fun HeroCard(title: String, message: String, statusColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = EclipseCard),
    ) {
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(statusColor, CircleShape))
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(if (ready) EclipseReady else EclipseWarning, CircleShape))
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
