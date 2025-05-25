package com.example.assignment3task2.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class GpxLogger(private val context: Context) {

    companion object {
        private const val TAG = "GpxLogger"
        private const val GPX_HEADER = """<?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="Assignment3Task2">
            """
        private const val GPX_FOOTER = "</gpx>"
    }

    private fun getDownloadsFolder(): File? {
        return context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    }

    fun createGpxFile(): File {
        val downloadsFolder = getDownloadsFolder()
        val gpxFile = File(downloadsFolder, "location_log.gpx")

        if (!gpxFile.exists()) {
            try {
                FileOutputStream(gpxFile).use { fos ->
                    fos.write(GPX_HEADER.toByteArray())
                    fos.flush()
                }
                Log.d(TAG, "GPX file created successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating GPX file", e)
            }
        }
        return gpxFile
    }

    fun logLocation(latitude: Double, longitude: Double, timestamp: Long) {
        val gpxFile = createGpxFile()

        val locationEntry = """
            <trk><name>Location Tracking</name><trkseg>
            <trkpt lat="$latitude" lon="$longitude">
            <time>${java.util.Date(timestamp)}</time>
            </trkpt>
            </trkseg></trk>
        """.trimIndent()

        try {
            FileOutputStream(gpxFile, true).use { fos ->
                fos.write(locationEntry.toByteArray())
                fos.flush()
            }
            Log.d(TAG, "Location logged: $latitude, $longitude at $timestamp")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging location to GPX file", e)
        }
    }

    fun finalizeGpxFile() {
        val gpxFile = createGpxFile()
        try {
            FileOutputStream(gpxFile, true).use { fos ->
                fos.write(GPX_FOOTER.toByteArray())
                fos.flush()
            }
            Log.d(TAG, "GPX file finalized.")
        } catch (e: Exception) {
            Log.e(TAG, "Error finalizing GPX file", e)
        }
    }
}