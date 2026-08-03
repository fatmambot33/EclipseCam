package com.fatmambo33.eclipsecam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                EclipseCamApp()
            }
        }
    }
}

@Composable
private fun EclipseCamApp() {
    Surface(
        modifier = Modifier.fillMaxSize().background(Color(0xFF070A12)),
        color = Color(0xFF070A12),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("EclipseCam", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Phone-first eclipse planning and automatic capture",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Cloud build bootstrap",
                modifier = Modifier.padding(top = 24.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
