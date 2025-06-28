package com.example.assignment5

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

interface TemperatureWriter {
    /**
     * Write the temperature to Firebase under:
     * /location/{normalizedCity}/{yyyy-MM-dd}/{timestamp} = temp
     *
     * @param city Name of the city (user input)
     * @param temp Temperature in Celsius (from API)
     * @param onDone Callback: true = success, false = error
     */
    fun write(city: String, temp: Double, onDone: (Boolean) -> Unit)
}

class FirebaseTemperatureWriter : TemperatureWriter {
    override fun write(city: String, temp: Double, onDone: (Boolean) -> Unit) {
        try {
            val normalizedCity = city.trim().replaceFirstChar { it.uppercase() }
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val timestamp = System.currentTimeMillis().toString()

            val ref = FirebaseDatabase.getInstance()
                .getReference("location")
                .child(normalizedCity)
                .child(date)
                .child(timestamp)

            ref.setValue(temp)
                .addOnSuccessListener {
                    Log.d("TEMP_WRITE", "✅ Wrote $temp°C to /location/$normalizedCity/$date/$timestamp")
                    onDone(true)
                }
                .addOnFailureListener { ex ->
                    Log.e("TEMP_WRITE", "❌ Failed to write temperature", ex)
                    onDone(false)
                }
        } catch (e: Exception) {
            Log.e("TEMP_WRITE", "❌ Exception during write", e)
            onDone(false)
        }
    }
}
