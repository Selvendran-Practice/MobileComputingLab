package com.example.bluetoothtemplate

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import com.example.bluetoothtemplate.ui.theme.BluetoothTemplateTheme

// Create a CompositionLocal for the BluetoothController
val LocalBluetoothController = compositionLocalOf<BluetoothController> { 
    error("No BluetoothController provided") 
}

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothController: BluetoothController
    
    // Activity result launcher for Bluetooth permissions
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>
    
    // Activity result launcher for enabling Bluetooth
    private lateinit var enableBluetoothLauncher: ActivityResultLauncher<Intent>
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d("MainActivity", "onCreate called")
        
        // Initialize Bluetooth controller
        bluetoothController = BluetoothController(this)
        
        // Initialize permission launcher
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allPermissionsGranted = permissions.entries.all { it.value }
            Log.d("MainActivity", "Permission result: $allPermissionsGranted")
            if (allPermissionsGranted) {
                // Permissions granted, check if Bluetooth is enabled
                checkBluetoothEnabled()
            } else {
                Toast.makeText(
                    this,
                    "Bluetooth permissions are required for this app",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        
        // Initialize Bluetooth enabling launcher
        enableBluetoothLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            Log.d("MainActivity", "Bluetooth enable result: ${result.resultCode}")
            if (result.resultCode == RESULT_OK) {
                Toast.makeText(this, "Bluetooth enabled successfully", Toast.LENGTH_SHORT).show()
                // Start scanning for devices after Bluetooth is enabled
                Log.d("MainActivity", "Starting scan after Bluetooth enabled")
                bluetoothController.startScan()
            } else {
                Toast.makeText(this, "Bluetooth is required for this app", Toast.LENGTH_LONG).show()
            }
        }
        
        enableEdgeToEdge()
        setContent {
            BluetoothTemplateTheme {
                MainScreen(
                    bluetoothController = bluetoothController,
                    requestPermissions = { 
                        Log.d("MainActivity", "Request permissions called from UI")
                        requestBluetoothPermissions() 
                    },
                    enableBluetooth = { 
                        Log.d("MainActivity", "Enable Bluetooth called from UI")
                        checkBluetoothEnabled() 
                    },
                    startScan = { 
                        Log.d("MainActivity", "Start scan called from UI")
                        // Start scanning for devices
                        bluetoothController.startScan() 
                    },
                    stopScan = { 
                        Log.d("MainActivity", "Stop scan called from UI")
                        bluetoothController.stopScan() 
                    }
                )
            }
        }
        
        // Check Bluetooth status when app starts
        checkBluetoothStatus()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up Bluetooth resources
        bluetoothController.release()
    }
    
    private fun checkBluetoothStatus() {
        Log.d("MainActivity", "Checking Bluetooth status")
        
        if (!bluetoothController.isBluetoothSupported()) {
            Log.e("MainActivity", "Bluetooth is not supported on this device")
            Toast.makeText(
                this,
                "Bluetooth is not supported on this device",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        
        val hasPermissions = bluetoothController.hasBluetoothPermissions()
        Log.d("MainActivity", "Has Bluetooth permissions: $hasPermissions")
        
        if (!hasPermissions) {
            requestBluetoothPermissions()
        } else {
            checkBluetoothEnabled()
        }
    }
    
    private fun requestBluetoothPermissions() {
        Log.d("MainActivity", "Requesting Bluetooth permissions")
        
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        
        Log.d("MainActivity", "Requesting permissions: ${permissions.joinToString()}")
        requestPermissionLauncher.launch(permissions)
    }
    
    private fun checkBluetoothEnabled() {
        Log.d("MainActivity", "Checking if Bluetooth is enabled")
        
        val isEnabled = bluetoothController.isBluetoothEnabled()
        Log.d("MainActivity", "Bluetooth enabled: $isEnabled")
        
        if (!isEnabled) {
            val enableBtIntent = bluetoothController.getEnableBluetoothIntent()
            Log.d("MainActivity", "Launching enable Bluetooth intent")
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
            // Bluetooth is enabled, now check location services
            checkLocationEnabled()
        }
    }
    
    private fun checkLocationEnabled() {
        Log.d("MainActivity", "Checking if Location is enabled")
        
        // Location check is only needed for Android 6.0 to Android 11
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && 
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                                   locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            
            Log.d("MainActivity", "Location enabled: $isLocationEnabled")
            
            if (!isLocationEnabled) {
                Toast.makeText(
                    this,
                    "Location services must be enabled for Bluetooth scanning",
                    Toast.LENGTH_LONG
                ).show()
                
                // Prompt user to enable location
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
                return
            }
        }
        
        // Location is enabled or not needed, start scanning
        Log.d("MainActivity", "Location check passed, starting scan")
        bluetoothController.startScan()
    }
}

