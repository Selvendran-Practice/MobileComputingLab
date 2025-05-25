package com.example.myapplication

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.io.File
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val bluetoothManager by lazy {
        getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter by lazy {
        bluetoothManager.adapter
    }
    private val bleScanner by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }
    
    // ViewModel will handle the device list and scanning state
    private lateinit var viewModel: ScanViewModel
    
    // Scan timeout (stop after 15 seconds)
    private val SCAN_PERIOD: Long = 15000
    
    // Handler for scan timeout
    private val handler = Handler(Looper.getMainLooper())

    // Activity result launcher for Bluetooth enable request
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            showToast("Bluetooth has been enabled")
            startBluetoothScan()
        } else {
            showToast("Bluetooth enabling was rejected")
        }
    }

    // Activity result launcher for Bluetooth permissions
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Log all permission results
        Log.d("BTPerms", "Permission results: ${permissions.entries.joinToString()}")
        
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.d("BTPerms", "All permissions granted, enabling Bluetooth")
            enableBluetooth()
        } else {
            val denied = permissions.filter { !it.value }.keys.joinToString()
            Log.e("BTPerms", "Some permissions denied: $denied")
            showToast("Bluetooth permissions are required for this app to function properly")
        }
    }
    
    // BLE Scan callback
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceAddress = device.address
            
            // Log device found with RSSI (signal strength)
            Log.i("BTScan", "onScanResult() called — device=${deviceAddress}, RSSI: ${result.rssi}")
            
            // Always add the device to the list, regardless of permission
            // We'll handle the name display in the UI
            if (viewModel.devices.none { it.address == deviceAddress }) {
                Log.d("BTScan", "Adding new BLE device to list: $deviceAddress")
                
                // Add to our list using the ViewModel
                viewModel.addDevice(device)
                Log.d("BTScan", "Current device list size: ${viewModel.devices.size}")
                
                // Try to get the name if we have permission
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val deviceName = device.name ?: "Unknown Device"
                    Log.d("BTScan", "Device name: $deviceName")
                    
                    // Show toast on UI thread
                    runOnUiThread {
                        showToast("Found BLE device: $deviceName")
                    }
                } else {
                    // Show toast with just the address
                    runOnUiThread {
                        showToast("Found BLE device: $deviceAddress")
                    }
                }
            } else {
                Log.d("BTScan", "BLE device already in list: $deviceAddress")
            }
        }
        
        override fun onBatchScanResults(results: List<ScanResult>) {
            Log.i("BTScan", "onBatchScanResults() called with ${results.size} results")
            
            for (result in results) {
                val device = result.device
                val deviceAddress = device.address
                
                // Add device if it's not already in the list
                if (viewModel.devices.none { it.address == deviceAddress }) {
                    Log.d("BTScan", "Adding batch BLE device: $deviceAddress")
                    
                    // Add to our list using the ViewModel
                    viewModel.addDevice(device)
                    Log.d("BTScan", "Current device list size: ${viewModel.devices.size}")
                    
                    runOnUiThread {
                        showToast("Found BLE device: $deviceAddress")
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BTScan", "onScanFailed($errorCode)")
            
            val errorMessage = when (errorCode) {
                ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
                ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE scanning not supported"
                ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
                else -> "Error code $errorCode"
            }
            
            runOnUiThread {
                showToast("BLE Scan failed: $errorMessage")
            }
            
            // Stop scan in ViewModel
            viewModel.stopScan()
        }
    }
    
    // Broadcast receiver for classic Bluetooth discovery
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("BTScan", "Received broadcast: ${intent.action}")
            
            when(intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Get the BluetoothDevice object from the Intent
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    
                    // Get RSSI if available
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    val rssiStr = if (rssi != Short.MIN_VALUE.toInt()) ", RSSI: $rssi" else ""
                    
                    device?.let {
                        val deviceAddress = it.address
                        Log.d("BTScan", "Found classic Bluetooth device: $deviceAddress$rssiStr")
                        
                        // Add device if it's not already in the list
                        if (viewModel.devices.none { d -> d.address == deviceAddress }) {
                            Log.d("BTScan", "Adding classic device to list: $deviceAddress")
                            
                            // Add to our list using the ViewModel
                            viewModel.addDevice(it)
                            Log.d("BTScan", "Current device list size: ${viewModel.devices.size}")
                            
                            // Try to get the name if we have permission
                            if (ActivityCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                val deviceName = it.name ?: "Unknown Device"
                                Log.d("BTScan", "Classic device name: $deviceName")
                                
                                // Show toast on UI thread
                                runOnUiThread {
                                    showToast("Found device: $deviceName")
                                }
                            } else {
                                // Show toast with just the address
                                runOnUiThread {
                                    showToast("Found device: $deviceAddress")
                                }
                            }
                        } else {
                            Log.d("BTScan", "Device already in list: $deviceAddress")
                        }
                    } ?: Log.e("BTScan", "Received null device in ACTION_FOUND")
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.d("BTScan", "Classic Bluetooth discovery started")
                    showToast("Classic Bluetooth discovery started")
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d("BTScan", "Classic Bluetooth discovery finished")
                    
                    // Don't stop scanning in ViewModel here as BLE scanning might still be active
                    // We'll handle this in stopBluetoothScan()
                    
                    showToast("Classic Bluetooth discovery finished")
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    when (state) {
                        BluetoothAdapter.STATE_OFF -> {
                            Log.d("BTScan", "Bluetooth turned off")
                            // If Bluetooth is turned off, stop scanning
                            if (viewModel.isScanning) {
                                stopBluetoothScan()
                            }
                        }
                        BluetoothAdapter.STATE_ON -> Log.d("BTScan", "Bluetooth turned on")
                        BluetoothAdapter.STATE_TURNING_OFF -> Log.d("BTScan", "Bluetooth turning off")
                        BluetoothAdapter.STATE_TURNING_ON -> Log.d("BTScan", "Bluetooth turning on")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize LogManager
        LogManager.init(this)
        
        // Create the ViewModel
        viewModel = ScanViewModel()
        
        // Register for broadcasts when a device is discovered
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        registerReceiver(bluetoothReceiver, filter)
        
        // Check Bluetooth permissions as soon as the app starts
        checkAndRequestBluetoothPermissions()
        
        setContent {
            MyApplicationTheme {
                // Get the ViewModel using the viewModel() composable function
                val vm: ScanViewModel = viewModel()
                
                // Store the reference for use in activity methods
                viewModel = vm
                
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BluetoothDevicesScreen(
                        modifier = Modifier.padding(innerPadding),
                        devices = vm.devices,
                        isScanning = vm.isScanning,
                        connectingDeviceAddress = vm.connectingDeviceAddress,
                        connectedDeviceAddress = vm.connectedDeviceAddress,
                        lastReceivedData = vm.lastReceivedData,
                        lastReceivedDataHex = vm.lastReceivedDataHex,
                        lastCharacteristicUuid = vm.lastCharacteristicUuid,
                        volumeLevel = vm.volumeLevel,
                        batteryLevel = vm.batteryLevel,
                        onScanClick = { 
                            if (vm.isScanning) {
                                stopBluetoothScan()
                            } else {
                                startBluetoothScan()
                            }
                        },
                        onDeviceClick = { device ->
                            // Check for BLUETOOTH_CONNECT permission
                            if (ActivityCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                // If this device is already connected, disconnect
                                if (device.address == vm.connectedDeviceAddress) {
                                    vm.disconnectFromDevice()
                                    showToast("Disconnected from ${device.name ?: device.address}")
                                } else {
                                    // Connect to the device
                                    vm.connectToDevice(this@MainActivity, device)
                                    
                                    // Show toast
                                    val deviceName = device.name ?: device.address
                                    showToast("Connecting to $deviceName...")
                                }
                            } else {
                                showToast("Bluetooth connect permission not granted")
                                Log.e("BTConnect", "Bluetooth connect permission not granted")
                            }
                        },
                        onRefreshClick = { device ->
                            // Check for BLUETOOTH_CONNECT permission
                            if (ActivityCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                // Refresh volume and battery levels
                                vm.queryVolumeLevel()
                                vm.queryBatteryLevel()
                                showToast("Refreshing device information...")
                            } else {
                                showToast("Bluetooth connect permission not granted")
                                Log.e("BTConnect", "Bluetooth connect permission not granted")
                            }
                        }
                    )
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Make sure to stop scanning when the activity is destroyed
        stopBluetoothScan()
        // Unregister the broadcast receiver
        unregisterReceiver(bluetoothReceiver)
    }

    private fun checkAndRequestBluetoothPermissions() {
        if (bluetoothAdapter == null) {
            showToast("This device doesn't support Bluetooth")
            Log.e("BTScan", "Device doesn't support Bluetooth")
            return
        }

        // Create a list of required permissions based on Android version
        val requiredPermissions = mutableListOf<String>()
        
        // Location permissions are required for BLE scanning on Android 6.0 - 11
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            requiredPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            Log.d("BTScan", "Adding location permissions for Android < 12")
        }
        
        // Bluetooth permissions for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            Log.d("BTScan", "Adding Bluetooth permissions for Android 12+")
        }
        
        // We don't need storage permissions anymore as we're using internal storage

        // Check which permissions are missing
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        // Log the current permission state
        requiredPermissions.forEach { permission ->
            val isGranted = ContextCompat.checkSelfPermission(this, permission) == 
                PackageManager.PERMISSION_GRANTED
            Log.d("BTPerms", "Permission $permission: ${if (isGranted) "GRANTED" else "DENIED"}")
        }

        if (missingPermissions.isNotEmpty()) {
            Log.d("BTScan", "Requesting missing permissions: ${missingPermissions.joinToString()}")
            requestPermissionLauncher.launch(missingPermissions)
            return
        }

        Log.d("BTScan", "All required permissions granted")
        // If we have all permissions, proceed to enable Bluetooth
        enableBluetooth()
    }

    private fun enableBluetooth() {
        if (bluetoothAdapter == null) {
            Log.e("BTScan", "Bluetooth adapter is null")
            showToast("Bluetooth is not available on this device")
            return
        }
        
        if (bluetoothAdapter?.isEnabled == false) {
            Log.d("BTScan", "Bluetooth is disabled, requesting to enable")
            // Request to enable Bluetooth
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                enableBluetoothLauncher.launch(enableBtIntent)
            } else {
                // This should not happen as we already checked permissions
                Log.e("BTScan", "BLUETOOTH_CONNECT permission missing")
                showToast("Bluetooth permission is required")
            }
        } else {
            Log.d("BTScan", "Bluetooth is already enabled")
            showToast("Bluetooth is already enabled")
            // Start scanning for devices
            startBluetoothScan()
        }
    }
    
    private fun startBluetoothScan() {
        // Start scan in ViewModel
        viewModel.startScan()
        
        // Check if Bluetooth is enabled
        if (bluetoothAdapter?.isEnabled != true) {
            Log.e("BTScan", "Bluetooth is OFF – cannot scan")
            showToast("Please enable Bluetooth")
            enableBluetooth() // Try to enable it
            return
        }
        
        // Check for scan permission
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showToast("Bluetooth scan permission not granted")
            Log.e("BTScan", "Bluetooth scan permission not granted")
            return
        }
        
        showToast("Started scanning for Bluetooth devices")
        Log.d("BTScan", "Started scanning for Bluetooth devices")
        
        // Set a timeout to stop scanning after SCAN_PERIOD
        handler.postDelayed({
            stopBluetoothScan()
        }, SCAN_PERIOD)
        
        // Get paired devices first
        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val pairedDevices = bluetoothAdapter?.bondedDevices
                Log.d("BTScan", "Found ${pairedDevices?.size ?: 0} paired devices")
                
                pairedDevices?.forEach { device ->
                    val deviceAddress = device.address
                    val deviceName = device.name ?: "Unknown Device"
                    
                    Log.d("BTScan", "Paired device: $deviceName ($deviceAddress)")
                    
                    // Add to our list if not already there
                    if (viewModel.devices.none { it.address == deviceAddress }) {
                        viewModel.addDevice(device)
                        Log.d("BTScan", "Added paired device to list: $deviceName")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BTScan", "Error getting paired devices: ${e.message}")
        }
        
        // Start classic Bluetooth discovery
        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                Log.d("BTScan", "Cancelling existing discovery")
                bluetoothAdapter?.cancelDiscovery()
            }
            
            val discoveryStarted = bluetoothAdapter?.startDiscovery() ?: false
            if (discoveryStarted) {
                Log.d("BTScan", "Classic Bluetooth discovery started successfully")
            } else {
                Log.e("BTScan", "Failed to start classic Bluetooth discovery")
                showToast("Failed to start Bluetooth discovery")
            }
        } catch (e: Exception) {
            Log.e("BTScan", "Error starting classic discovery: ${e.message}")
            showToast("Error: ${e.message}")
        }
        
        // Also start BLE scanning if available
        if (bleScanner != null) {
            try {
                // Set up scan settings for better discovery
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // Use low latency for faster results
                    .setReportDelay(0) // Report results immediately
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) // Report all matches
                    .build()
                
                // Start the BLE scan
                bleScanner?.startScan(null, settings, scanCallback)
                Log.d("BTScan", "BLE scanning started successfully")
            } catch (e: Exception) {
                Log.e("BTScan", "Error starting BLE scan: ${e.message}")
                showToast("BLE Error: ${e.message}")
            }
        } else {
            Log.d("BTScan", "BLE scanner not available, using only classic discovery")
        }
    }
    
    private fun stopBluetoothScan() {
        // Stop scan in ViewModel
        viewModel.stopScan()
        
        // Check for scan permission
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("BTScan", "Cannot stop scan - no BLUETOOTH_SCAN permission")
            return
        }
        
        handler.removeCallbacksAndMessages(null) // Remove pending stop scan
        
        try {
            // Stop BLE scanning if it was started
            if (bleScanner != null) {
                bleScanner?.stopScan(scanCallback)
                Log.d("BTScan", "BLE scanning stopped")
            }
            
            // Stop classic Bluetooth discovery if it was started
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter?.cancelDiscovery()
                Log.d("BTScan", "Classic Bluetooth discovery stopped")
            }
            
            Log.d("BTScan", "Stopped scanning, found ${viewModel.devices.size} devices")
            
            // Log all found devices
            viewModel.devices.forEachIndexed { index, device ->
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val name = device.name ?: "Unknown"
                    Log.d("BTScan", "Device $index: $name (${device.address})")
                } else {
                    Log.d("BTScan", "Device $index: Address ${device.address}")
                }
            }
            
            showToast("Stopped scanning, found ${viewModel.devices.size} devices")
        } catch (e: Exception) {
            Log.e("BTScan", "Error stopping scan: ${e.message}")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun BluetoothDevicesScreen(
    modifier: Modifier = Modifier,
    devices: List<BluetoothDevice>,
    isScanning: Boolean,
    connectingDeviceAddress: String? = null,
    connectedDeviceAddress: String? = null,
    lastReceivedData: String? = null,
    lastReceivedDataHex: String? = null,
    lastCharacteristicUuid: String? = null,
    volumeLevel: Int? = null,
    batteryLevel: Int? = null,
    onScanClick: () -> Unit,
    onDeviceClick: (BluetoothDevice) -> Unit,
    onRefreshClick: (BluetoothDevice) -> Unit = {}
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Bluetooth Device Scanner",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = onScanClick,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(if (isScanning) "Stop Scan" else "Start Scan")
            }
            
            // Add button to view logs
            Button(
                onClick = {
                    try {
                        val logFile = LogManager.getLogFile()
                        val logPath = LogManager.getLogFilePath()
                        
                        if (logFile != null && logFile.exists()) {
                            // Show the log file path
                            Toast.makeText(context, "Log file: $logPath", Toast.LENGTH_LONG).show()
                            
                            // Create a simple text file with the log content
                            val intent = Intent(Intent.ACTION_SEND)
                            intent.type = "text/plain"
                            
                            // Add the log file name as the subject
                            intent.putExtra(Intent.EXTRA_SUBJECT, "Bluetooth Log: ${LogManager.getLogFileName()}")
                            
                            // Read the log file content
                            val logContent = logFile.readText()
                            
                            // Add the log content as text
                            intent.putExtra(Intent.EXTRA_TEXT, logContent)
                            
                            // Start the activity
                            context.startActivity(Intent.createChooser(intent, "Share Log File"))
                        } else {
                            Toast.makeText(context, "Log file not found", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("LogShare", "Error sharing log: ${e.message}")
                        Toast.makeText(context, "Error sharing log: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text("View/Share Logs")
            }
        }
        
        if (isScanning) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Scanning for devices...")
            }
        }
        
        // Show connected device info if any
        if (connectedDeviceAddress != null) {
            val connectedDevice = devices.find { it.address == connectedDeviceAddress }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Connected Device",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (connectedDevice != null && ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        Text(
                            text = "Name: ${connectedDevice.name ?: "Unknown"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Text(
                        text = "Address: $connectedDeviceAddress",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    // Show volume level if available
                    if (volumeLevel != null) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Text(
                            text = "Volume Level: $volumeLevel",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Show battery level if available
                    if (batteryLevel != null) {
                        if (volumeLevel == null) {
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                        
                        Text(
                            text = "Battery Level: $batteryLevel%",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = if (volumeLevel != null) 4.dp else 0.dp)
                        )
                    }
                    
                    // Show received data if available
                    if (lastReceivedData != null && lastCharacteristicUuid != null) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Text(
                            text = "Latest Data:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = "From: $lastCharacteristicUuid",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Text(
                            text = "Data: $lastReceivedData",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        
                        if (lastReceivedDataHex != null) {
                            Text(
                                text = "Hex: $lastReceivedDataHex",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    
                    // Add buttons row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Refresh button
                        Button(
                            onClick = {
                                // Find the connected device and refresh volume/battery
                                connectedDevice?.let { 
                                    // This will be handled in MainActivity
                                    onRefreshClick(it)
                                }
                            }
                        ) {
                            Text("Refresh Info")
                        }
                        
                        // Disconnect button
                        Button(
                            onClick = { 
                                // Find the connected device and pass it to onDeviceClick
                                // This will trigger a disconnect in the ViewModel
                                connectedDevice?.let { onDeviceClick(it) }
                            }
                        ) {
                            Text("Disconnect")
                        }
                    }
                }
            }
        }
        
        Text(
            text = "Found Devices (${devices.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
        
        if (devices.isEmpty() && !isScanning) {
            Text(
                text = "No devices found. Try scanning again.",
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            LazyColumn {
                items(devices) { device ->
                    DeviceItem(
                        device = device,
                        isConnecting = device.address == connectingDeviceAddress,
                        isConnected = device.address == connectedDeviceAddress,
                        onClick = { onDeviceClick(device) }
                    )
                    Divider()
                }
            }
        }
    }
}

@Composable
fun DeviceItem(
    device: BluetoothDevice,
    isConnecting: Boolean = false,
    isConnected: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // We need to check for permission before accessing device name
            val deviceName = if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // Try to get the device name, but it might be null for many BLE devices
                val name = device.name
                if (name.isNullOrEmpty()) "Unknown Device (${device.address})" else name
            } else {
                "Permission Required"
            }
            
            Text(
                text = deviceName,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            
            Text(
                text = "Address: ${device.address}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            
            // Add device type information
            val deviceType = when(device.type) {
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
                BluetoothDevice.DEVICE_TYPE_LE -> "Low Energy"
                BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual Mode"
                BluetoothDevice.DEVICE_TYPE_UNKNOWN -> "Unknown"
                else -> "Undefined"
            }
            
            Text(
                text = "Type: $deviceType",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
            
            // Show connection status
            when {
                isConnected -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = "Connected",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                isConnecting -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 4.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Connecting...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                else -> {
                    Text(
                        text = "Tap to connect",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BluetoothDevicesScreenPreview() {
    MyApplicationTheme {
        // Create a preview with empty device list
        BluetoothDevicesScreen(
            devices = emptyList(),
            isScanning = false,
            connectingDeviceAddress = null,
            connectedDeviceAddress = null,
            lastReceivedData = null,
            lastReceivedDataHex = null,
            lastCharacteristicUuid = null,
            volumeLevel = 75,
            batteryLevel = 80,
            onScanClick = {},
            onDeviceClick = {},
            onRefreshClick = {}
        )
    }
}