package com.example.bluetoothtemplate

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun BluetoothPermissionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bluetooth Required") },
        text = { Text("This app requires Bluetooth to function properly. Would you like to enable Bluetooth?") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Enable Bluetooth")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}