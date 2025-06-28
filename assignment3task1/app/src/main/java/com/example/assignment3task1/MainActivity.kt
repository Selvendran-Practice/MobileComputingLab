package com.example.assignment3task1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.assignment3task1.ui.theme.Assignment3task1Theme

class MainActivity : ComponentActivity() {

    private lateinit var beaconScanner: BeaconScanner
    private val viewModel = BeaconViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        beaconScanner = BeaconScanner(this, viewModel)

        enableEdgeToEdge()

        setContent {
            Assignment3task1Theme {
                BeaconScreen(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        beaconScanner.startScan()
    }

    override fun onPause() {
        super.onPause()
        beaconScanner.stopScan()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        beaconScanner.handlePermissionsResult(requestCode, grantResults)
    }
}
