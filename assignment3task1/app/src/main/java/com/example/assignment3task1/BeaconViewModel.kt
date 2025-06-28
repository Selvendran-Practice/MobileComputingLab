package com.example.assignment3task1

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel

class BeaconViewModel : ViewModel() {
    private val _beaconData = mutableStateOf("Scanning...")
    val beaconData: State<String> = _beaconData

    fun updateData(newData: String) {
        _beaconData.value = newData
    }
}
