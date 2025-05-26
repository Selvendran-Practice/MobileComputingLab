package com.example.assignment3task2.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.assignment3task2.utils.GpxLogger
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*

class LocationManager(private val context: Context) {

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 100
        private const val REQUEST_CHECK_SETTINGS = 123

        private const val MIN_DISTANCE_THRESHOLD = 2.0f
        private const val MIN_ACCURACY_THRESHOLD = 10.0f
        private const val TAG = "LocationManager"
    }

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private lateinit var locationCallback: LocationCallback
    private var isTracking = false
    private var previousLocation: Location? = null
    private var trackingStartTime: Long = 7000L

    val locationTextState = mutableStateOf("No location yet")
    val distanceTravelledState = mutableStateOf(0f)
    val averageSpeedState = mutableStateOf(0.0)

    // GPX File Logger Instance
    private val gpxLogger = GpxLogger(context)


    fun startLocationTracking() {


        if (!hasPermissions()) {
            Toast.makeText(context, "Permissions not granted", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isTracking) {
            isTracking = true
            previousLocation = null
            distanceTravelledState.value = 0f
            averageSpeedState.value = 0.0
            trackingStartTime = System.currentTimeMillis()

            val gpxFile = gpxLogger.createGpxFile()
            if (gpxFile.exists()) {
                gpxFile.delete()
                Log.d(TAG, "Deleted old GPX file before starting new tracking.")
            }

            // Create GPX file when tracking starts
            gpxLogger.createGpxFile()

            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation
                    if (location != null) {
                        Log.d(TAG, "Location: ${location.latitude}, ${location.longitude}, Acc: ${location.accuracy}")

                        if (location.accuracy <= MIN_ACCURACY_THRESHOLD) {
                            if (previousLocation != null) {
                                val deltaDistance = previousLocation!!.distanceTo(location)
                                if (deltaDistance > MIN_DISTANCE_THRESHOLD) {
                                    distanceTravelledState.value += deltaDistance
                                    previousLocation = location

                                    val elapsedTimeSeconds = (System.currentTimeMillis() - trackingStartTime) / 1000.0
                                    if (elapsedTimeSeconds > 0) {
                                        averageSpeedState.value = distanceTravelledState.value / elapsedTimeSeconds
                                    }
                                }
                            } else {
                                previousLocation = location
                            }

                            // Log location to GPX file
                            gpxLogger.logLocation(location.latitude, location.longitude, System.currentTimeMillis())
                        }

                        locationTextState.value =
                            "Lat: ${location.latitude}, Lon: ${location.longitude}\n" +
                                    "Accuracy: ${location.accuracy} m\n" +
                                    "Instantaneous Speed: ${"%.2f".format(location.speed)} m/s\n" +
                                    "Distance: ${"%.2f".format(distanceTravelledState.value)} m\n" +
                                    "Avg Speed: ${"%.2f".format(averageSpeedState.value)} m/s"
                    } else {
                        locationTextState.value = "Location not available."
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback, context.mainLooper
            )
            Toast.makeText(context, "Started location tracking", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopLocationTracking() {
        if (isTracking) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            isTracking = false

            // Finalize GPX file when tracking stops
            gpxLogger.finalizeGpxFile()

            locationTextState.value =
                "Tracking stopped.\nTotal Distance: ${"%.2f".format(distanceTravelledState.value)} m\n" +
                        "Average Speed: ${"%.2f".format(averageSpeedState.value)} m/s"
            Toast.makeText(context, "Stopped location tracking", Toast.LENGTH_SHORT).show()
        }
    }

    fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestLocationPermission(activity: Activity) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        ActivityCompat.requestPermissions(activity, permissions.toTypedArray(), REQUEST_LOCATION_PERMISSION)
    }

    fun checkLocationSettings(activity: Activity) {
        val locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).build()
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(activity)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            Toast.makeText(activity, "GPS is enabled.", Toast.LENGTH_SHORT).show()
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    exception.startResolutionForResult(activity, REQUEST_CHECK_SETTINGS)
                } catch (sendEx: IntentSender.SendIntentException) {
                    Toast.makeText(activity, "Error enabling GPS.", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(activity, "GPS is required for this app.", Toast.LENGTH_LONG).show()
            }
        }
    }
}