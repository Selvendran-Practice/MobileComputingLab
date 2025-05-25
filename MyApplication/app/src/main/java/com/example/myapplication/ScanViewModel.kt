package com.example.myapplication

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.getDefaultAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.util.Arrays
import java.util.UUID

class ScanViewModel : ViewModel() {
    // Standard Bluetooth GATT UUIDs
    companion object {
        // Services
        val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
        val AUDIO_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB") // Heart Rate service often used for audio controls
        val VOLUME_CONTROL_SERVICE_UUID = UUID.fromString("00001844-0000-1000-8000-00805F9B34FB") // Volume Control service
        
        // Characteristics
        val BATTERY_LEVEL_CHARACTERISTIC_UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
        val VOLUME_STATE_CHARACTERISTIC_UUID = UUID.fromString("00002B7D-0000-1000-8000-00805F9B34FB")
        val VOLUME_CONTROL_POINT_CHARACTERISTIC_UUID = UUID.fromString("00002B7E-0000-1000-8000-00805F9B34FB")
        val VOLUME_FLAGS_CHARACTERISTIC_UUID = UUID.fromString("00002B7F-0000-1000-8000-00805F9B34FB")
    }
    
    // Scanning state
    var isScanning by mutableStateOf(false)
        private set
    
    // Device list
    val devices = mutableStateListOf<BluetoothDevice>()
    
    // Connection states
    var connectingDeviceAddress by mutableStateOf<String?>(null)
        private set
    
    var connectedDeviceAddress by mutableStateOf<String?>(null)
        private set
        
    // Latest received data
    var lastReceivedData by mutableStateOf<String?>(null)
        private set
        
    var lastReceivedDataHex by mutableStateOf<String?>(null)
        private set
        
    var lastCharacteristicUuid by mutableStateOf<String?>(null)
        private set
        
    // Volume level information
    var volumeLevel by mutableStateOf<Int?>(null)
        private set
        
    // Battery level information
    var batteryLevel by mutableStateOf<Int?>(null)
        private set
    
    // GATT connection
    private var bluetoothGatt: BluetoothGatt? = null
    
    fun startScan() {
        LogManager.log("BTScan", "ViewModel: startScan called")
        isScanning = true
        devices.clear()
    }
    
    fun stopScan() {
        LogManager.log("BTScan", "ViewModel: stopScan called")
        isScanning = false
    }
    
    fun addDevice(device: BluetoothDevice) {
        if (devices.none { it.address == device.address }) {
            LogManager.log("BTScan", "ViewModel: Adding device ${device.address}")
            devices.add(device)
        }
    }
    
    fun connectToDevice(context: Context, device: BluetoothDevice) {
        // Don't try to connect if already connecting or connected to this device
        if (connectingDeviceAddress == device.address || connectedDeviceAddress == device.address) {
            Log.d("BTConnect", "Already connecting/connected to ${device.address}")
            return
        }
        
        // Disconnect from any existing connection
        disconnectFromDevice()
        
        // Set connecting state
        connectingDeviceAddress = device.address
        Log.d("BTConnect", "Connecting to device: ${device.address}")
        
        // Check if the device is likely in range by checking if it's in the scanned devices list
        val isInScannedDevices = devices.any { it.address == device.address }
        if (!isInScannedDevices && devices.isNotEmpty()) {
            LogManager.log("BTConnect", "Warning: Device ${device.address} is not in the recently scanned devices list. It might be out of range.", LogManager.LogLevel.WARNING)
        }
        
        try {
            // First, try to determine if this is an audio device (headphones, speaker, etc.)
            // by checking its class
            val deviceClass = device.bluetoothClass?.majorDeviceClass
            val isAudioDevice = deviceClass == android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO
            
            LogManager.log("BTConnect", "Device class: $deviceClass, isAudioDevice: $isAudioDevice", LogManager.LogLevel.INFO)
            
            if (isAudioDevice) {
                // This is likely a headphone or speaker, so we should use A2DP profile
                LogManager.log("BTConnect", "Detected audio device, attempting to connect using A2DP profile", LogManager.LogLevel.INFO)
                
                // For audio devices, we need to create a bond first if not already bonded
                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    LogManager.log("BTConnect", "Device not bonded, creating bond first", LogManager.LogLevel.INFO)
                    
                    // Create a bond (pair) with the device
                    val createBondMethod = device.javaClass.getMethod("createBond")
                    val bondSuccess = createBondMethod.invoke(device) as Boolean
                    
                    if (bondSuccess) {
                        LogManager.log("BTConnect", "Bond creation initiated successfully", LogManager.LogLevel.INFO)
                        connectionCallback(false, "Pairing with device... Please wait")
                    } else {
                        LogManager.log("BTConnect", "Failed to initiate bond creation", LogManager.LogLevel.ERROR)
                        connectingDeviceAddress = null
                        connectionCallback(false, "Failed to initiate pairing")
                        return
                    }
                }
                
                // For audio devices, we need to connect to the A2DP profile
                // This requires reflection since the API is not directly accessible
                try {
                    LogManager.log("BTConnect", "Attempting to connect to A2DP profile", LogManager.LogLevel.INFO)
                    
                    // Get the A2DP proxy class
                    val a2dpClass = Class.forName("android.bluetooth.BluetoothA2dp")
                    
                    // Get the connect method
                    val connectMethod = a2dpClass.getMethod("connect", BluetoothDevice::class.java)
                    
                    // Get the A2DP proxy
                    val getProfileProxyMethod = BluetoothAdapter::class.java.getMethod(
                        "getProfileProxy",
                        Context::class.java,
                        Class.forName("android.bluetooth.BluetoothProfile\$ServiceListener"),
                        Int::class.java
                    )
                    
                    // Create a service listener
                    val serviceListener = object : Any() {
                        fun onServiceConnected(profile: Int, proxy: Any) {
                            LogManager.log("BTConnect", "A2DP service connected", LogManager.LogLevel.INFO)
                            
                            try {
                                // Connect to the device using the A2DP proxy
                                val result = connectMethod.invoke(proxy, device) as Boolean
                                
                                if (result) {
                                    LogManager.log("BTConnect", "A2DP connection initiated successfully", LogManager.LogLevel.INFO)
                                    connectedDeviceAddress = device.address
                                    connectingDeviceAddress = null
                                    connectionCallback(true, "Connected to audio device")
                                } else {
                                    LogManager.log("BTConnect", "A2DP connection failed to initiate", LogManager.LogLevel.ERROR)
                                    connectingDeviceAddress = null
                                    connectionCallback(false, "Failed to connect to audio device")
                                }
                            } catch (e: Exception) {
                                LogManager.log("BTConnect", "Error connecting to A2DP: ${e.message}", LogManager.LogLevel.ERROR)
                                connectingDeviceAddress = null
                                connectionCallback(false, "Error connecting to audio device: ${e.message}")
                            }
                        }
                        
                        fun onServiceDisconnected(profile: Int) {
                            LogManager.log("BTConnect", "A2DP service disconnected", LogManager.LogLevel.INFO)
                        }
                    }
                    
                    // Get the A2DP profile proxy
                    val result = getProfileProxyMethod.invoke(
                        bluetoothAdapter,
                        context,
                        serviceListener,
                        BluetoothProfile.A2DP
                    ) as Boolean
                    
                    if (result) {
                        LogManager.log("BTConnect", "A2DP proxy request successful", LogManager.LogLevel.INFO)
                        // The connection will be handled in the service listener
                    } else {
                        LogManager.log("BTConnect", "Failed to get A2DP proxy", LogManager.LogLevel.ERROR)
                        connectingDeviceAddress = null
                        connectionCallback(false, "Failed to initialize audio connection")
                    }
                } catch (e: Exception) {
                    LogManager.log("BTConnect", "Error setting up A2DP connection: ${e.message}", LogManager.LogLevel.ERROR)
                    e.printStackTrace()
                    
                    // Fall back to GATT connection if A2DP fails
                    LogManager.log("BTConnect", "Falling back to GATT connection", LogManager.LogLevel.INFO)
                    connectUsingGatt(context, device, connectionCallback)
                }
                return
            } else {
                // For non-audio devices, use GATT connection
                LogManager.log("BTConnect", "Not an audio device, using GATT connection", LogManager.LogLevel.INFO)
                connectUsingGatt(context, device, connectionCallback)
                return
            }
        } catch (e: Exception) {
            LogManager.log("BTConnect", "Error in connection process: ${e.message}", LogManager.LogLevel.ERROR)
            e.printStackTrace()
            
            // Fall back to GATT connection if there was an error
            LogManager.log("BTConnect", "Falling back to GATT connection due to error", LogManager.LogLevel.INFO)
            connectUsingGatt(context, device, connectionCallback)
        }
                    
                    // Set connecting state to null
                    connectingDeviceAddress = null
                    
