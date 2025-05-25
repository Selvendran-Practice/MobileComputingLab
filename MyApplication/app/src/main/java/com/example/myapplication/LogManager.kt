package com.example.myapplication

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility class to manage logging to both Logcat and a file
 */
object LogManager {
    private const val TAG = "LogManager"
    private var logFile: File? = null
    private var logFileName = ""
    
    /**
     * Initialize the log file
     */
    fun init(context: Context) {
        try {
            // Create a timestamp for the log file name
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            logFileName = "bluetooth_log_$timestamp.txt"
            
            // Create the log file in the app's files directory
            logFile = File(context.filesDir, logFileName)
            
            // Log the file path
            Log.i(TAG, "Log file created at: ${logFile?.absolutePath}")
            
            // Write header to the log file
            appendToFile("=== Bluetooth Log Started at $timestamp ===\n")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing log file: ${e.message}")
        }
    }
    
    /**
     * Log a message to both Logcat and the log file
     */
    fun log(tag: String, message: String, level: LogLevel = LogLevel.INFO) {
        // Log to Logcat
        when (level) {
            LogLevel.VERBOSE -> Log.v(tag, message)
            LogLevel.DEBUG -> Log.d(tag, message)
            LogLevel.INFO -> Log.i(tag, message)
            LogLevel.WARNING -> Log.w(tag, message)
            LogLevel.ERROR -> Log.e(tag, message)
        }
        
        // Log to file
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val logEntry = "[$timestamp] ${level.name}/$tag: $message\n"
        appendToFile(logEntry)
    }
    
    /**
     * Get the path to the log file
     */
    fun getLogFilePath(): String? {
        return logFile?.absolutePath
    }
    
    /**
     * Get the File object for the log file (for sharing)
     */
    fun getLogFile(): File? {
        return logFile
    }
    
    /**
     * Get the log file name
     */
    fun getLogFileName(): String {
        return logFileName
    }
    
    /**
     * Append text to the log file
     */
    private fun appendToFile(text: String) {
        logFile?.let { file ->
            try {
                FileOutputStream(file, true).use { fos ->
                    fos.write(text.toByteArray())
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error writing to log file: ${e.message}")
            }
        }
    }
    
    /**
     * Log levels
     */
    enum class LogLevel {
        VERBOSE, DEBUG, INFO, WARNING, ERROR
    }
}