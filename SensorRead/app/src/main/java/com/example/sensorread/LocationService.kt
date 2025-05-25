package com.example.sensorread

import android.content.Context
import android.location.Location
import android.location.LocationManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LocationService(private val context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location
    
    private var updateJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    fun startPeriodicUpdates(periodMillis: Long) {
        updateJob?.cancel()
        
        updateJob = coroutineScope.launch {
            while (isActive) {
                try {
                    _location.value = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
                delay(periodMillis)
            }
        }
    }

}