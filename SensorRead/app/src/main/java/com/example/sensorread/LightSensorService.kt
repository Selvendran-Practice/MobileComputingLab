package com.example.sensorread

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LightSensorService(private val context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    
    private val _lightLevel = MutableStateFlow<Float?>(null)
    val lightLevel: StateFlow<Float?> = _lightLevel

    private var threshold: Float? = null
    private var hasNotifiedThreshold = false
    private var lastUpdateTime = 0L
    private var updateJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private var currentLightValue: Float? = null

    fun setThreshold(value: Float?) {
        threshold = value
        hasNotifiedThreshold = false
    }

    fun startPeriodicUpdates(period: Long) {
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        lastUpdateTime = System.currentTimeMillis()
        
        // Start periodic UI updates
        updateJob?.cancel()
        updateJob = coroutineScope.launch {
            while (isActive) {
                delay(period) // Use the provided period instead of hardcoded 5000
                currentLightValue?.let { value ->
                    _lightLevel.value = value
                }
            }
        }
    }

    fun stopPeriodicUpdates() {
        sensorManager.unregisterListener(this)
        updateJob?.cancel()
        updateJob = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_LIGHT) {
            val newValue = event.values[0]
            currentLightValue = newValue

            // Check threshold
            threshold?.let { thresholdValue ->
                if (!hasNotifiedThreshold && newValue >= thresholdValue) {
                    hasNotifiedThreshold = true
                    android.widget.Toast.makeText(
                        context,
                        "Light level (${newValue.format(2)} lux) has reached threshold (${thresholdValue.format(2)} lux)!",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else if (newValue < thresholdValue) {
                    hasNotifiedThreshold = false
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun Float.format(digits: Int) = "%.${digits}f".format(this)
} 