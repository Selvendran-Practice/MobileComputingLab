package com.example.assignment5

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TeamsScreen() {
    var city by remember { mutableStateOf("") }
    var latestText by remember { mutableStateOf("Enter a city and tap Show") }
    var avgText by remember { mutableStateOf("") }

    // Hold the current DB ref + listener so we can remove it on city change
    var dbRef by remember { mutableStateOf<DatabaseReference?>(null) }
    var listener by remember { mutableStateOf<ValueEventListener?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            label = { Text("City name") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                // guard
                if (city.isBlank()) {
                    latestText = "Please enter a city name."
                    avgText = ""
                    return@Button
                }

                // detach old listener if any
                listener?.let { old ->
                    dbRef?.removeEventListener(old)
                }

                // normalize and point to /location/{City}/{today}
                val normalized = city.trim().replaceFirstChar { it.uppercase() }
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(Date())
                val ref = FirebaseDatabase.getInstance()
                    .getReference("location")
                    .child(normalized)
                    .child(today)

                dbRef = ref
                latestText = "Loading…"
                avgText = ""

                // attach new real-time listener
                val newListener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        var sum = 0.0
                        var count = 0
                        var latestTs = -1L
                        var latestTemp = 0.0

                        // iterate all timestamp children
                        snapshot.children.forEach { entry ->
                            val ts = entry.key?.toLongOrNull() ?: return@forEach
                            val t  = entry.getValue(Double::class.java) ?: return@forEach

                            sum += t
                            count++

                            if (ts > latestTs) {
                                latestTs = ts
                                latestTemp = t
                            }
                        }

                        if (count > 0) {
                            // format date+time for latest
                            val sdfDT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            val dt = sdfDT.format(Date(latestTs))
                            latestText = "Latest in $normalized: $latestTemp°C on $dt"

                            // compute average
                            val avg = sum / count
                            avgText = "Avg today in $normalized: ${"%.1f".format(avg)}°C"
                        } else {
                            latestText = "No entries for $normalized today"
                            avgText = ""
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("FIREBASE", "Realtime load failed", error.toException())
                        latestText = "Error loading data: ${error.message}"
                        avgText = ""
                    }
                }

                listener = newListener
                ref.addValueEventListener(newListener)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Show")
        }

        // display results
        Text(latestText, style = MaterialTheme.typography.bodyLarge)
        if (avgText.isNotEmpty()) {
            Text(avgText, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
