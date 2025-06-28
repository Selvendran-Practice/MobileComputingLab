package com.example.assignment5

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler


@Composable
fun AppContent(writer: TemperatureWriter) {
    var currentScreen by remember { mutableStateOf("menu") }

    // ⬅️ Intercept system back press
    BackHandler(enabled = currentScreen != "menu") {
        currentScreen = "menu"
    }

    Column(modifier = Modifier.padding(24.dp)) {
        when (currentScreen) {
            "menu" -> {
                Text("Assignment 5 Demo", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { currentScreen = "temperature" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Fetch Temperature")
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { currentScreen = "teams" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Read Teams")
                }
            }

            "temperature" -> {
                CityTemperatureScreen(writer)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { currentScreen = "menu" }) {
                    Text("← Back to Menu")
                }
            }

            "teams" -> {
                TeamsScreen()
                Spacer(Modifier.height(12.dp))
                Button(onClick = { currentScreen = "menu" }) {
                    Text("← Back to Menu")
                }
            }
        }
    }
}
