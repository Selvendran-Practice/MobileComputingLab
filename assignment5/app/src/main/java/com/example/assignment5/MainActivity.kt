package com.example.assignment5

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import com.example.assignment5.ui.theme.Assignment5Theme
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme


class MainActivity : ComponentActivity() {
    private lateinit var firebaseWriter: TemperatureWriter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1️⃣ Provide the real Firebase-backed implementation
        firebaseWriter = object : TemperatureWriter {
            override fun write(
                city: String,
                temp: Double,
                onDone: (Boolean) -> Unit
            ) {
                // Normalize city & build date/timestamp
                val normalizedCity = city.trim().replaceFirstChar { it.uppercase() }
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(Date())
                val timestamp = System.currentTimeMillis().toString()

                // Write to /location/{city}/{date}/{timestamp}
                FirebaseDatabase.getInstance()
                    .getReference("location")
                    .child(normalizedCity)
                    .child(date)
                    .child(timestamp)
                    .setValue(temp)
                    .addOnSuccessListener {
                        Log.d(
                            "TEMP_WRITE",
                            "✅ Wrote $temp°C to /location/$normalizedCity/$date/$timestamp"
                        )
                        onDone(true)
                    }
                    .addOnFailureListener { err ->
                        Log.e("TEMP_WRITE", "❌ Failed to write temperature", err)
                        onDone(false)
                    }
            }
        }

        // 2️⃣ Launch your Compose UI, passing in the writer
        setContent {
            Assignment5Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(writer = firebaseWriter)
                }
            }
        }
    }
}
