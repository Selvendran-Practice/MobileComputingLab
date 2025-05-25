package com.example.bluetoothtemplate

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@SuppressLint("MissingPermission")
@Composable
fun BluetoothDeviceItem(
    device: BluetoothDevice,
    modifier: Modifier = Modifier
) {
    // Determine if this is likely a BLE device
    val isBleDevice = try {
        device.type == BluetoothDevice.DEVICE_TYPE_LE || 
        device.type == BluetoothDevice.DEVICE_TYPE_DUAL ||
        (device.bluetoothClass == null)
    } catch (e: Exception) {
        // If we can't determine the type, assume it's a BLE device
        true
    }
    
    val deviceTypeColor = when {
        isBleDevice -> Color(0xFF4CAF50) // Green for BLE
        else -> Color(0xFF2196F3) // Blue for Classic
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Device type indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(deviceTypeColor)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Device name (or address if name is null)
                val deviceName = try {
                    device.name ?: "Unknown Device"
                } catch (e: Exception) {
                    "Unknown Device"
                }
                
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Device address
                val deviceAddress = try {
                    device.address
                } catch (e: Exception) {
                    "Unknown Address"
                }
                
                Text(
                    text = deviceAddress,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                // Device type
                val deviceTypeText = when {
                    isBleDevice -> "Bluetooth Low Energy"
                    else -> "Classic Bluetooth"
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = deviceTypeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Device class/type if available
            val deviceClass = remember {
                try {
                    device.bluetoothClass
                } catch (e: Exception) {
                    null
                }
            }
            
            deviceClass?.let { bluetoothClass ->
                Text(
                    text = getDeviceType(bluetoothClass),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

// Helper function to get a readable device type from the BluetoothClass
private fun getDeviceType(bluetoothClass: BluetoothClass): String {
    return when (bluetoothClass.majorDeviceClass) {
        BluetoothClass.Device.Major.COMPUTER -> "Computer"
        BluetoothClass.Device.Major.PHONE -> "Phone"
        BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio"
        BluetoothClass.Device.Major.PERIPHERAL -> "Peripheral"
        BluetoothClass.Device.Major.IMAGING -> "Imaging"
        BluetoothClass.Device.Major.WEARABLE -> "Wearable"
        BluetoothClass.Device.Major.TOY -> "Toy"
        BluetoothClass.Device.Major.HEALTH -> "Health"
        BluetoothClass.Device.Major.UNCATEGORIZED -> "Uncategorized"
        else -> "Other"
    }
}