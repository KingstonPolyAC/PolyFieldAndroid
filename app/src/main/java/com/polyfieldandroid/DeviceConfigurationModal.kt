package com.polyfieldandroid

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Device Configuration Modal
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceConfigurationModal(
    onDismiss: () -> Unit,
    devices: DeviceConfig,
    detectedDevices: List<DetectedDevice>,
    initialSelectedDevice: String? = null,
    onUpdateDevice: (String, DeviceState) -> Unit,
    onRefreshUsb: () -> Unit = {},
    onTestScoreboard: () -> Unit = {}
) {
    var selectedDevice by remember { mutableStateOf(initialSelectedDevice ?: "edm") }
    
    // Get current device configuration based on selected device
    val currentDevice = when (selectedDevice) {
        "edm" -> devices.edm
        "wind" -> devices.wind
        "scoreboard" -> devices.scoreboard
        else -> devices.edm
    }
    
    var connectionType by remember(selectedDevice) { mutableStateOf(currentDevice.connectionType) }
    var serialPort by remember(selectedDevice) { mutableStateOf(currentDevice.serialPort) }
    var ipAddress by remember(selectedDevice) { mutableStateOf(currentDevice.ipAddress) }
    var port by remember(selectedDevice) { mutableIntStateOf(currentDevice.port) }
    
    // Dropdown state for detected devices
    var expanded by remember { mutableStateOf(false) }
    var selectedDetectedDevice by remember { mutableStateOf<DetectedDevice?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Device Configuration",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                // Device name (fixed, no selection when initialSelectedDevice is provided)
                if (initialSelectedDevice != null) {
                    Text(
                        text = "Configuring: ${
                            when (selectedDevice) {
                                "edm" -> "EDM Device"
                                "wind" -> "Wind Gauge"
                                "scoreboard" -> "Scoreboard"
                                else -> "Device"
                            }
                        }",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                } else {
                    // Device selection only when no specific device is pre-selected
                    Text(
                        text = "Device:",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedDevice == "edm",
                            onClick = { selectedDevice = "edm" },
                            label = { Text("EDM") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedDevice == "wind",
                            onClick = { selectedDevice = "wind" },
                            label = { Text("Wind") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedDevice == "scoreboard",
                            onClick = { selectedDevice = "scoreboard" },
                            label = { Text("Scoreboard") },
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Empty space to maintain layout
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Connection type
                Text(
                    text = "Connection Type:",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = connectionType == "serial",
                        onClick = { connectionType = "serial" },
                        label = { Text("Serial") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = connectionType == "network",
                        onClick = { connectionType = "network" },
                        label = { Text("Network") },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Connection details
                if (connectionType == "serial") {
                    if (detectedDevices.isNotEmpty()) {
                        // Dropdown for detected devices with refresh button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Detected Devices:",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            OutlinedButton(
                                onClick = { onRefreshUsb() },
                                modifier = Modifier.size(width = 80.dp, height = 32.dp)
                            ) {
                                Text(
                                    text = "Refresh",
                                    fontSize = 10.sp
                                )
                            }
                        }
                        
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedDetectedDevice?.deviceName ?: "Select a device...",
                                onValueChange = { },
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                detectedDevices.forEach { device ->
                                    DropdownMenuItem(
                                        text = { 
                                            Column {
                                                Text(
                                                    text = device.deviceName,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "VID: ${String.format("%04X", device.vendorId)}, PID: ${String.format("%04X", device.productId)}",
                                                    fontSize = 12.sp,
                                                    color = androidx.compose.ui.graphics.Color.Gray
                                                )
                                                Text(
                                                    text = "Path: ${device.serialPath}",
                                                    fontSize = 12.sp,
                                                    color = androidx.compose.ui.graphics.Color.Gray
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedDetectedDevice = device
                                            serialPort = device.serialPath
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        // Fallback to text field if no devices detected with refresh button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "No USB Devices:",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            OutlinedButton(
                                onClick = { onRefreshUsb() },
                                modifier = Modifier.size(width = 80.dp, height = 32.dp)
                            ) {
                                Text(
                                    text = "Refresh",
                                    fontSize = 10.sp
                                )
                            }
                        }
                        
                        OutlinedTextField(
                            value = serialPort,
                            onValueChange = { serialPort = it },
                            label = { Text("Serial Port") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Text(
                            text = "No USB devices detected. Enter path manually or try Refresh.",
                            fontSize = 12.sp,
                            color = androidx.compose.ui.graphics.Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = ipAddress,
                        onValueChange = { ipAddress = it },
                        label = { Text("IP Address") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = port.toString(),
                        onValueChange = { port = it.toIntOrNull() ?: 8080 },
                        label = { Text("Port") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Test button for scoreboard
                    if (selectedDevice == "scoreboard") {
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = onTestScoreboard,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Test Scoreboard (3-2-1-0 Countdown)")
                        }

                        Text(
                            text = "Tests connection by displaying countdown: 33.33 → 22.22 → 11.11 → 00.00",
                            fontSize = 12.sp,
                            color = androidx.compose.ui.graphics.Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val deviceState = DeviceState(
                        connected = false,
                        connectionType = connectionType,
                        serialPort = serialPort,
                        ipAddress = ipAddress,
                        port = port
                    )
                    onUpdateDevice(selectedDevice, deviceState)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}