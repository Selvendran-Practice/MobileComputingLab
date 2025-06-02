package com.example.assignment3task1

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.assignment3task1.ui.theme.Assignment3task1Theme

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var scanner: BluetoothLeScanner
    private val PERMISSIONS_REQUEST_CODE = 1001

    private val requiredPermissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    private val beaconData = mutableStateOf("Scanning for beacons...")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // UI
        setContent {
            Assignment3task1Theme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    content = { padding ->
                        Column(
                            modifier = Modifier
                                .padding(padding)
                                .padding(16.dp)
                        ) {
                            Text(text = "Eddystone Beacon Info", style = MaterialTheme.typography.headlineSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = beaconData.value)
                        }
                    }
                )
            }
        }

        // Setup Bluetooth
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Permissions
        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, requiredPermissions.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        } else {
            startBleScan()
        }
    }

    private fun hasPermissions(): Boolean {
        return requiredPermissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startBleScan() {
        if (!bluetoothAdapter.isEnabled) {
            beaconData.value = "Bluetooth is disabled."
            return
        }

        scanner = bluetoothAdapter.bluetoothLeScanner
        scanner.startScan(leScanCallback)
    }

    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val scanData = result.scanRecord?.bytes ?: return
            val rssi = result.rssi
            parseEddystoneFrame(scanData, rssi)
        }
    }

    private fun parseEddystoneFrame(scanRecord: ByteArray, rssi: Int) {
        for (i in scanRecord.indices) {
            if (i + 4 < scanRecord.size &&
                scanRecord[i + 0] == 0x03.toByte() &&
                scanRecord[i + 1] == 0x03.toByte() &&
                scanRecord[i + 2] == 0xAA.toByte() &&
                scanRecord[i + 3] == 0xFE.toByte()
            ) {
                // Eddystone frame found
                val frameTypeIndex = i + 5
                if (frameTypeIndex >= scanRecord.size) return

                when (scanRecord[frameTypeIndex]) {
                    0x00.toByte() -> { // UID
                        val namespace = scanRecord.copyOfRange(frameTypeIndex + 2, frameTypeIndex + 12)
                        val instance = scanRecord.copyOfRange(frameTypeIndex + 12, frameTypeIndex + 18)
                        val beaconId = namespace.joinToString("") { "%02X".format(it) } +
                                instance.joinToString("") { "%02X".format(it) }
                        val txPower = scanRecord[frameTypeIndex + 1].toInt()
                        val distance = calculateDistance(rssi, txPower)
                        beaconData.value = "UID Frame:\nBeacon ID: $beaconId\nDistance: ${"%.2f".format(distance)} m"
                    }

                    0x10.toByte() -> { // URL
                        val url = decodeUrl(scanRecord, frameTypeIndex + 2)
                        beaconData.value = "URL Frame:\n$url"
                    }

                    0x20.toByte() -> { // TLM
                        val voltage = ((scanRecord[frameTypeIndex + 2].toInt() and 0xFF) shl 8) or
                                (scanRecord[frameTypeIndex + 3].toInt() and 0xFF)
                        val tempRaw = (scanRecord[frameTypeIndex + 4].toInt() shl 8) or
                                (scanRecord[frameTypeIndex + 5].toInt() and 0xFF)
                        val temperature = tempRaw / 256.0
                        beaconData.value = "TLM Frame:\nVoltage: ${voltage / 1000.0} V\nTemperature: ${"%.2f".format(temperature)} °C"
                    }
                }
            }
        }
    }

    private fun decodeUrl(scanRecord: ByteArray, startIndex: Int): String {
        val prefixMap = mapOf(
            0x00.toByte() to "http://www.",
            0x01.toByte() to "https://www.",
            0x02.toByte() to "http://",
            0x03.toByte() to "https://"
        )

        val suffixMap = mapOf(
            0x00.toByte() to ".com/",
            0x01.toByte() to ".org/",
            0x02.toByte() to ".edu/",
            0x03.toByte() to ".net/",
            0x04.toByte() to ".info/",
            0x05.toByte() to ".biz/",
            0x06.toByte() to ".gov/",
            0x07.toByte() to ".com",
            0x08.toByte() to ".org",
            0x09.toByte() to ".edu",
            0x0A.toByte() to ".net",
            0x0B.toByte() to ".info",
            0x0C.toByte() to ".biz",
            0x0D.toByte() to ".gov"
        )

        if (startIndex >= scanRecord.size) return "Invalid URL"
        val scheme = prefixMap[scanRecord[startIndex]] ?: return "Unknown scheme"

        val sb = StringBuilder(scheme)
        for (i in (startIndex + 1) until scanRecord.size) {
            val b = scanRecord[i]
            if (suffixMap.containsKey(b)) {
                sb.append(suffixMap[b])
            } else {
                sb.append(b.toInt().toChar())
            }
        }
        return sb.toString()
    }

    private fun calculateDistance(rssi: Int, txPower: Int): Double {
        if (rssi == 0) return -1.0
        val ratio = rssi * 1.0 / txPower
        return if (ratio < 1.0) {
            Math.pow(ratio, 10.0)
        } else {
            0.89976 * Math.pow(ratio, 7.7095) + 0.111
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startBleScan()
        } else {
            beaconData.value = "Permissions not granted"
        }
    }
}
