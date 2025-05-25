package com.example.assignment3task2.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.assignment3task2.ui.theme.Assignment3Task2Theme

@Composable
fun LocationTrackingScreen(
    locationText: String,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onStartTracking) {
            Text("Start Tracking")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onStopTracking) {
            Text("Stop Tracking")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            elevation = CardDefaults.elevatedCardElevation(4.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = locationText,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LocationTrackingScreenPreview() {
    Assignment3Task2Theme {
        LocationTrackingScreen(
            locationText = "Lat: 0.0, Lon: 0.0\nDistance Travelled: 0 m",
            onStartTracking = {},
            onStopTracking = {}
        )
    }
}