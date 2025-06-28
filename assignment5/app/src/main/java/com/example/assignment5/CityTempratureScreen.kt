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
                        // --- Fetch from RapidAPI ---
                        val encodedCity = URLEncoder.encode(city, "UTF-8")
                        val url = "https://cities-temperature.p.rapidapi.com/weather/v1/current?location=$encodedCity"
                        val request = Request.Builder()
                            .url(url)
                            .get()
                            .addHeader("x-rapidapi-key", "2687136a58msh4dff6c4c2ae1c83p1813bbjsn1919f10665ff")
                            .addHeader("x-rapidapi-host", "cities-temperature.p.rapidapi.com")
                            .build()
                        val client = OkHttpClient()

                        client.newCall(request).execute().use { resp ->
                            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                            val body = resp.body?.string() ?: throw Exception("Empty body")
                            val temp = JSONObject(body).getDouble("temperature")
                            Log.d("TEMP", "Parsed temperature: $temp")

                            // --- Back to Main thread to update UI & write to DB ---
                            launch(Dispatchers.Main) {
                                resultText = "Current temperature in $city: $temp °C"

                                // Delegate the DB write; get callback for success/failure
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
