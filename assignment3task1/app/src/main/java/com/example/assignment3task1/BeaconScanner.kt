package com.example.assignment3task1

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class BeaconScanner(
    private val context: Context,
    private val viewModel: BeaconViewModel
) {

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val scanCallback = BluetoothAdapter.LeScanCallback { device, rssi, scanRecord ->
        BeaconProcessor.process(scanRecord, rssi, viewModel)
    }

    fun startScan() {
        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                1001
            )
            return
        }

        if (bluetoothAdapter?.isEnabled == true) {
            bluetoothAdapter.startLeScan(scanCallback)
        }
    }

    fun stopScan() {
        bluetoothAdapter?.stopLeScan(scanCallback)
    }

    fun handlePermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode == 1001 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            startScan()
        }
    }

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    }
}