                    // Check if we should retry the connection
                    if (status == 133 && connectionRetryCount < MAX_CONNECTION_RETRIES) {
                        // Store retry information
                        deviceToRetry = gatt.device
                        retryContext = context
                        retryCallback = connectionCallback
                        
                        // Increment retry count
                        connectionRetryCount++
                        
                        // Log retry attempt
                        LogManager.log("BTConnect", "Connection failed with timeout. Retrying ($connectionRetryCount/$MAX_CONNECTION_RETRIES) in 2 seconds...", LogManager.LogLevel.WARNING)
                        
                        // Notify user of retry
                        connectionCallback(false, "Connection timeout. Retrying in 2 seconds... (Attempt $connectionRetryCount/$MAX_CONNECTION_RETRIES)")
                        
                        // Schedule retry after delay
                        Handler(Looper.getMainLooper()).postDelayed({
                            retryConnection()
                        }, 2000) // 2 second delay
                    } else {
                        // Reset retry count
                        connectionRetryCount = 0
                        deviceToRetry = null
                        retryContext = null
                        retryCallback = null
                        
                        // Notify of failure
                        connectionCallback(false, errorMessage)
                    }
                }
            }
            
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    LogManager.log("BTConnect", "Services discovered")
                    
                    // Set connected state
                    connectingDeviceAddress = null
                    connectedDeviceAddress = gatt.device.address
                    
                    // Log all services and characteristics
                    val services = gatt.services
                    LogManager.log("BTData", "Found ${services.size} services")
                    
                    // Log detailed information about all services and characteristics
                    logAllServicesAndCharacteristics(gatt)
                    
                    // After services are discovered, query volume and battery levels
                    LogManager.log("BTData", "Querying volume and battery levels after service discovery", LogManager.LogLevel.INFO)
                    queryVolumeLevel()
                    queryBatteryLevel()
                    
                    services.forEach { service ->
                        val serviceUuid = service.uuid
                        LogManager.log("BTData", "Service: $serviceUuid")
                        
                        // Get characteristics for this service
                        val characteristics = service.characteristics
                        LogManager.log("BTData", "  Found ${characteristics.size} characteristics")
                        
                        characteristics.forEach { characteristic ->
                            val charUuid = characteristic.uuid
                            val properties = characteristic.properties
                            LogManager.log("BTData", "  Characteristic: $charUuid, Properties: $properties")
                            
                            // Check if this characteristic has notify property
                            if ((properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                                LogManager.log("BTData", "  Enabling notifications for $charUuid")
                                
                                // Enable notifications for this characteristic
                                val success = gatt.setCharacteristicNotification(characteristic, true)
                                LogManager.log("BTData", "  Notification enabled: $success")
                                
                                // Get the descriptor for enabling notifications
                                val descriptor = characteristic.getDescriptor(
                                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") // Client Characteristic Configuration Descriptor
                                )
                                
                                if (descriptor != null) {
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    gatt.writeDescriptor(descriptor)
                                    LogManager.log("BTData", "  Notification descriptor written")
                                }
                            }
                            
                            // Check if this characteristic has read property
                            if ((properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                                LogManager.log("BTData", "  Reading characteristic $charUuid")
                                gatt.readCharacteristic(characteristic)
                            }
                        }
                    }
                } else {
                    Log.e("BTConnect", "Service discovery failed: $status")
                }
            }
            
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                val uuid = characteristic.uuid
                val uuidString = uuid.toString()
                
                // Determine if this is a known characteristic
                val charName = when(uuid) {
                    BATTERY_LEVEL_CHARACTERISTIC_UUID -> "Battery Level"
                    VOLUME_STATE_CHARACTERISTIC_UUID -> "Volume State"
                    VOLUME_CONTROL_POINT_CHARACTERISTIC_UUID -> "Volume Control Point"
                    VOLUME_FLAGS_CHARACTERISTIC_UUID -> "Volume Flags"
                    else -> "Unknown"
                }
                
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val data = value
                    val hexString = bytesToHex(data)
                    val utf8String = try {
                        String(data, Charsets.UTF_8)
                    } catch (e: Exception) {
                        "Non-text data"
                    }
                    
                    LogManager.log("BTData", "Read from $uuidString ($charName)", LogManager.LogLevel.INFO)
                    LogManager.log("BTData", "Data (HEX): $hexString", LogManager.LogLevel.INFO)
                    LogManager.log("BTData", "Data (UTF-8): $utf8String", LogManager.LogLevel.INFO)
                    LogManager.log("BTData", "Data (Bytes): ${Arrays.toString(data)}", LogManager.LogLevel.INFO)
                    
                    // Log specific interpretations for debugging
                    if (data.isNotEmpty()) {
                        val firstByte = data[0].toInt() and 0xFF
                        LogManager.log("BTData", "First byte as integer: $firstByte", LogManager.LogLevel.INFO)
                        
                        if (data.size >= 2) {
                            val value16bitLE = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
                            val value16bitBE = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                            LogManager.log("BTData", "As 16-bit LE: $value16bitLE", LogManager.LogLevel.INFO)
                            LogManager.log("BTData", "As 16-bit BE: $value16bitBE", LogManager.LogLevel.INFO)
                        }
                    }
                    
                    // Update the state variables
                    lastReceivedData = utf8String
                    lastReceivedDataHex = hexString
                    lastCharacteristicUuid = characteristic.uuid.toString()
                    
                    // Check if this is a battery level characteristic
                    if (characteristic.uuid == BATTERY_LEVEL_CHARACTERISTIC_UUID) {
                        if (data.isNotEmpty()) {
                            val level = data[0].toInt() and 0xFF
                            LogManager.log("BTBattery", "Battery level from standard characteristic: $level%", LogManager.LogLevel.INFO)
                            LogManager.log("BTBattery", "Raw data: ${bytesToHex(data)}", LogManager.LogLevel.INFO)
                            batteryLevel = level
                        } else {
                            LogManager.log("BTBattery", "Battery characteristic returned empty data", LogManager.LogLevel.WARNING)
                        }
                    }
                    
                    // Check if this is a volume state characteristic
                    if (characteristic.uuid == VOLUME_STATE_CHARACTERISTIC_UUID) {
                        if (data.isNotEmpty()) {
                            val level = data[0].toInt() and 0xFF
                            LogManager.log("BTVolume", "Volume level from standard characteristic: $level", LogManager.LogLevel.INFO)
                            LogManager.log("BTVolume", "Raw data: ${bytesToHex(data)}", LogManager.LogLevel.INFO)
                            volumeLevel = level
                        } else {
                            LogManager.log("BTVolume", "Volume characteristic returned empty data", LogManager.LogLevel.WARNING)
                        }
                    }
                    
                    // Check for other potential volume or battery characteristics
                    val uuid = characteristic.uuid.toString().lowercase()
                    
                    // Log all data for debugging
                    LogManager.log("BTData", "Characteristic ${characteristic.uuid} data:", LogManager.LogLevel.INFO)
                    LogManager.log("BTData", "  Hex: ${bytesToHex(data)}", LogManager.LogLevel.INFO)
                    LogManager.log("BTData", "  Decimal: ${data.joinToString(", ") { (it.toInt() and 0xFF).toString() }}", LogManager.LogLevel.INFO)
                    if (data.size >= 2) {
                        // Try to interpret as 16-bit value (little endian)
                        val value16bit = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
                        LogManager.log("BTData", "  As 16-bit value (LE): $value16bit", LogManager.LogLevel.INFO)
                        
                        // Try to interpret as 16-bit value (big endian)
                        val value16bitBE = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                        LogManager.log("BTData", "  As 16-bit value (BE): $value16bitBE", LogManager.LogLevel.INFO)
                    }
                    
                    // Volume-related characteristics
                    if (uuid.contains("vol") || uuid.contains("audio")) {
                        if (data.isNotEmpty()) {
                            // Try different interpretations of the data
                            val level1 = data[0].toInt() and 0xFF
                            LogManager.log("BTVolume", "Potential volume level (first byte): $level1", LogManager.LogLevel.INFO)
                            
                            // If there are at least 2 bytes, try 16-bit interpretation
                            if (data.size >= 2) {
                                val level2 = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
                                LogManager.log("BTVolume", "Potential volume level (16-bit LE): $level2", LogManager.LogLevel.INFO)
                                
                                val level3 = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                                LogManager.log("BTVolume", "Potential volume level (16-bit BE): $level3", LogManager.LogLevel.INFO)
                            }
                            
                            // Use the first byte as the volume level
                            volumeLevel = level1
                        } else {
                            LogManager.log("BTVolume", "Volume-related characteristic returned empty data", LogManager.LogLevel.WARNING)
                        }
                    } 
                    // Battery-related characteristics
                    else if (uuid.contains("batt") || (uuid.contains("level") && !uuid.contains("vol"))) {
                        if (data.isNotEmpty()) {
                            // Try different interpretations of the data
                            val level1 = data[0].toInt() and 0xFF
                            
                            // If there are at least 2 bytes, try 16-bit interpretation
                            if (data.size >= 2) {
                                val level2 = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
                                LogManager.log("BTBattery", "Potential battery level (16-bit LE): $level2", LogManager.LogLevel.INFO)
                                
                                val level3 = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                                LogManager.log("BTBattery", "Potential battery level (16-bit BE): $level3", LogManager.LogLevel.INFO)
                                
                                // If the 16-bit value seems reasonable (0-100), use it
                                if (level2 in 0..100) {
                                    LogManager.log("BTBattery", "Using 16-bit LE value as battery level: $level2%", LogManager.LogLevel.INFO)
                                    batteryLevel = level2
                                    return
                                } else if (level3 in 0..100) {
                                    LogManager.log("BTBattery", "Using 16-bit BE value as battery level: $level3%", LogManager.LogLevel.INFO)
                                    batteryLevel = level3
                                    return
                                }
                            }
                            
                            // If the first byte seems reasonable (0-100), use it
                            if (level1 in 0..100) {
                                LogManager.log("BTBattery", "Potential battery level (first byte): $level1%", LogManager.LogLevel.INFO)
                                batteryLevel = level1
                            } else {
                                LogManager.log("BTBattery", "First byte value $level1 outside expected battery range (0-100)", LogManager.LogLevel.WARNING)
                                
                                // Try to interpret as a percentage of 255
                                val percentOf255 = (level1 * 100) / 255
                                if (percentOf255 in 0..100) {
                                    LogManager.log("BTBattery", "Interpreting as percentage of 255: $percentOf255%", LogManager.LogLevel.INFO)
                                    batteryLevel = percentOf255
                                }
                            }
                        } else {
                            LogManager.log("BTBattery", "Battery-related characteristic returned empty data", LogManager.LogLevel.WARNING)
                        }
                    }
                } else {
                    LogManager.log("BTData", "Failed to read characteristic ${characteristic.uuid}, status: $status", LogManager.LogLevel.ERROR)
                }
            }
            
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                val data = value
                val hexString = data.joinToString(separator = " ") { 
                    String.format("%02X", it) 
                }
                val utf8String = try {
                    String(data, Charsets.UTF_8)
                } catch (e: Exception) {
                    "Non-text data"
                }
                
                LogManager.log("BTData", "Notification from ${characteristic.uuid}")
                LogManager.log("BTData", "Data (HEX): $hexString")
                LogManager.log("BTData", "Data (UTF-8): $utf8String")
                LogManager.log("BTData", "Data (Bytes): ${Arrays.toString(data)}")
                
                // Update the state variables
                lastReceivedData = utf8String
                lastReceivedDataHex = hexString
                lastCharacteristicUuid = characteristic.uuid.toString()
                
                // Log all notifications for debugging
                LogManager.log("BTNotify", "Notification from ${characteristic.uuid}", LogManager.LogLevel.INFO)
                LogManager.log("BTNotify", "  Hex: ${bytesToHex(data)}", LogManager.LogLevel.INFO)
                LogManager.log("BTNotify", "  Decimal: ${data.joinToString(", ") { (it.toInt() and 0xFF).toString() }}", LogManager.LogLevel.INFO)
                
                if (data.size >= 2) {
                    // Try to interpret as 16-bit value (little endian)
                    val value16bit = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
                    LogManager.log("BTNotify", "  As 16-bit value (LE): $value16bit", LogManager.LogLevel.INFO)
                    
                    // Try to interpret as 16-bit value (big endian)
                    val value16bitBE = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                    LogManager.log("BTNotify", "  As 16-bit value (BE): $value16bitBE", LogManager.LogLevel.INFO)
                }
                
                // Check if this is a battery level characteristic
                if (characteristic.uuid == BATTERY_LEVEL_CHARACTERISTIC_UUID) {
                    if (data.isNotEmpty()) {
                        val level = data[0].toInt() and 0xFF
                        LogManager.log("BTBattery", "Battery level notification from standard characteristic: $level%", LogManager.LogLevel.INFO)
                        LogManager.log("BTBattery", "Raw data: ${bytesToHex(data)}", LogManager.LogLevel.INFO)
                        batteryLevel = level
                    } else {
                        LogManager.log("BTBattery", "Battery characteristic notification returned empty data", LogManager.LogLevel.WARNING)
                    }
                }
                
                // Check if this is a volume state characteristic
                if (characteristic.uuid == VOLUME_STATE_CHARACTERISTIC_UUID) {
                    if (data.isNotEmpty()) {
                        val level = data[0].toInt() and 0xFF
                        LogManager.log("BTVolume", "Volume level notification from standard characteristic: $level", LogManager.LogLevel.INFO)
                        LogManager.log("BTVolume", "Raw data: ${bytesToHex(data)}", LogManager.LogLevel.INFO)
                        volumeLevel = level
                    } else {
                        LogManager.log("BTVolume", "Volume characteristic notification returned empty data", LogManager.LogLevel.WARNING)
                    }
                }
                
                // Check for other potential volume or battery characteristics
                val uuid = characteristic.uuid.toString().lowercase()
                
                // Volume-related characteristics
                if (uuid.contains("vol") || uuid.contains("audio")) {
                    if (data.isNotEmpty()) {
                        // Try different interpretations of the data
                        val level1 = data[0].toInt() and 0xFF
                        LogManager.log("BTVolume", "Potential volume level notification (first byte): $level1", LogManager.LogLevel.INFO)
                        
                        // If there are at least 2 bytes, try 16-bit interpretation
                        if (data.size >= 2) {
                            val level2 = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
                            LogManager.log("BTVolume", "Potential volume level notification (16-bit LE): $level2", LogManager.LogLevel.INFO)
                            
                            val level3 = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                            LogManager.log("BTVolume", "Potential volume level notification (16-bit BE): $level3", LogManager.LogLevel.INFO)
                        }
                        
                        // Use the first byte as the volume level
                        volumeLevel = level1
                    } else {
                        LogManager.log("BTVolume", "Volume-related characteristic notification returned empty data", LogManager.LogLevel.WARNING)
                    }
                } 
                // Battery-related characteristics
                else if (uuid.contains("batt") || (uuid.contains("level") && !uuid.contains("vol"))) {
                    if (data.isNotEmpty()) {
                        // Try different interpretations of the data
                        val level1 = data[0].toInt() and 0xFF
                        
                        // If there are at least 2 bytes, try 16-bit interpretation
                        if (data.size >= 2) {
                            val level2 = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
                            LogManager.log("BTBattery", "Potential battery level notification (16-bit LE): $level2", LogManager.LogLevel.INFO)
                            
                            val level3 = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                            LogManager.log("BTBattery", "Potential battery level notification (16-bit BE): $level3", LogManager.LogLevel.INFO)
                            
                            // If the 16-bit value seems reasonable (0-100), use it
                            if (level2 in 0..100) {
                                LogManager.log("BTBattery", "Using 16-bit LE value as battery level notification: $level2%", LogManager.LogLevel.INFO)
                                batteryLevel = level2
                                return
                            } else if (level3 in 0..100) {
                                LogManager.log("BTBattery", "Using 16-bit BE value as battery level notification: $level3%", LogManager.LogLevel.INFO)
                                batteryLevel = level3
                                return
                            }
                        }
                        
                        // If the first byte seems reasonable (0-100), use it
                        if (level1 in 0..100) {
                            LogManager.log("BTBattery", "Potential battery level notification (first byte): $level1%", LogManager.LogLevel.INFO)
                            batteryLevel = level1
                        } else {
                            LogManager.log("BTBattery", "First byte value $level1 outside expected battery range (0-100)", LogManager.LogLevel.WARNING)
                            
                            // Try to interpret as a percentage of 255
                            val percentOf255 = (level1 * 100) / 255
                            if (percentOf255 in 0..100) {
                                LogManager.log("BTBattery", "Interpreting notification as percentage of 255: $percentOf255%", LogManager.LogLevel.INFO)
                                batteryLevel = percentOf255
                            }
                        }
                    } else {
                        LogManager.log("BTBattery", "Battery-related characteristic notification returned empty data", LogManager.LogLevel.WARNING)
                    }
                }
            }
        }
        
        // Connect to the device
        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        } catch (e: Exception) {
            LogManager.log("BTConnect", "Error connecting to device: ${e.message}", LogManager.LogLevel.ERROR)
            connectingDeviceAddress = null
            connectionCallback(false, "Error: ${e.message}")
        }
    }
    
    fun disconnectFromDevice() {
        // First try to disconnect from GATT if connected
        bluetoothGatt?.let { gatt ->
            try {
                LogManager.log("BTConnect", "Disconnecting from GATT device: ${gatt.device.address}")
                gatt.disconnect()
                gatt.close()
            } catch (e: Exception) {
                LogManager.log("BTConnect", "Error disconnecting from GATT: ${e.message}", LogManager.LogLevel.ERROR)
            } finally {
                bluetoothGatt = null
            }
        }
        
        // If we have a connected device address, try to disconnect from A2DP profile as well
        val deviceAddress = connectedDeviceAddress
        if (deviceAddress != null) {
            try {
                LogManager.log("BTConnect", "Attempting to disconnect from A2DP profile for device: $deviceAddress", LogManager.LogLevel.INFO)
                
                // This requires reflection since the API is not directly accessible
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                if (bluetoothAdapter != null) {
                    // Get the A2DP proxy class
                    val a2dpClass = Class.forName("android.bluetooth.BluetoothA2dp")
                    
                    // Get the disconnect method
                    val disconnectMethod = a2dpClass.getMethod("disconnect", BluetoothDevice::class.java)
                    
                    // Find the device by address
                    val pairedDevices = bluetoothAdapter.bondedDevices
                    val device = pairedDevices.find { it.address == deviceAddress }
                    
                    if (device != null) {
                        // Get the A2DP proxy
                        val getProfileProxyMethod = BluetoothAdapter::class.java.getMethod(
                            "getProfileProxy",
                            Context::class.java,
                            Class.forName("android.bluetooth.BluetoothProfile\$ServiceListener"),
                            Int::class.java
                        )
                        
                        // Create a service listener
                        val serviceListener = object : Any() {
                            fun onServiceConnected(profile: Int, proxy: Any) {
                                LogManager.log("BTConnect", "A2DP service connected for disconnection", LogManager.LogLevel.INFO)
                                
                                try {
                                    // Disconnect from the device using the A2DP proxy
                                    val result = disconnectMethod.invoke(proxy, device) as Boolean
                                    
                                    if (result) {
                                        LogManager.log("BTConnect", "A2DP disconnection initiated successfully", LogManager.LogLevel.INFO)
                                    } else {
                                        LogManager.log("BTConnect", "A2DP disconnection failed to initiate", LogManager.LogLevel.ERROR)
                                    }
                                } catch (e: Exception) {
                                    LogManager.log("BTConnect", "Error disconnecting from A2DP: ${e.message}", LogManager.LogLevel.ERROR)
                                }
                            }
                            
                            fun onServiceDisconnected(profile: Int) {
                                LogManager.log("BTConnect", "A2DP service disconnected", LogManager.LogLevel.INFO)
                            }
                        }
                        
                        // Get the A2DP profile proxy
                        getProfileProxyMethod.invoke(
                            bluetoothAdapter,
                            null, // We don't have a context here, but it might still work
                            serviceListener,
                            BluetoothProfile.A2DP
                        )
                    } else {
                        LogManager.log("BTConnect", "Could not find device with address $deviceAddress in bonded devices", LogManager.LogLevel.WARNING)
                    }
                }
            } catch (e: Exception) {
                LogManager.log("BTConnect", "Error disconnecting from A2DP: ${e.message}", LogManager.LogLevel.ERROR)
                e.printStackTrace()
            }
        }
        
        // Reset all connection state variables
        connectedDeviceAddress = null
        connectingDeviceAddress = null
        volumeLevel = null
        batteryLevel = null
        
        LogManager.log("BTConnect", "Disconnection complete", LogManager.LogLevel.INFO)
    }
    
    /**
     * Query the volume level from the connected device
     * This method will search for and read from any characteristic that might contain volume information
     */
    fun queryVolumeLevel() {
        bluetoothGatt?.let { gatt ->
            try {
                val deviceName = gatt.device.name ?: gatt.device.address
                LogManager.log("BTVolume", "Querying volume level from $deviceName", LogManager.LogLevel.INFO)
                
                // Log all services to help with debugging
                LogManager.log("BTVolume", "Device has ${gatt.services.size} services")
                
                // First try the standard Volume Control service
                var volumeService = gatt.getService(VOLUME_CONTROL_SERVICE_UUID)
                var volumeCharacteristic = volumeService?.getCharacteristic(VOLUME_STATE_CHARACTERISTIC_UUID)
                
                if (volumeCharacteristic != null) {
                    LogManager.log("BTVolume", "Found standard volume state characteristic: ${volumeCharacteristic.uuid}")
                    val success = gatt.readCharacteristic(volumeCharacteristic)
                    LogManager.log("BTVolume", "Read request sent: $success")
                    return
                }
                
                // If standard service not found, try to find any characteristic that might be related to volume
                LogManager.log("BTVolume", "Standard volume service not found, searching for volume-related characteristics")
                
                // Keep track of potential volume characteristics for logging
                val potentialCharacteristics = mutableListOf<String>()
                
                for (service in gatt.services) {
                    LogManager.log("BTVolume", "Checking service: ${service.uuid}")
                    for (characteristic in service.characteristics) {
                        val uuid = characteristic.uuid.toString().lowercase()
                        val properties = characteristic.properties
                        
                        // Check if this characteristic might be related to volume
                        if (uuid.contains("vol") || uuid.contains("audio") || uuid.contains("level")) {
                            potentialCharacteristics.add("${characteristic.uuid} (Properties: $properties)")
                            
                            // Only try to read if the characteristic has the READ property
                            if ((properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                                LogManager.log("BTVolume", "Reading potential volume characteristic: ${characteristic.uuid}")
                                val success = gatt.readCharacteristic(characteristic)
                                LogManager.log("BTVolume", "Read request sent: $success")
                                return
                            } else {
                                LogManager.log("BTVolume", "Characteristic ${characteristic.uuid} doesn't have READ property")
                            }
                        }
                    }
                }
                
                if (potentialCharacteristics.isEmpty()) {
                    LogManager.log("BTVolume", "No volume-related characteristics found", LogManager.LogLevel.WARNING)
                } else {
                    LogManager.log("BTVolume", "Found ${potentialCharacteristics.size} potential volume characteristics, but none could be read:", LogManager.LogLevel.WARNING)
                    potentialCharacteristics.forEach { 
                        LogManager.log("BTVolume", "  $it", LogManager.LogLevel.WARNING)
                    }
                }
            } catch (e: Exception) {
                LogManager.log("BTVolume", "Error querying volume level: ${e.message}", LogManager.LogLevel.ERROR)
                e.printStackTrace()
            }
        } ?: LogManager.log("BTVolume", "Not connected to any device", LogManager.LogLevel.WARNING)
    }
    
    /**
     * Retry a failed connection
     */
    private fun retryConnection() {
        val device = deviceToRetry
        val context = retryContext
        val callback = retryCallback
        
        if (device != null && context != null && callback != null) {
            LogManager.log("BTConnect", "Retrying connection to ${device.address}...", LogManager.LogLevel.INFO)
            connectToDevice(context, device, callback)
        } else {
            LogManager.log("BTConnect", "Cannot retry connection: missing device, context, or callback", LogManager.LogLevel.ERROR)
            connectionRetryCount = 0
        }
    }
    
    /**
     * Connect to a device using GATT (for BLE devices)
     */
    private fun connectUsingGatt(context: Context, device: BluetoothDevice, connectionCallback: (Boolean, String) -> Unit) {
        LogManager.log("BTConnect", "Connecting to device using GATT: ${device.address}", LogManager.LogLevel.INFO)
        
        // Create GATT callback
        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                val deviceAddress = gatt.device.address
                
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        LogManager.log("BTConnect", "Successfully connected to $deviceAddress")
                        
                        // Reset retry parameters on successful connection
                        connectionRetryCount = 0
                        deviceToRetry = null
                        retryContext = null
                        retryCallback = null
                        
                        connectionCallback(true, "Connected to device")
                        
                        // Reset volume and battery levels
                        volumeLevel = null
                        batteryLevel = null
                        
                        // Discover services
                        LogManager.log("BTConnect", "Discovering services...")
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        LogManager.log("BTConnect", "Disconnected from $deviceAddress")
                        gatt.close()
                        bluetoothGatt = null
                        connectedDeviceAddress = null
                        
                        // Reset volume and battery levels
                        volumeLevel = null
                        batteryLevel = null
                        
                        connectionCallback(false, "Disconnected from device")
                    }
                } else {
                    // Handle connection error with specific error messages
                    val errorMessage = when (status) {
                        133 -> "Connection timeout. Device might be out of range or turned off."
                        8 -> "Connection already exists. Try disconnecting first."
                        22 -> "Connection failed due to authentication issues."
                        62 -> "Connection failed due to insufficient resources."
                        else -> "Error connecting to device (code: $status)"
                    }
                    
                    LogManager.log("BTConnect", "Error connecting to $deviceAddress, status: $status - $errorMessage", LogManager.LogLevel.ERROR)
                    gatt.close()
                    bluetoothGatt = null
                    
                    // Set connecting state to null
                    connectingDeviceAddress = null
                    
                    // Check if we should retry the connection
                    if (status == 133 && connectionRetryCount < MAX_CONNECTION_RETRIES) {
                        // Store retry information
                        deviceToRetry = gatt.device
                        retryContext = context
                        retryCallback = connectionCallback
                        
                        // Increment retry count
                        connectionRetryCount++
                        
                        // Log retry attempt
                        LogManager.log("BTConnect", "Connection failed with timeout. Retrying ($connectionRetryCount/$MAX_CONNECTION_RETRIES) in 2 seconds...", LogManager.LogLevel.WARNING)
                        
                        // Notify user of retry
                        connectionCallback(false, "Connection timeout. Retrying in 2 seconds... (Attempt $connectionRetryCount/$MAX_CONNECTION_RETRIES)")
                        
                        // Schedule retry after delay
                        Handler(Looper.getMainLooper()).postDelayed({
                            retryConnection()
                        }, 2000) // 2 second delay
                    } else {
                        // Reset retry count
                        connectionRetryCount = 0
                        deviceToRetry = null
                        retryContext = null
                        retryCallback = null
                        
                        // Notify of failure
                        connectionCallback(false, errorMessage)
                    }
                }
            }
            
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    LogManager.log("BTConnect", "Services discovered")
                    
                    // Set connected state
                    connectingDeviceAddress = null
                    connectedDeviceAddress = gatt.device.address
                    
                    // Log all services and characteristics
                    val services = gatt.services
                    LogManager.log("BTData", "Found ${services.size} services")
                    
                    // Log detailed information about all services and characteristics
                    logAllServicesAndCharacteristics(gatt)
                    
                    // After services are discovered, query volume and battery levels
                    LogManager.log("BTData", "Querying volume and battery levels after service discovery", LogManager.LogLevel.INFO)
                    queryVolumeLevel()
                    queryBatteryLevel()
                    
                    services.forEach { service ->
                        val serviceUuid = service.uuid
                        LogManager.log("BTData", "Service: $serviceUuid")
                        
                        // Get characteristics for this service
                        val characteristics = service.characteristics
                        LogManager.log("BTData", "  Found ${characteristics.size} characteristics")
                        
                        characteristics.forEach { characteristic ->
                            val charUuid = characteristic.uuid
                            val properties = characteristic.properties
                            LogManager.log("BTData", "  Characteristic: $charUuid, Properties: $properties")
                            
                            // Check if this characteristic has notify property
                            if ((properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                                LogManager.log("BTData", "  Enabling notifications for $charUuid")
                                
                                // Enable notifications for this characteristic
                                val success = gatt.setCharacteristicNotification(characteristic, true)
                                LogManager.log("BTData", "  Notification enabled: $success")
                                
                                // Get the descriptor for enabling notifications
                                val descriptor = characteristic.getDescriptor(
                                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") // Client Characteristic Configuration Descriptor
                                )
                                
                                if (descriptor != null) {
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    gatt.writeDescriptor(descriptor)
                                    LogManager.log("BTData", "  Notification descriptor written")
                                }
                            }
                            
                            // Check if this characteristic has read property
                            if ((properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                                LogManager.log("BTData", "  Reading characteristic $charUuid")
                                gatt.readCharacteristic(characteristic)
                            }
                        }
                    }
                } else {
                    Log.e("BTConnect", "Service discovery failed: $status")
                }
            }
            
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                super.onCharacteristicRead(gatt, characteristic, value, status)
                
                // Handle characteristic read
                handleCharacteristicRead(gatt, characteristic, value, status)
            }
            
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                super.onCharacteristicChanged(gatt, characteristic, value)
                
                // Handle characteristic change
                handleCharacteristicChanged(gatt, characteristic, value)
            }
        }
        
        // Connect to the device
        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            if (bluetoothGatt == null) {
                LogManager.log("BTConnect", "Failed to create GATT connection", LogManager.LogLevel.ERROR)
                connectingDeviceAddress = null
                connectionCallback(false, "Failed to create connection")
            }
        } catch (e: Exception) {
            LogManager.log("BTConnect", "Error connecting to device: ${e.message}", LogManager.LogLevel.ERROR)
            connectingDeviceAddress = null
            connectionCallback(false, "Error: ${e.message}")
        }
    }
    
    /**
     * Handle characteristic read events
     */
    private fun handleCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        val uuid = characteristic.uuid
        val uuidString = uuid.toString()
        
        // Determine if this is a known characteristic
        val charName = when(uuid) {
            BATTERY_LEVEL_CHARACTERISTIC_UUID -> "Battery Level"
            VOLUME_STATE_CHARACTERISTIC_UUID -> "Volume State"
            VOLUME_CONTROL_POINT_CHARACTERISTIC_UUID -> "Volume Control Point"
            VOLUME_FLAGS_CHARACTERISTIC_UUID -> "Volume Flags"
            else -> "Unknown"
        }
        
        if (status == BluetoothGatt.GATT_SUCCESS) {
            val data = value
            val hexString = bytesToHex(data)
            val utf8String = try {
                String(data, Charsets.UTF_8)
            } catch (e: Exception) {
                "Non-text data"
            }
            
            LogManager.log("BTData", "Read from $uuidString ($charName)", LogManager.LogLevel.INFO)
            LogManager.log("BTData", "Data (HEX): $hexString", LogManager.LogLevel.INFO)
            LogManager.log("BTData", "Data (UTF-8): $utf8String", LogManager.LogLevel.INFO)
            LogManager.log("BTData", "Data (Bytes): ${Arrays.toString(data)}", LogManager.LogLevel.INFO)
            
            // Log specific interpretations for debugging
            if (data.isNotEmpty()) {
                val firstByte = data[0].toInt() and 0xFF
                LogManager.log("BTData", "First byte as integer: $firstByte", LogManager.LogLevel.INFO)
                
                if (data.size >= 2) {
                    val value16bitLE = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
                    val value16bitBE = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                    LogManager.log("BTData", "As 16-bit LE: $value16bitLE", LogManager.LogLevel.INFO)
                    LogManager.log("BTData", "As 16-bit BE: $value16bitBE", LogManager.LogLevel.INFO)
                }
            }
            
            // Update the state variables
            lastReceivedData = utf8String
            lastReceivedDataHex = hexString
            lastCharacteristicUuid = characteristic.uuid.toString()
            
            // Check if this is a battery level characteristic
            if (characteristic.uuid == BATTERY_LEVEL_CHARACTERISTIC_UUID) {
                if (data.isNotEmpty()) {
                    val level = data[0].toInt() and 0xFF
                    LogManager.log("BTBattery", "Battery level from standard characteristic: $level%", LogManager.LogLevel.INFO)
                    LogManager.log("BTBattery", "Raw data: ${bytesToHex(data)}", LogManager.LogLevel.INFO)
                    batteryLevel = level
                } else {
                    LogManager.log("BTBattery", "Battery characteristic returned empty data", LogManager.LogLevel.WARNING)
                }
            }
            
            // Check if this is a volume state characteristic
            if (characteristic.uuid == VOLUME_STATE_CHARACTERISTIC_UUID) {
                if (data.isNotEmpty()) {
                    val level = data[0].toInt() and 0xFF
                    LogManager.log("BTVolume", "Volume level from standard characteristic: $level", LogManager.LogLevel.INFO)
                    LogManager.log("BTVolume", "Raw data: ${bytesToHex(data)}", LogManager.LogLevel.INFO)
                    volumeLevel = level
                } else {
                    LogManager.log("BTVolume", "Volume characteristic returned empty data", LogManager.LogLevel.WARNING)
                }
            }
            
            // Check for other potential volume or battery characteristics
            val uuid = characteristic.uuid.toString().lowercase()
            
            // Log all data for debugging
            LogManager.log("BTData", "Characteristic ${characteristic.uuid} data:", LogManager.LogLevel.INFO)
            LogManager.log("BTData", "  Hex: ${bytesToHex(data)}", LogManager.LogLevel.INFO)
            LogManager.log("BTData", "  Decimal: ${data.joinToString(", ") { (it.toInt() and 0xFF).toString() }}", LogManager.LogLevel.INFO)
            if (data.size >= 2) {
                // Try to interpret as 16-bit value (little endian)
                val value16bit = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
                LogManager.log("BTData", "  As 16-bit value (LE): $value16bit", LogManager.LogLevel.INFO)
                
                // Try to interpret as 16-bit value (big endian)
                val value16bitBE = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                LogManager.log("BTData", "  As 16-bit value (BE): $value16bitBE", LogManager.LogLevel.INFO)
            }
            
            // Volume-related characteristics
            if (uuid.contains("vol") || uuid.contains("audio")) {
                if (data.isNotEmpty()) {
                    // Try different interpretations of the data
                    val level1 = data[0].toInt() and 0xFF
                    LogManager.log("BTVolume", "Potential volume level (first byte): $level1", LogManager.LogLevel.INFO)
                    
                    // If there are at least 2 bytes, try 16-bit interpretation
                    if (data.size >= 2) {
                        val level2 = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
                        LogManager.log("BTVolume", "Potential volume level (16-bit LE): $level2", LogManager.LogLevel.INFO)
                        
                        val level3 = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                        LogManager.log("BTVolume", "Potential volume level (16-bit BE): $level3", LogManager.LogLevel.INFO)
                    }
                    
                    // Use the first byte as the volume level
                    volumeLevel = level1
                } else {
                    LogManager.log("BTVolume", "Volume-related characteristic returned empty data", LogManager.LogLevel.WARNING)
                }
            } 
            // Battery-related characteristics
            else if (uuid.contains("batt") || (uuid.contains("level") && !uuid.contains("vol"))) {
                if (data.isNotEmpty()) {
                    // Try different interpretations of the data
                    val level1 = data[0].toInt() and 0xFF
                    LogManager.log("BTBattery", "Potential battery level (first byte): $level1%", LogManager.LogLevel.INFO)
                    
                    // If there are at least 2 bytes, try 16-bit interpretation
                    if (data.size >= 2) {
                        val level2 = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
                        LogManager.log("BTBattery", "Potential battery level (16-bit LE): $level2%", LogManager.LogLevel.INFO)
                        
                        val level3 = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                        LogManager.log("BTBattery", "Potential battery level (16-bit BE): $level3%", LogManager.LogLevel.INFO)
                        
                        // If the 16-bit value seems reasonable (0-100), use it
                        if (level2 in 0..100) {
                            LogManager.log("BTBattery", "Using 16-bit LE value as battery level: $level2%", LogManager.LogLevel.INFO)
                            batteryLevel = level2
                            return
                        } else if (level3 in 0..100) {
                            LogManager.log("BTBattery", "Using 16-bit BE value as battery level: $level3%", LogManager.LogLevel.INFO)
                            batteryLevel = level3
                            return
                        }
                    }
                    
                    // If the first byte seems reasonable (0-100), use it
                    if (level1 in 0..100) {
                        batteryLevel = level1
                    } else {
                        LogManager.log("BTBattery", "First byte value $level1 outside expected battery range (0-100)", LogManager.LogLevel.WARNING)
                        
                        // Try to interpret as a percentage of 255
                        val percentOf255 = (level1 * 100) / 255
                        if (percentOf255 in 0..100) {
                            LogManager.log("BTBattery", "Interpreting as percentage of 255: $percentOf255%", LogManager.LogLevel.INFO)
                            batteryLevel = percentOf255
                        }
                    }
                } else {
                    LogManager.log("BTBattery", "Battery-related characteristic returned empty data", LogManager.LogLevel.WARNING)
                }
            }
        }
    }
    
    /**
     * Handle characteristic changed events
     */
    private fun handleCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        val uuid = characteristic.uuid
        val uuidString = uuid.toString()
        
        // Determine if this is a known characteristic
        val charName = when(uuid) {
            BATTERY_LEVEL_CHARACTERISTIC_UUID -> "Battery Level"
            VOLUME_STATE_CHARACTERISTIC_UUID -> "Volume State"
            VOLUME_CONTROL_POINT_CHARACTERISTIC_UUID -> "Volume Control Point"
            VOLUME_FLAGS_CHARACTERISTIC_UUID -> "Volume Flags"
            else -> "Unknown"
        }
        
        val data = value
        val hexString = bytesToHex(data)
        val utf8String = try {
            String(data, Charsets.UTF_8)
        } catch (e: Exception) {
            "Non-text data"
        }
        
        LogManager.log("BTData", "Notification from $uuidString ($charName)", LogManager.LogLevel.INFO)
        LogManager.log("BTData", "Data (HEX): $hexString", LogManager.LogLevel.INFO)
        LogManager.log("BTData", "Data (UTF-8): $utf8String", LogManager.LogLevel.INFO)
        LogManager.log("BTData", "Data (Bytes): ${Arrays.toString(data)}", LogManager.LogLevel.INFO)
        
        // Update the state variables
        lastReceivedData = utf8String
        lastReceivedDataHex = hexString
        lastCharacteristicUuid = characteristic.uuid.toString()
        
        // Check if this is a battery level characteristic
        if (characteristic.uuid == BATTERY_LEVEL_CHARACTERISTIC_UUID) {
            if (data.isNotEmpty()) {
                val level = data[0].toInt() and 0xFF
                LogManager.log("BTBattery", "Battery level notification from standard characteristic: $level%", LogManager.LogLevel.INFO)
                LogManager.log("BTBattery", "Raw data: ${bytesToHex(data)}", LogManager.LogLevel.INFO)
                batteryLevel = level
            } else {
                LogManager.log("BTBattery", "Battery characteristic notification returned empty data", LogManager.LogLevel.WARNING)
            }
        }
        
        // Check if this is a volume state characteristic
        if (characteristic.uuid == VOLUME_STATE_CHARACTERISTIC_UUID) {
            if (data.isNotEmpty()) {
                val level = data[0].toInt() and 0xFF
                LogManager.log("BTVolume", "Volume level notification from standard characteristic: $level", LogManager.LogLevel.INFO)
                LogManager.log("BTVolume", "Raw data: ${bytesToHex(data)}", LogManager.LogLevel.INFO)
                volumeLevel = level
            } else {
                LogManager.log("BTVolume", "Volume characteristic notification returned empty data", LogManager.LogLevel.WARNING)
            }
        }
        
        // Check for other potential volume or battery characteristics
        val uuid = characteristic.uuid.toString().lowercase()
        
        // Volume-related characteristics
        if (uuid.contains("vol") || uuid.contains("audio")) {
            if (data.isNotEmpty()) {
                // Try different interpretations of the data
                val level1 = data[0].toInt() and 0xFF
                LogManager.log("BTVolume", "Potential volume level notification (first byte): $level1", LogManager.LogLevel.INFO)
                
                // If there are at least 2 bytes, try 16-bit interpretation
                if (data.size >= 2) {
                    val level2 = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
                    LogManager.log("BTVolume", "Potential volume level notification (16-bit LE): $level2", LogManager.LogLevel.INFO)
                    
                    val level3 = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                    LogManager.log("BTVolume", "Potential volume level notification (16-bit BE): $level3", LogManager.LogLevel.INFO)
                }
                
                // Use the first byte as the volume level
                volumeLevel = level1
            } else {
                LogManager.log("BTVolume", "Volume-related characteristic notification returned empty data", LogManager.LogLevel.WARNING)
            }
        } 
        // Battery-related characteristics
        else if (uuid.contains("batt") || (uuid.contains("level") && !uuid.contains("vol"))) {
            if (data.isNotEmpty()) {
                // Try different interpretations of the data
                val level1 = data[0].toInt() and 0xFF
                
                // If there are at least 2 bytes, try 16-bit interpretation
                if (data.size >= 2) {
                    val level2 = ((data[1].toInt() and 0xFF) shl 8) or (data[0].toInt() and 0xFF)
                    LogManager.log("BTBattery", "Potential battery level notification (16-bit LE): $level2", LogManager.LogLevel.INFO)
                    
                    val level3 = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                    LogManager.log("BTBattery", "Potential battery level notification (16-bit BE): $level3", LogManager.LogLevel.INFO)
                    
                    // If the 16-bit value seems reasonable (0-100), use it
                    if (level2 in 0..100) {
                        LogManager.log("BTBattery", "Using 16-bit LE value as battery level notification: $level2%", LogManager.LogLevel.INFO)
                        batteryLevel = level2
                        return
                    } else if (level3 in 0..100) {
                        LogManager.log("BTBattery", "Using 16-bit BE value as battery level notification: $level3%", LogManager.LogLevel.INFO)
                        batteryLevel = level3
                        return
                    }
                }
                
                // If the first byte seems reasonable (0-100), use it
                if (level1 in 0..100) {
                    LogManager.log("BTBattery", "Potential battery level notification (first byte): $level1%", LogManager.LogLevel.INFO)
                    batteryLevel = level1
                } else {
                    LogManager.log("BTBattery", "First byte value $level1 outside expected battery range (0-100)", LogManager.LogLevel.WARNING)
                    
                    // Try to interpret as a percentage of 255
                    val percentOf255 = (level1 * 100) / 255
                    if (percentOf255 in 0..100) {
                        LogManager.log("BTBattery", "Interpreting notification as percentage of 255: $percentOf255%", LogManager.LogLevel.INFO)
                        batteryLevel = percentOf255
                    }
                }
            } else {
                LogManager.log("BTBattery", "Battery-related characteristic notification returned empty data", LogManager.LogLevel.WARNING)
            }
        }
    }
    
    /**
     * Query the battery level from the connected device
     * This method will search for and read from any characteristic that might contain battery information
     */
    fun queryBatteryLevel() {
        bluetoothGatt?.let { gatt ->
            try {
                val deviceName = gatt.device.name ?: gatt.device.address
                LogManager.log("BTBattery", "Querying battery level from $deviceName", LogManager.LogLevel.INFO)
                
                // Log all services to help with debugging
                LogManager.log("BTBattery", "Device has ${gatt.services.size} services")
                
                // First try the standard Battery service
                val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
                val batteryCharacteristic = batteryService?.getCharacteristic(BATTERY_LEVEL_CHARACTERISTIC_UUID)
                
                if (batteryCharacteristic != null) {
                    LogManager.log("BTBattery", "Found standard battery level characteristic: ${batteryCharacteristic.uuid}")
                    val success = gatt.readCharacteristic(batteryCharacteristic)
                    LogManager.log("BTBattery", "Read request sent: $success")
                    return
                }
                
                // If standard service not found, try to find any characteristic that might be related to battery
                LogManager.log("BTBattery", "Standard battery service not found, searching for battery-related characteristics")
                
                // Keep track of potential battery characteristics for logging
                val potentialCharacteristics = mutableListOf<String>()
                
                for (service in gatt.services) {
                    LogManager.log("BTBattery", "Checking service: ${service.uuid}")
                    for (characteristic in service.characteristics) {
                        val uuid = characteristic.uuid.toString().lowercase()
                        val properties = characteristic.properties
                        
                        // Check if this characteristic might be related to battery
                        if (uuid.contains("batt") || (uuid.contains("level") && !uuid.contains("vol"))) {
                            potentialCharacteristics.add("${characteristic.uuid} (Properties: $properties)")
                            
                            // Only try to read if the characteristic has the READ property
                            if ((properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                                LogManager.log("BTBattery", "Reading potential battery characteristic: ${characteristic.uuid}")
                                val success = gatt.readCharacteristic(characteristic)
                                LogManager.log("BTBattery", "Read request sent: $success")
                                return
                            } else {
                                LogManager.log("BTBattery", "Characteristic ${characteristic.uuid} doesn't have READ property")
                            }
                        }
                    }
                }
                
                if (potentialCharacteristics.isEmpty()) {
                    LogManager.log("BTBattery", "No battery-related characteristics found", LogManager.LogLevel.WARNING)
                } else {
                    LogManager.log("BTBattery", "Found ${potentialCharacteristics.size} potential battery characteristics, but none could be read:", LogManager.LogLevel.WARNING)
                    potentialCharacteristics.forEach { 
                        LogManager.log("BTBattery", "  $it", LogManager.LogLevel.WARNING)
                    }
                }
                
                // As a last resort, try to find any characteristic with "level" in the name
                LogManager.log("BTBattery", "Searching for any characteristic with 'level' in the name")
                for (service in gatt.services) {
                    for (characteristic in service.characteristics) {
                        val uuid = characteristic.uuid.toString().lowercase()
                        val properties = characteristic.properties
                        
                        if (uuid.contains("level") && (properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                            LogManager.log("BTBattery", "Reading generic level characteristic: ${characteristic.uuid}")
                            val success = gatt.readCharacteristic(characteristic)
                            LogManager.log("BTBattery", "Read request sent: $success")
                            return
                        }
                    }
                }
                
                LogManager.log("BTBattery", "No suitable battery level characteristics found", LogManager.LogLevel.WARNING)
            } catch (e: Exception) {
                LogManager.log("BTBattery", "Error querying battery level: ${e.message}", LogManager.LogLevel.ERROR)
                e.printStackTrace()
            }
        } ?: LogManager.log("BTBattery", "Not connected to any device", LogManager.LogLevel.WARNING)
    }
    
    override fun onCleared() {
        super.onCleared()
        // Make sure to disconnect when ViewModel is cleared
        disconnectFromDevice()
        LogManager.log("BTScan", "ViewModel cleared")
    }
    
    /**
     * Helper function to convert a byte array to a hex string
     */
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(separator = " ") { 
            String.format("%02X", it) 
        }
    }
    
    /**
     * Log detailed information about all services and characteristics
     */
    private fun logAllServicesAndCharacteristics(gatt: BluetoothGatt) {
        LogManager.log("BTDebug", "=== DETAILED BLUETOOTH DEVICE INFORMATION ===", LogManager.LogLevel.INFO)
        LogManager.log("BTDebug", "Device: ${gatt.device.name ?: "Unknown"} (${gatt.device.address})", LogManager.LogLevel.INFO)
        
        val services = gatt.services
        LogManager.log("BTDebug", "Total services: ${services.size}", LogManager.LogLevel.INFO)
        
        services.forEachIndexed { serviceIndex, service ->
            LogManager.log("BTDebug", "Service ${serviceIndex + 1}: ${service.uuid}", LogManager.LogLevel.INFO)
            
            // Check if this is a known service
            val serviceName = when(service.uuid) {
                BATTERY_SERVICE_UUID -> "Battery Service"
                AUDIO_SERVICE_UUID -> "Audio Service"
                VOLUME_CONTROL_SERVICE_UUID -> "Volume Control Service"
                else -> "Unknown Service"
            }
            LogManager.log("BTDebug", "  Name: $serviceName", LogManager.LogLevel.INFO)
            
            // Log characteristics
            val characteristics = service.characteristics
            LogManager.log("BTDebug", "  Characteristics: ${characteristics.size}", LogManager.LogLevel.INFO)
            
            characteristics.forEachIndexed { charIndex, characteristic ->
                LogManager.log("BTDebug", "  Characteristic ${charIndex + 1}: ${characteristic.uuid}", LogManager.LogLevel.INFO)
                
                // Check if this is a known characteristic
                val charName = when(characteristic.uuid) {
                    BATTERY_LEVEL_CHARACTERISTIC_UUID -> "Battery Level"
                    VOLUME_STATE_CHARACTERISTIC_UUID -> "Volume State"
                    VOLUME_CONTROL_POINT_CHARACTERISTIC_UUID -> "Volume Control Point"
                    VOLUME_FLAGS_CHARACTERISTIC_UUID -> "Volume Flags"
                    else -> "Unknown Characteristic"
                }
                LogManager.log("BTDebug", "    Name: $charName", LogManager.LogLevel.INFO)
                
                // Log properties
                val properties = characteristic.properties
                val propList = mutableListOf<String>()
                if ((properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) propList.add("READ")
                if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) propList.add("WRITE")
                if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) propList.add("WRITE_NO_RESPONSE")
                if ((properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) propList.add("NOTIFY")
                if ((properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) propList.add("INDICATE")
                
                LogManager.log("BTDebug", "    Properties: ${propList.joinToString(", ")}", LogManager.LogLevel.INFO)
                
                // Log descriptors
                val descriptors = characteristic.descriptors
                if (descriptors.isNotEmpty()) {
                    LogManager.log("BTDebug", "    Descriptors: ${descriptors.size}", LogManager.LogLevel.INFO)
                    descriptors.forEachIndexed { descIndex, descriptor ->
                        LogManager.log("BTDebug", "      Descriptor ${descIndex + 1}: ${descriptor.uuid}", LogManager.LogLevel.INFO)
                    }
                }
                
                // If this characteristic is readable, try to read it
                if ((properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                    LogManager.log("BTDebug", "    Attempting to read characteristic...", LogManager.LogLevel.INFO)
                    gatt.readCharacteristic(characteristic)
                }
            }
        }
        
        LogManager.log("BTDebug", "=== END OF DEVICE INFORMATION ===", LogManager.LogLevel.INFO)
    }
    
    /**
     * Connect to a device using GATT (for BLE devices)
     */
    private fun connectUsingGatt(context: Context, device: BluetoothDevice, connectionCallback: (Boolean, String) -> Unit) {
        LogManager.log("BTConnect", "Connecting to device using GATT: ${device.address}", LogManager.LogLevel.INFO)
        
        // Create GATT callback
        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                val deviceAddress = gatt.device.address
                
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        LogManager.log("BTConnect", "Successfully connected to $deviceAddress")
                        
                        // Reset retry parameters on successful connection
                        connectionRetryCount = 0
                        deviceToRetry = null
                        retryContext = null
                        retryCallback = null
                        
                        connectionCallback(true, "Connected to device")
                        
                        // Reset volume and battery levels
                        volumeLevel = null
                        batteryLevel = null
                        
                        // Discover services
                        LogManager.log("BTConnect", "Discovering services...")
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        LogManager.log("BTConnect", "Disconnected from $deviceAddress")
                        gatt.close()
                        bluetoothGatt = null
                        connectedDeviceAddress = null
                        
                        // Reset volume and battery levels
                        volumeLevel = null
                        batteryLevel = null
                        
                        connectionCallback(false, "Disconnected from device")
                    }
                } else {
                    // Handle connection error with specific error messages
                    val errorMessage = when (status) {
                        133 -> "Connection timeout. Device might be out of range or turned off."
                        8 -> "Connection already exists. Try disconnecting first."
                        22 -> "Connection failed due to authentication issues."
                        62 -> "Connection failed due to insufficient resources."
                        else -> "Error connecting to device (code: $status)"
                    }
                    
                    LogManager.log("BTConnect", "Error connecting to $deviceAddress, status: $status - $errorMessage", LogManager.LogLevel.ERROR)
                    gatt.close()
                    bluetoothGatt = null
                    
                    // Set connecting state to null
                    connectingDeviceAddress = null
                    
                    // Check if we should retry the connection
                    if (status == 133 && connectionRetryCount < MAX_CONNECTION_RETRIES) {
                        // Store retry information
                        deviceToRetry = gatt.device
                        retryContext = context
                        retryCallback = connectionCallback
                        
                        // Increment retry count
                        connectionRetryCount++
                        
                        // Log retry attempt
                        LogManager.log("BTConnect", "Connection failed with timeout. Retrying ($connectionRetryCount/$MAX_CONNECTION_RETRIES) in 2 seconds...", LogManager.LogLevel.WARNING)
                        
                        // Notify user of retry
                        connectionCallback(false, "Connection timeout. Retrying in 2 seconds... (Attempt $connectionRetryCount/$MAX_CONNECTION_RETRIES)")
                        
                        // Schedule retry after delay
                        Handler(Looper.getMainLooper()).postDelayed({
                            retryConnection()
                        }, 2000) // 2 second delay
                    } else {
                        // Reset retry count
                        connectionRetryCount = 0
                        deviceToRetry = null
                        retryContext = null
                        retryCallback = null
                        
                        // Notify of failure
                        connectionCallback(false, errorMessage)
                    }
                }
            }
            
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    LogManager.log("BTConnect", "Services discovered")
                    
                    // Set connected state
                    connectingDeviceAddress = null
                    connectedDeviceAddress = gatt.device.address
                    
                    // Log all services and characteristics
                    val services = gatt.services
                    LogManager.log("BTData", "Found ${services.size} services")
                    
                    // Log detailed information about all services and characteristics
                    logAllServicesAndCharacteristics(gatt)
                    
                    // After services are discovered, query volume and battery levels
                    LogManager.log("BTData", "Querying volume and battery levels after service discovery", LogManager.LogLevel.INFO)
                    queryVolumeLevel()
                    queryBatteryLevel()
                    
                    services.forEach { service ->
                        val serviceUuid = service.uuid
                        LogManager.log("BTData", "Service: $serviceUuid")
                        
                        // Get characteristics for this service
                        val characteristics = service.characteristics
                        LogManager.log("BTData", "  Found ${characteristics.size} characteristics")
                        
                        characteristics.forEach { characteristic ->
                            val charUuid = characteristic.uuid
                            val properties = characteristic.properties
                            LogManager.log("BTData", "  Characteristic: $charUuid, Properties: $properties")
                            
                            // Check if this characteristic has notify property
                            if ((properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                                LogManager.log("BTData", "  Enabling notifications for $charUuid")
                                
                                // Enable notifications for this characteristic
                                val success = gatt.setCharacteristicNotification(characteristic, true)
                                LogManager.log("BTData", "  Notification enabled: $success")
                                
                                // Get the descriptor for enabling notifications
                                val descriptor = characteristic.getDescriptor(
                                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") // Client Characteristic Configuration Descriptor
                                )
                                
                                if (descriptor != null) {
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    gatt.writeDescriptor(descriptor)
                                    LogManager.log("BTData", "  Notification descriptor written")
                                }
                            }
                            
                            // Check if this characteristic has read property
                            if ((properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                                LogManager.log("BTData", "  Reading characteristic $charUuid")
                                gatt.readCharacteristic(characteristic)
                            }
                        }
                    }
                } else {
                    Log.e("BTConnect", "Service discovery failed: $status")
                }
            }
            
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                super.onCharacteristicRead(gatt, characteristic, value, status)
                
                // Handle characteristic read
                handleCharacteristicRead(gatt, characteristic, value, status)
            }
            
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                super.onCharacteristicChanged(gatt, characteristic, value)
                
                // Handle characteristic change
                handleCharacteristicChanged(gatt, characteristic, value)
            }
        }
        
        // Connect to the device
        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            if (bluetoothGatt == null) {
                LogManager.log("BTConnect", "Failed to create GATT connection", LogManager.LogLevel.ERROR)
                connectingDeviceAddress = null
                connectionCallback(false, "Failed to create connection")
            }
        } catch (e: Exception) {
            LogManager.log("BTConnect", "Error connecting to device: ${e.message}", LogManager.LogLevel.ERROR)
            connectingDeviceAddress = null
            connectionCallback(false, "Error: ${e.message}")
        }
    }
    
    /**
     * Handle characteristic read events
     */
    private fun handleCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        val uuid = characteristic.uuid
        val uuidString = uuid.toString()
        
        // Determine if this is a known characteristic
        val charName = when(uuid) {
            BATTERY_LEVEL_CHARACTERISTIC_UUID -> "Battery Level"
            VOLUME_STATE_CHARACTERISTIC_UUID -> "Volume State"
            VOLUME_CONTROL_POINT_CHARACTERISTIC_UUID -> "Volume Control Point"
            VOLUME_FLAGS_CHARACTERISTIC_UUID -> "Volume Flags"
            else -> "Unknown"
        }
        
        if (status == BluetoothGatt.GATT_SUCCESS) {
            val data = value
            val hexString = bytesToHex(data)
            val utf8String = try {
                String(data, Charsets.UTF_8)
            } catch (e: Exception) {
                "Non-text data"
            }
            
            LogManager.log("BTData", "Read from $uuidString ($charName)", LogManager.LogLevel.INFO)
            LogManager.log("BTData", "Data (HEX): $hexString", LogManager.LogLevel.INFO)
            LogManager.log("BTData", "Data (UTF-8): $utf8String", LogManager.LogLevel.INFO)
            LogManager.log("BTData", "Data (Bytes): ${Arrays.toString(data)}", LogManager.LogLevel.INFO)
            
            // Update the state variables
            lastReceivedData = utf8String
            lastReceivedDataHex = hexString
            lastCharacteristicUuid = characteristic.uuid.toString()
            
            // Check if this is a battery level characteristic
            if (characteristic.uuid == BATTERY_LEVEL_CHARACTERISTIC_UUID) {
                if (data.isNotEmpty()) {
                    val level = data[0].toInt() and 0xFF
                    LogManager.log("BTBattery", "Battery level from standard characteristic: $level%", LogManager.LogLevel.INFO)
                    batteryLevel = level
                }
            }
            
            // Check if this is a volume state characteristic
            if (characteristic.uuid == VOLUME_STATE_CHARACTERISTIC_UUID) {
                if (data.isNotEmpty()) {
                    val level = data[0].toInt() and 0xFF
                    LogManager.log("BTVolume", "Volume level from standard characteristic: $level", LogManager.LogLevel.INFO)
                    volumeLevel = level
                }
            }
        }
    }
    
    /**
     * Handle characteristic changed events
     */
    private fun handleCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        val uuid = characteristic.uuid
        val uuidString = uuid.toString()
        
        // Determine if this is a known characteristic
        val charName = when(uuid) {
            BATTERY_LEVEL_CHARACTERISTIC_UUID -> "Battery Level"
            VOLUME_STATE_CHARACTERISTIC_UUID -> "Volume State"
            VOLUME_CONTROL_POINT_CHARACTERISTIC_UUID -> "Volume Control Point"
            VOLUME_FLAGS_CHARACTERISTIC_UUID -> "Volume Flags"
            else -> "Unknown"
        }
        
        val data = value
        val hexString = bytesToHex(data)
        val utf8String = try {
            String(data, Charsets.UTF_8)
        } catch (e: Exception) {
            "Non-text data"
        }
        
        LogManager.log("BTData", "Notification from $uuidString ($charName)", LogManager.LogLevel.INFO)
        LogManager.log("BTData", "Data (HEX): $hexString", LogManager.LogLevel.INFO)
        LogManager.log("BTData", "Data (UTF-8): $utf8String", LogManager.LogLevel.INFO)
        LogManager.log("BTData", "Data (Bytes): ${Arrays.toString(data)}", LogManager.LogLevel.INFO)
        
        // Update the state variables
        lastReceivedData = utf8String
        lastReceivedDataHex = hexString
        lastCharacteristicUuid = characteristic.uuid.toString()
        
        // Check if this is a battery level characteristic
        if (characteristic.uuid == BATTERY_LEVEL_CHARACTERISTIC_UUID) {
            if (data.isNotEmpty()) {
                val level = data[0].toInt() and 0xFF
                LogManager.log("BTBattery", "Battery level notification: $level%", LogManager.LogLevel.INFO)
                batteryLevel = level
            }
        }
        
        // Check if this is a volume state characteristic
        if (characteristic.uuid == VOLUME_STATE_CHARACTERISTIC_UUID) {
            if (data.isNotEmpty()) {
                val level = data[0].toInt() and 0xFF
                LogManager.log("BTVolume", "Volume level notification: $level", LogManager.LogLevel.INFO)
                volumeLevel = level
            }
        }
    }
    
    /**
     * Helper function to convert a byte array to a hex string
     */
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(separator = " ") { 
            String.format("%02X", it) 
        }
    }
    
    /**
     * Retry a failed connection
     */
    private fun retryConnection() {
        val device = deviceToRetry
        val context = retryContext
        val callback = retryCallback
        
        if (device != null && context != null && callback != null) {
            LogManager.log("BTConnect", "Retrying connection to ${device.address}...", LogManager.LogLevel.INFO)
            connectToDevice(context, device, callback)
        } else {
            LogManager.log("BTConnect", "Cannot retry connection: missing device, context, or callback", LogManager.LogLevel.ERROR)
            connectionRetryCount = 0
        }
    }
}