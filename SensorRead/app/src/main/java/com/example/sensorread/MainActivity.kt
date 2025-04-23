package com.example.sensorread

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.sensorread.ui.theme.SensorReadTheme

class MainActivity : ComponentActivity() {
    private lateinit var locationService: LocationService
    private lateinit var lightSensorService: LightSensorService

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)) {
            startPeriodicUpdates()
        }
    }

    private fun startPeriodicUpdates() {
        // Will be called when period is set
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationService = LocationService(this)
        lightSensorService = LightSensorService(this)
        
        enableEdgeToEdge()
        setContent {
            SensorReadTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SensorScreen(
                        locationService = locationService,
                        lightSensorService = lightSensorService,
                        onUpdate = { period ->
                            locationService.startPeriodicUpdates(period)
                            lightSensorService.startPeriodicUpdates(period)
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
    }
}

@Composable
fun SensorScreen(
    locationService: LocationService,
    lightSensorService: LightSensorService,
    onUpdate: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val location by locationService.location.collectAsState()
    val lightLevel by lightSensorService.lightLevel.collectAsState()
    var thresholdText by remember { mutableStateOf("") }
    var periodText by remember { mutableStateOf("1000") }
    var thresholdError by remember { mutableStateOf<String?>(null) }
    var periodError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Update Period Section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Update Period",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = periodText,
                    onValueChange = { 
                        periodText = it
                        periodError = null
                    },
                    label = { Text("Update Period (milliseconds)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = periodError != null,
                    supportingText = periodError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        try {
                            val period = periodText.toLongOrNull()
                            if (period == null) {
                                periodError = "Please enter a valid number"
                            } else if (period < 100) {
                                periodError = "Period must be at least 100ms"
                            } else {
                                onUpdate(period)
                                periodError = null
                            }
                        } catch (e: NumberFormatException) {
                            periodError = "Please enter a valid number"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Update")
                }
            }
        }

        // GPS Section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "GPS Location",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (location != null) {
                        "Latitude: ${location?.latitude}\nLongitude: ${location?.longitude}"
                    } else {
                        "Waiting for location..."
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // Light Sensor Section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Ambient Light",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (lightLevel != null) {
                        "Light Level: ${lightLevel?.format(2)} lux"
                    } else {
                        "Waiting for light sensor..."
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = { 
                        thresholdText = it
                        thresholdError = null
                    },
                    label = { Text("Light Threshold (lux)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = thresholdError != null,
                    supportingText = thresholdError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        try {
                            val threshold = thresholdText.toFloatOrNull()
                            if (threshold == null) {
                                thresholdError = "Please enter a valid number"
                            } else if (threshold < 0) {
                                thresholdError = "Threshold cannot be negative"
                            } else {
                                lightSensorService.setThreshold(threshold)
                                thresholdError = null
                            }
                        } catch (e: NumberFormatException) {
                            thresholdError = "Please enter a valid number"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Set Threshold")
                }
            }
        }
    }
}

private fun Float.format(digits: Int) = "%.${digits}f".format(this)