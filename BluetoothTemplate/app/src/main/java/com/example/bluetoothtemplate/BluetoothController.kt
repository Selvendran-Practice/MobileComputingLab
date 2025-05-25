package com.example.bluetoothtemplate

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A utility class to manage Bluetooth operations
 */
class BluetoothController(private val context: Context) {
    private val TAG = "BluetoothController"

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager?.adapter
    }

    private val bluetoothLeScanner: BluetoothLeScanner? by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    // State for discovered devices
    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDevice>> = _scannedDevices.asStateFlow()

    // State for scanning status
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    // Track if BLE scanning is active
    private var bleScanning = false

    // Handler for stopping BLE scan after a period
    private val handler = Handler(Looper.getMainLooper())

    // BroadcastReceiver for Classic Bluetooth device discovery
    private val bluetoothDiscoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        // Get the BluetoothDevice object from the Intent
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(
                                BluetoothDevice.EXTRA_DEVICE,
                                BluetoothDevice::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        
                        device?.let { bluetoothDevice ->
                            try {
                                // Add device to our list
                                addDeviceToList(bluetoothDevice)
                                
                                // Get RSSI if available
                                val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                                val rssiStr = if (rssi != Short.MIN_VALUE) "${rssi}dBm" else "N/A"
                                
                                // Log detailed device info
                                Log.d(TAG, "Classic device found: " +
                                      "Name=${bluetoothDevice.name ?: "Unknown"}, " +
                                      "Address=${bluetoothDevice.address}, " +
                                      "RSSI=$rssiStr, " +
                                      "Type=${getDeviceTypeName(bluetoothDevice)}")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing Classic device: ${e.message}")
                                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
                            }
                        } ?: Log.e(TAG, "Received ACTION_FOUND but device was null")
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                        _isScanning.value = true
                        Log.d(TAG, "Classic Bluetooth discovery started")
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.d(TAG, "Classic Bluetooth discovery finished, found ${_scannedDevices.value.size} devices, BLE scanning: $bleScanning")
                        
                        // Only update UI state if BLE is also not running
                        if (!bleScanning) {
                            Log.d(TAG, "Both Classic and BLE scanning finished, updating isScanning state")
                            _isScanning.value = false
                        } else {
                            Log.d(TAG, "Classic discovery finished but BLE still running, keeping isScanning=true")
                        }
                        
                        // If we have no devices and BLE isn't running, try to restart discovery
                        if (_scannedDevices.value.isEmpty() && !bleScanning && _isScanning.value) {
                            Log.d(TAG, "No devices found and BLE not running, restarting Classic discovery")
                            try {
                                bluetoothAdapter?.startDiscovery()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error restarting discovery: ${e.message}")
                            }
                        }
                    }
                    else -> {
                        Log.d(TAG, "Received unhandled Bluetooth action: ${intent.action}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Bluetooth broadcast receiver: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun getDeviceTypeName(device: BluetoothDevice): String {
        return try {
            when (device.type) {
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
                BluetoothDevice.DEVICE_TYPE_LE -> "LE"
                BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual"
                BluetoothDevice.DEVICE_TYPE_UNKNOWN -> "Unknown"
                else -> "Other"
            }
        } catch (e: Exception) {
            "Error"
        }
    }

    // Callback for BLE device scanning
    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // Log the callback type
            val callbackTypeStr = when(callbackType) {
                ScanSettings.CALLBACK_TYPE_ALL_MATCHES -> "ALL_MATCHES"
                ScanSettings.CALLBACK_TYPE_FIRST_MATCH -> "FIRST_MATCH"
                ScanSettings.CALLBACK_TYPE_MATCH_LOST -> "MATCH_LOST"
                else -> "UNKNOWN($callbackType)"
            }
            
            val device = result.device
            try {
                // Add device to our list
                addDeviceToList(device)
                
                // Log detailed device info
                Log.d(TAG, "BLE device found (callback=$callbackTypeStr): " +
                      "Name=${device.name ?: "Unknown"}, " +
                      "Address=${device.address}, " +
                      "RSSI=${result.rssi}dBm, " +
                      "Type=${getDeviceTypeName(device)}")
                
                // If we're getting results, we're definitely scanning
                if (!bleScanning) {
                    Log.d(TAG, "Received scan result while bleScanning=false, correcting state")
                    bleScanning = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing BLE device: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            Log.d(TAG, "BLE batch results received: ${results.size} devices")
            
            try {
                for (result in results) {
                    val device = result.device
                    addDeviceToList(device)
                    Log.d(TAG, "BLE batch device: Name=${device.name ?: "Unknown"}, " +
                          "Address=${device.address}, RSSI=${result.rssi}dBm")
                }
                
                // If we're getting results, we're definitely scanning
                if (!bleScanning) {
                    Log.d(TAG, "Received batch results while bleScanning=false, correcting state")
                    bleScanning = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing BLE batch results: ${e.message}")
                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            val errorMessage = when(errorCode) {
                ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed (power optimizations?)"
                ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE not supported on this device"
                ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
                else -> "Unknown error code: $errorCode"
            }
            
            Log.e(TAG, "⚠️ BLE scan failed: $errorMessage")
            
            // Update our state
            bleScanning = false
            
            // For certain errors, we should update the UI immediately
            if (errorCode == ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED) {
                Log.d(TAG, "BLE not supported, updating isScanning state")
                _isScanning.value = false
            } else {
                // For other errors, we'll let Classic Bluetooth continue if it's running
                Log.d(TAG, "BLE scan failed, but keeping isScanning state for classic discovery")
            }
        }
    }

    // We'll register the receiver in the startScan method instead of init
    private val filter = IntentFilter().apply {
        addAction(BluetoothDevice.ACTION_FOUND)
        addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
    }
    
    // Track if receiver is registered
    private var receiverRegistered = false

    // Helper function to add a device to the list without duplicates
    private fun addDeviceToList(device: BluetoothDevice) {
        _scannedDevices.update { devices ->
            if (devices.none { it.address == device.address }) {
                devices + device
            } else {
                devices
            }
        }
    }

    // Start scanning for both Classic Bluetooth and BLE devices
    @SuppressLint("MissingPermission")
    fun startScan() {
        Log.d(TAG, "startScan called")
        
        // Check if already scanning
        if (_isScanning.value) {
            Log.d(TAG, "Scan already in progress, ignoring startScan call")
            return
        }
        
        // Log device information
        Log.d(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        
        // Check permissions and Bluetooth state
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "Cannot start scan: Bluetooth permissions not granted")
            return
        }
        
        if (!isBluetoothEnabled()) {
            Log.e(TAG, "Cannot start scan: Bluetooth is not enabled")
            return
        }
        
        // Log Bluetooth adapter information
        bluetoothAdapter?.let { adapter ->
            Log.d(TAG, "Bluetooth adapter: ${adapter.name}, address: ${adapter.address}")
            Log.d(TAG, "Bluetooth state: ${getBluetoothStateName(adapter.state)}")
            Log.d(TAG, "Bluetooth LE supported: ${adapter.isLe2MPhySupported}")
        } ?: Log.e(TAG, "Bluetooth adapter is null")

        // Clear previous results
        _scannedDevices.value = emptyList()
        
        // Register receiver if not already registered
        if (!receiverRegistered) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(
                        bluetoothDiscoveryReceiver, 
                        filter, 
                        Context.RECEIVER_NOT_EXPORTED
                    )
                } else {
                    context.registerReceiver(bluetoothDiscoveryReceiver, filter)
                }
                receiverRegistered = true
                Log.d(TAG, "Bluetooth discovery receiver registered")
            } catch (e: Exception) {
                Log.e(TAG, "Error registering Bluetooth discovery receiver: ${e.message}")
            }
        }
        
        // Cancel any ongoing discovery
        try {
            bluetoothAdapter?.cancelDiscovery()
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling discovery: ${e.message}")
        }
        
        // Update scanning state
        _isScanning.value = true
        
        // Start Classic Bluetooth discovery
        try {
            val discoveryStarted = bluetoothAdapter?.startDiscovery() ?: false
            Log.d(TAG, "Started Classic Bluetooth discovery: $discoveryStarted")
            
            // If discovery didn't start, update the scanning state
            if (!discoveryStarted) {
                Log.e(TAG, "Failed to start discovery")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Classic Bluetooth discovery: ${e.message}")
        }
        
        // Start BLE scanning
        try {
            // Get a fresh reference to the BLE scanner
            val scanner = bluetoothAdapter?.bluetoothLeScanner
            
            if (scanner == null) {
                Log.e(TAG, "BLE Scanner is null - BLE might not be supported or Bluetooth might not be fully initialized")
            } else {
                // Use empty list for scan filters to maximize device discovery
                val scanSettings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // Use highest power/highest discovery mode
                    .build()
                
                // Start the scan on the main thread
                try {
                    scanner.startScan(emptyList(), scanSettings, bleScanCallback)
                    bleScanning = true
                    Log.d(TAG, "Started BLE scanning with LOW_LATENCY mode and no filters")
                    
                    // Stop BLE scan after SCAN_PERIOD to save battery
                    handler.postDelayed({
                        Log.d(TAG, "BLE scan timeout reached (${SCAN_PERIOD/1000} seconds)")
                        stopBleScan()
                    }, SCAN_PERIOD)
                } catch (innerEx: Exception) {
                    Log.e(TAG, "Error in startScan call: ${innerEx.message}")
                    Log.e(TAG, "Stack trace: ${innerEx.stackTraceToString()}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing BLE scan: ${e.message}")
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
        }
    }

    // Stop scanning for Bluetooth devices
    @SuppressLint("MissingPermission")
    fun stopScan() {
        Log.d(TAG, "stopScan called")
        
        // First, update scanning state to ensure UI updates immediately
        _isScanning.value = false
        
        // Cancel any pending scan stop callbacks
        handler.removeCallbacksAndMessages(null)
        
        // Stop Classic Bluetooth discovery
        try {
            val discoveryCancelled = bluetoothAdapter?.cancelDiscovery() ?: false
            Log.d(TAG, "Stopped Classic Bluetooth discovery: $discoveryCancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Classic Bluetooth discovery: ${e.message}")
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
        }
        
        // Stop BLE scanning
        stopBleScan()
        
        // Log the final state
        Log.d(TAG, "Scan stopped, isScanning=${_isScanning.value}, bleScanning=$bleScanning")
        
        // We'll keep the receiver registered until release() is called
    }
    
    // Helper method to stop BLE scanning
    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        // Only attempt to stop if we believe we're scanning
        if (!bleScanning) {
            Log.d(TAG, "BLE scan not running, skipping stopBleScan")
            return
        }
        
        try {
            // Get a fresh reference to the scanner
            val scanner = bluetoothAdapter?.bluetoothLeScanner
            
            if (scanner == null) {
                Log.e(TAG, "BLE Scanner is null when trying to stop scan")
                bleScanning = false
            } else {
                try {
                    // Stop the scan on the main thread
                    scanner.stopScan(bleScanCallback)
                    Log.d(TAG, "Stopped BLE scanning successfully")
                } catch (innerEx: Exception) {
                    Log.e(TAG, "Error in stopScan call: ${innerEx.message}")
                    Log.e(TAG, "Stack trace: ${innerEx.stackTraceToString()}")
                } finally {
                    // Always update the state
                    bleScanning = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing to stop BLE scan: ${e.message}")
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            bleScanning = false
        }
    }

    // Clean up resources
    fun release() {
        Log.d(TAG, "Releasing Bluetooth controller resources")
        
        // First stop any ongoing scans
        stopScan()
        
        // Remove any pending callbacks
        handler.removeCallbacksAndMessages(null)
        
        // Unregister the receiver if it's registered
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(bluetoothDiscoveryReceiver)
                receiverRegistered = false
                Log.d(TAG, "Bluetooth discovery receiver unregistered")
            } catch (e: IllegalArgumentException) {
                // Receiver not registered
                Log.e(TAG, "Error unregistering receiver: ${e.message}")
            }
        }
    }

    // Check if Bluetooth is supported on the device
    fun isBluetoothSupported(): Boolean {
        return bluetoothAdapter != null
    }

    // Check if Bluetooth is enabled
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    // Check if we have the necessary Bluetooth permissions
    fun hasBluetoothPermissions(): Boolean {
        val hasPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+)
            val hasScan = hasPermission(Manifest.permission.BLUETOOTH_SCAN)
            val hasConnect = hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
            
            Log.d(TAG, "Android 12+ permissions: BLUETOOTH_SCAN=$hasScan, BLUETOOTH_CONNECT=$hasConnect")
            hasScan && hasConnect
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-11 (API 23-30)
            val hasBluetooth = hasPermission(Manifest.permission.BLUETOOTH)
            val hasBluetoothAdmin = hasPermission(Manifest.permission.BLUETOOTH_ADMIN)
            val hasLocation = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            
            Log.d(TAG, "Android 6-11 permissions: BLUETOOTH=$hasBluetooth, " +
                  "BLUETOOTH_ADMIN=$hasBluetoothAdmin, ACCESS_FINE_LOCATION=$hasLocation")
            hasBluetooth && hasBluetoothAdmin && hasLocation
        } else {
            // Android 5.1 and below (API 22-)
            val hasBluetooth = hasPermission(Manifest.permission.BLUETOOTH)
            val hasBluetoothAdmin = hasPermission(Manifest.permission.BLUETOOTH_ADMIN)
            
            Log.d(TAG, "Android 5.1- permissions: BLUETOOTH=$hasBluetooth, BLUETOOTH_ADMIN=$hasBluetoothAdmin")
            hasBluetooth && hasBluetoothAdmin
        }
        
        Log.d(TAG, "Has all required Bluetooth permissions: $hasPermissions")
        return hasPermissions
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun getBluetoothStateName(state: Int): String {
        return when (state) {
            BluetoothAdapter.STATE_OFF -> "OFF"
            BluetoothAdapter.STATE_TURNING_ON -> "TURNING_ON"
            BluetoothAdapter.STATE_ON -> "ON"
            BluetoothAdapter.STATE_TURNING_OFF -> "TURNING_OFF"
            else -> "UNKNOWN"
        }
    }

    // Get intent to enable Bluetooth
    fun getEnableBluetoothIntent(): Intent {
        return Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    }
    
    companion object {
        // Scan period for BLE scanning (60 seconds)
        private const val SCAN_PERIOD: Long = 60000
    }
}