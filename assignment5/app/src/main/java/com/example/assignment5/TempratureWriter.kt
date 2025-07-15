package com.example.assignment5

import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.time.LocalDate
import java.time.format.DateTimeFormatter

interface TemperatureWriter {

    fun write(city: String, temp: Double, onDone: (Boolean) -> Unit)
}

class FirebaseTemperatureWriter(
    private val rootRef: DatabaseReference = FirebaseDatabase
        .getInstance()
        .getReference(LOCATION_PATH),
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
) : TemperatureWriter {

    override fun write(city: String, temp: Double, onDone: (Boolean) -> Unit) {
        val normalizedCity = city
            .trim()
            .replaceFirstChar { it.uppercase() }

        val date = LocalDate.now()
            .format(dateFormatter)

        val timestamp = System.currentTimeMillis().toString()

        rootRef
            .child(normalizedCity)
            .child(date)
            .child(timestamp)
            .setValue(temp)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Wrote $temp°C to /$LOCATION_PATH/$normalizedCity/$date/$timestamp")
                onDone(true)
            }
            .addOnFailureListener { ex ->
                Log.e(TAG, "❌ Failed to write temperature", ex)
                onDone(false)
            }
    }

    companion object {
        private const val TAG = "TemperatureWriter"
        private const val LOCATION_PATH = "location"
    }
}