@Composable
fun MainScreen(
    bluetoothController: BluetoothController,
    requestPermissions: () -> Unit,
    enableBluetooth: () -> Unit,
    startScan: () -> Unit,
    stopScan: () -> Unit
) {
    val context = LocalContext.current
    val scannedDevices by bluetoothController.scannedDevices.collectAsState()
    val isScanning by bluetoothController.isScanning.collectAsState()
    
    // Provide the BluetoothController to the composition
    androidx.compose.runtime.CompositionLocalProvider(
        LocalBluetoothController provides bluetoothController
    ) {
    
    // Check Bluetooth status when the composable is first launched
    LaunchedEffect(key1 = Unit) {
        if (!bluetoothController.isBluetoothSupported()) {
            Toast.makeText(
                context,
                "Bluetooth is not supported on this device",
                Toast.LENGTH_LONG
            ).show()
            return@LaunchedEffect
        }
        
        if (!bluetoothController.hasBluetoothPermissions()) {
            // This will be handled by the Activity
        } else if (!bluetoothController.isBluetoothEnabled()) {
            // Directly request to enable Bluetooth without showing a custom dialog
            enableBluetooth()
        } else {
            // Start scanning if Bluetooth is already enabled
            startScan()
        }
    }
    
    // Clean up when the composable is disposed
    DisposableEffect(key1 = Unit) {
        onDispose {
            stopScan()
        }
    }
    
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        BluetoothDeviceList(
            devices = scannedDevices,
            isScanning = isScanning,
            onScanClick = {
                if (isScanning) {
                    Log.d("MainActivity", "Stopping scan...")
                    Toast.makeText(context, "Stopping Bluetooth scan", Toast.LENGTH_SHORT).show()
                    // Use runCatching to handle any exceptions
                    runCatching {
                        stopScan()
                    }.onFailure { e ->
                        Log.e("MainActivity", "Error stopping scan: ${e.message}")
                        Toast.makeText(context, "Error stopping scan", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.d("MainActivity", "Starting scan...")
                    Toast.makeText(context, "Starting Bluetooth scan for nearby devices", Toast.LENGTH_SHORT).show()
                    // Use runCatching to handle any exceptions
                    runCatching {
                        startScan()
                    }.onFailure { e ->
                        Log.e("MainActivity", "Error starting scan: ${e.message}")
                        Toast.makeText(context, "Error starting scan", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            contentPadding = innerPadding
        )
    }
    } // Close CompositionLocalProvider
}

@Composable
fun BluetoothDeviceList(
    devices: List<BluetoothDevice>,
    isScanning: Boolean,
    onScanClick: () -> Unit,
    contentPadding: PaddingValues
) {
    val bluetoothController = LocalBluetoothController.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        // Header with scan button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Nearby Bluetooth Devices",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Show device count
            Text(
                text = "${devices.size} device${if (devices.size != 1) "s" else ""} found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onScanClick) {
                    Text(text = if (isScanning) "Stop Scan" else "Start Scan")
                }
            }
            
            if (isScanning) {
                Spacer(modifier = Modifier.height(8.dp))
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Scanning for both Classic and BLE devices...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        // List of devices
        if (devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isScanning) "Searching for devices..." else "No devices found",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    
                    if (!isScanning) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Make sure Bluetooth devices are nearby and discoverable",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(devices) { device ->
                    BluetoothDeviceItem(device = device)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BluetoothDeviceListEmptyPreview() {
    // For preview, we'll just show the UI without the actual controller
    BluetoothTemplateTheme {
        // Create a simple mock version of the BluetoothDeviceList that doesn't use the controller
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PaddingValues(0.dp))
        ) {
            // Header with scan button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Nearby Bluetooth Devices",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Show device count
                Text(
                    text = "0 devices found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { }) {
                        Text(text = "Start Scan")
                    }
                    
                    OutlinedButton(onClick = { }) {
                        Text("Add Test Device")
                    }
                }
                
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No devices found",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Make sure Bluetooth devices are nearby and discoverable",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BluetoothDeviceListScanningPreview() {
    // For preview, we'll just show the UI without the actual controller
    BluetoothTemplateTheme {
        // Create a simple mock version of the BluetoothDeviceList that doesn't use the controller
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PaddingValues(0.dp))
        ) {
            // Header with scan button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Nearby Bluetooth Devices",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Show device count
                Text(
                    text = "0 devices found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { }) {
                        Text(text = "Stop Scan")
                    }
                    
                    OutlinedButton(onClick = { }) {
                        Text("Add Test Device")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Scanning for both Classic and BLE devices...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                
                // Empty state with scanning
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Searching for devices...",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}