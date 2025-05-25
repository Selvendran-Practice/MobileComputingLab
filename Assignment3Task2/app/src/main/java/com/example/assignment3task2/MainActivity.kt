package com.example.assignment3task2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.assignment3task2.location.LocationManager
import com.example.assignment3task2.ui.LocationTrackingScreen
import com.example.assignment3task2.ui.theme.Assignment3Task2Theme

class MainActivity : ComponentActivity() {

    private lateinit var locationManager: LocationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        locationManager = LocationManager(this)

        setContent {
            Assignment3Task2Theme {
                LocationTrackingScreen(
                    locationText = locationManager.locationTextState.value,
                    onStartTracking = { locationManager.startLocationTracking() },
                    onStopTracking = { locationManager.stopLocationTracking() }
                )
            }
        }
    }
}