package com.example.assignment3task1

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BeaconScreen(viewModel: BeaconViewModel) {
    val data by viewModel.beaconData
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Text(
            text = data,
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        )
    }
}
