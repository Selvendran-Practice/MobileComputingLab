package com.example.assignment5

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

@Composable
fun CityTemperatureScreen(writer: TemperatureWriter) {
    var city by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("Enter a city and tap Fetch") }
    val scope = rememberCoroutineScope()

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
                if (city.isBlank()) {
                    resultText = "Please enter a city name."
                    return@Button
                }
                resultText = "Fetching..."
                scope.launch(Dispatchers.IO) {
                    try {
                        // --- Fetch from Weatherstack API ---
                        val encodedCity = URLEncoder.encode(city, "UTF-8")
                        val apiKey = "ab171cb7b467bee1f80d27b3b2a6b0c0"
                        val url = "https://api.weatherstack.com/current" +
                                "?access_key=$apiKey" +
                                "&query=$encodedCity"

                        val request = Request.Builder()
                            .url(url)
                            .get()
                            .build()

                        val client = OkHttpClient()
                        client.newCall(request).execute().use { resp ->
                            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                            val body = resp.body?.string() ?: throw Exception("Empty body")

                            // Parse out current.temperature
                            val temp = JSONObject(body)
                                .getJSONObject("current")
                                .getDouble("temperature")
                            Log.d("TEMP", "Parsed temperature: $temp")

                            // --- Back to Main thread to update UI & write to DB ---
                            launch(Dispatchers.Main) {
                                resultText = "Current temperature in $city: $temp °C"
                                writer.write(city, temp) { success ->
                                    resultText = if (success) {
                                        "✅ Fetched $temp°C for $city and stored in DB"
                                    } else {
                                        "⚠️ Fetched $temp°C for $city but failed to write to DB"
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("TEMP", "Exception during fetch", e)
                        launch(Dispatchers.Main) {
                            resultText = "Error: ${e.localizedMessage ?: "Unknown error"}"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Fetch Temperature")
        }

        Text(text = resultText, style = MaterialTheme.typography.bodyLarge)
    }
}