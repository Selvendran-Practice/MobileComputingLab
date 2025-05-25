package com.example.blue

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
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
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.blue.ui.theme.BlueTheme

// Opt-in for experimental Material 3 APIs
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var isScanning = false
    private val discoveredDevices = mutableStateListOf<BluetoothDevice>()
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var bluetoothEnableLauncher: ActivityResultLauncher<Intent>
    private val handler = Handler(Looper.getMainLooper())
    private val scanTimeout = 30000L // 30 seconds timeout for scanning
    
    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    
    // Tag for logging
    companion object {
        private const val TAG = "BluetoothScanner"
    }

    // BLE Scanner callback
    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            Log.d(TAG, "BLE device found: ${device.address}, RSSI: ${result.rssi}")
            
            if (!discoveredDevices.contains(device)) {
                discoveredDevices.add(device)
                
                // Try to get the device name with proper permission check
                if (ActivityCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val deviceName = device.name ?: "Unknown"
                    Log.d(TAG, "Added BLE device: $deviceName (${device.address})")
                }
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
            isScanning = false
        }
    }

    // Classic Bluetooth receiver
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    Log.d(TAG, "Classic Bluetooth device found")
                    
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    
                    device?.let {
                        Log.d(TAG, "Classic device address: ${it.address}")
                        
                        if (!discoveredDevices.contains(it)) {
                            discoveredDevices.add(it)
                            
                            // Try to get the device name with proper permission check
                            if (ActivityCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                val deviceName = it.name ?: "Unknown"
                                Log.d(TAG, "Added classic device: $deviceName (${it.address})")
                            }
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Log.d(TAG, "Classic Bluetooth discovery finished")
                    isScanning = false
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Log.d(TAG, "Classic Bluetooth discovery started")
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    when (state) {
                        BluetoothAdapter.STATE_OFF -> Log.d(TAG, "Bluetooth turned off")
                        BluetoothAdapter.STATE_ON -> Log.d(TAG, "Bluetooth turned on")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize activity result launchers
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.values.all { it }) {
                // All permissions granted, start scanning
                startBluetoothScan()
            } else {
                // Permissions denied
                Toast.makeText(
                    this,
                    "Permissions are required to scan for Bluetooth devices",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        
        bluetoothEnableLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, start scanning
                startBluetoothScan()
            } else {
                // User declined to enable Bluetooth
                Toast.makeText(
                    this,
                    "Bluetooth is required to scan for devices",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        
        // Initialize Bluetooth adapter
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        
        // Register for broadcasts when a device is discovered
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        registerReceiver(bluetoothReceiver, filter)
        
        Log.d(TAG, "Bluetooth receiver registered")
        
        enableEdgeToEdge()
        setContent {
            BlueTheme {
                BluetoothScannerApp(
                    discoveredDevices = discoveredDevices,
                    isScanning = isScanning,
                    onStartScan = { startBluetoothScan() },
                    onStopScan = { stopBluetoothScan() },
                    onEnableBluetooth = { enableBluetooth() },
                    isBluetoothEnabled = { bluetoothAdapter.isEnabled },
                    requestPermissions = { requestPermissions() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister broadcast receiver
        unregisterReceiver(bluetoothReceiver)
        // Stop scanning if active
        stopBluetoothScan()
    }

    private fun startBluetoothScan() {
        // Check if we have the necessary permissions
        if (!hasRequiredPermissions()) {
            Log.d(TAG, "Missing required permissions, requesting...")
            requestPermissions()
            return
        }

        // Check if Bluetooth is enabled
        if (!bluetoothAdapter.isEnabled) {
            Log.d(TAG, "Bluetooth is not enabled, requesting to enable...")
            enableBluetooth()
            return
        }

        // Clear previous results
        discoveredDevices.clear()
        isScanning = true
        Log.d(TAG, "Starting Bluetooth scan...")

        // Start classic Bluetooth discovery
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Stop any existing discovery first
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
            
            // Start classic Bluetooth discovery
            val discoveryStarted = bluetoothAdapter.startDiscovery()
            Log.d(TAG, "Classic Bluetooth discovery started: $discoveryStarted")
            
            // Configure BLE scan settings for better discovery
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // Use low latency, high power mode
                .setReportDelay(0) // Report results immediately
                .build()
                
            // Start BLE scanning with optimized settings
            bluetoothAdapter.bluetoothLeScanner?.let { scanner ->
                Log.d(TAG, "Starting BLE scan with optimized settings")
                scanner.startScan(null, scanSettings, bleScanCallback)
            } ?: Log.e(TAG, "BLE scanner is null")
            
            // Show a toast message
            Toast.makeText(this, "Scanning for devices...", Toast.LENGTH_SHORT).show()
            
            // Set a timeout to stop scanning after the defined period
            handler.postDelayed({
                if (isScanning) {
                    Log.d(TAG, "Scan timeout reached, stopping scan")
                    stopBluetoothScan()
                    Toast.makeText(this, "Scan completed", Toast.LENGTH_SHORT).show()
                }
            }, scanTimeout)
        } else {
            Log.e(TAG, "BLUETOOTH_SCAN permission not granted")
        }
    }

    private fun stopBluetoothScan() {
        Log.d(TAG, "Stopping Bluetooth scan...")
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Stop classic Bluetooth discovery
            if (bluetoothAdapter.isDiscovering) {
                val discoveryStopped = bluetoothAdapter.cancelDiscovery()
                Log.d(TAG, "Classic Bluetooth discovery stopped: $discoveryStopped")
            }
            
            // Stop BLE scanning
            bluetoothAdapter.bluetoothLeScanner?.let { scanner ->
                Log.d(TAG, "Stopping BLE scan")
                scanner.stopScan(bleScanCallback)
            }
            
            isScanning = false
        } else {
            Log.e(TAG, "BLUETOOTH_SCAN permission not granted")
        }
        
        // Remove any pending scan timeout callbacks
        handler.removeCallbacksAndMessages(null)
    }

    private fun enableBluetooth() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothEnableLauncher.launch(enableBtIntent)
        } else {
            requestPermissions()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        permissionLauncher.launch(requiredPermissions)
    }

    // Already defined above with TAG
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothScannerApp(
    discoveredDevices: List<BluetoothDevice>,
    isScanning: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onEnableBluetooth: () -> Unit,
    isBluetoothEnabled: () -> Boolean,
    requestPermissions: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Bluetooth Scanner") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Scan button
            Button(
                onClick = {
                    if (isScanning) {
                        onStopScan()
                    } else {
                        if (isBluetoothEnabled()) {
                            onStartScan()
                        } else {
                            onEnableBluetooth()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(if (isScanning) "Stop Scan" else "Start Scan")
            }
            
            // Status text
            Text(
                text = if (isScanning) "Scanning for devices..." else "Scan stopped",
                modifier = Modifier.padding(vertical = 8.dp),
                color = if (isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            
            // Devices count
            Text(
                text = "Found ${discoveredDevices.size} device(s)",
                modifier = Modifier.padding(vertical = 8.dp),
                fontWeight = FontWeight.Bold
            )
            
            // Device list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(discoveredDevices) { device ->
                    DeviceItem(device)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceItem(device: BluetoothDevice) {
    val deviceName = if (ActivityCompat.checkSelfPermission(
            LocalContext.current,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    ) {
        device.name ?: "Unknown Device"
    } else {
        "Unknown Device (Permission Required)"
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = deviceName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "MAC: ${device.address}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Type: ${getDeviceTypeName(device.type)}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun getDeviceTypeName(type: Int): String {
    return when (type) {
        BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
        BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
        BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual Mode"
        BluetoothDevice.DEVICE_TYPE_UNKNOWN -> "Unknown"
        else -> "Unknown"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun BluetoothScannerPreview() {
    BlueTheme {
        BluetoothScannerApp(
            discoveredDevices = emptyList(),
            isScanning = false,
            onStartScan = { },
            onStopScan = { },
            onEnableBluetooth = { },
            isBluetoothEnabled = { false },
            requestPermissions = { }
        )
    }
}