package com.polyfieldandroid

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

// Device State
data class DeviceState(
    val connected: Boolean = false,
    val connectionType: String = "serial", // "serial" or "network"
    val serialPort: String = "/dev/ttyUSB0",
    val ipAddress: String = "192.168.1.100",
    val port: Int = 8080
)

data class DeviceConfig(
    val edm: DeviceState = DeviceState(),
    val wind: DeviceState = DeviceState(),
    val scoreboard: DeviceState = DeviceState()
)

// Calibration State
data class CalibrationState(
    val circleType: String = "SHOT",
    val targetRadius: Double = 1.0675,
    val centreSet: Boolean = false,
    val centreTimestamp: String? = null,
    val stationCoordinates: Pair<Double, Double>? = null,
    val edgeVerified: Boolean = false,
    val edgeResult: EdgeResult? = null
)

data class EdgeResult(
    val toleranceCheck: Boolean = false,
    val measurements: List<Double> = emptyList(),
    val averageRadius: Double = 0.0,
    val deviation: Double = 0.0
)

// Throw Coordinate
data class ThrowCoordinate(
    val x: Double,
    val y: Double,
    val distance: Double,
    val circleType: String,
    val timestamp: String
)

// Settings data class
data class AppSettings(
    val isDoubleReadMode: Boolean = true
)

// Detected USB Device info
data class DetectedDevice(
    val vendorId: Int,
    val productId: Int,
    val deviceName: String,
    val serialPath: String
)

// Complete App State matching original
data class AppState(
    val currentScreen: String = "SELECT_EVENT_TYPE",
    val eventType: String? = null,
    val isDemoMode: Boolean = false, // Live mode by default like original
    val measurement: String = "",
    val windMeasurement: String = "",
    val isLoading: Boolean = false,
    val devices: DeviceConfig = DeviceConfig(),
    val calibration: CalibrationState = CalibrationState(),
    val throwCoordinates: List<ThrowCoordinate> = emptyList(),
    val deviceSetupVisible: Boolean = false,
    val selectedDeviceForConfig: String? = null,
    val heatMapVisible: Boolean = false,
    val connectedDevice: UsbDevice? = null,
    val detectedDevices: List<DetectedDevice> = emptyList(),
    val errorMessage: String? = null,
    val errorTitle: String? = null,
    val settings: AppSettings = AppSettings()
)

class AppViewModel(private val context: android.content.Context) : androidx.lifecycle.ViewModel() {
    private val _uiState = MutableStateFlow(AppState())
    val uiState: StateFlow<AppState> = _uiState.asStateFlow()
    
    // EDM Module for device communication
    private val edmModule = EDMModule(context)
    
    fun updateScreen(screen: String) {
        _uiState.value = _uiState.value.copy(currentScreen = screen)
    }
    
    fun updateEventType(eventType: String) {
        _uiState.value = _uiState.value.copy(eventType = eventType)
    }
    
    fun updateCircleType(circleType: String) {
        val targetRadius = getDemoUKARadius(circleType)
        _uiState.value = _uiState.value.copy(
            calibration = _uiState.value.calibration.copy(
                circleType = circleType,
                targetRadius = targetRadius
            )
        )
    }
    
    fun toggleDemoMode() {
        val currentMode = _uiState.value.isDemoMode
        _uiState.value = _uiState.value.copy(isDemoMode = !currentMode)
        
        if (currentMode) { // Switching to live mode
            resetCalibration()
            if (_uiState.value.eventType == "Throws") {
                updateScreen("DEVICE_SETUP")
            }
        }
    }
    
    fun updateSettings(settings: AppSettings) {
        _uiState.value = _uiState.value.copy(settings = settings)
    }
    
    fun updateDetectedDevices(devices: List<DetectedDevice>) {
        _uiState.value = _uiState.value.copy(detectedDevices = devices)
    }
    
    fun getUsbDevices(): Map<String, Any> {
        return edmModule.listUsbDevices()
    }
    
    fun refreshUsbDevices() {
        android.util.Log.d("PolyField", "Manual USB refresh requested")
        val usbDevicesResult = getUsbDevices()
        android.util.Log.d("PolyField", "USB refresh result: $usbDevicesResult")
        
        @Suppress("UNCHECKED_CAST")
        val usbDevices = (usbDevicesResult["ports"] as? List<Map<String, Any>>) ?: emptyList()
        
        val detectedDevices = if (usbDevices.isEmpty()) {
            // If no real devices found, add a test device to verify UI functionality
            android.util.Log.d("PolyField", "No real USB devices found - adding test device for UI verification")
            listOf(
                DetectedDevice(
                    vendorId = 1027,  // FTDI VID
                    productId = 24577, // FTDI PID
                    deviceName = "Test FTDI Device - FT232R USB UART (VID:0403 PID:6001)",
                    serialPath = "/dev/ttyUSB0"
                )
            )
        } else {
            usbDevices.mapIndexed { index, deviceInfo ->
                DetectedDevice(
                    vendorId = deviceInfo["vendorId"] as? Int ?: 0,
                    productId = deviceInfo["productId"] as? Int ?: 0,
                    deviceName = deviceInfo["description"] as? String ?: "USB Device ${index + 1}",
                    serialPath = "/dev/ttyUSB$index"
                )
            }
        }
        
        android.util.Log.d("PolyField", "Detected ${detectedDevices.size} USB devices after refresh")
        updateDetectedDevices(detectedDevices)
    }
    
    fun updateDevice(device: UsbDevice?, isDemoMode: Boolean) {
        _uiState.value = _uiState.value.copy(
            connectedDevice = device,
            isDemoMode = isDemoMode
        )
    }
    
    fun updateDeviceConnection(deviceType: String, connected: Boolean) {
        if (connected && !_uiState.value.isDemoMode) {
            // In live mode, attempt actual device connection
            connectToRealDevice(deviceType)
        } else if (!connected) {
            // Disconnect device
            edmModule.disconnectDevice(deviceType)
        }
        
        val devices = _uiState.value.devices
        val updatedDevices = when (deviceType) {
            "edm" -> devices.copy(edm = devices.edm.copy(connected = connected))
            "wind" -> devices.copy(wind = devices.wind.copy(connected = connected))
            "scoreboard" -> devices.copy(scoreboard = devices.scoreboard.copy(connected = connected))
            else -> devices
        }
        _uiState.value = _uiState.value.copy(devices = updatedDevices)
    }
    
    private fun connectToRealDevice(deviceType: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                val device = when (deviceType) {
                    "edm" -> _uiState.value.devices.edm
                    "wind" -> _uiState.value.devices.wind
                    "scoreboard" -> _uiState.value.devices.scoreboard
                    else -> return@launch
                }
                
                val result = when (device.connectionType) {
                    "serial" -> edmModule.connectSerialDevice(deviceType, device.serialPort)
                    "network" -> edmModule.connectNetworkDevice(deviceType, device.ipAddress, device.port)
                    else -> edmModule.connectUsbDevice(deviceType, device.serialPort)
                }
                
                android.util.Log.d("PolyField", "Device connection result: $result")
                
                // Update connection status based on result
                val success = result["success"] as? Boolean == true
                if (!success) {
                    val error = result["error"] as? String ?: "Unknown error"
                    android.util.Log.e("PolyField", "Device connection failed: $error")
                    // Reset connection state on failure
                    updateDeviceConnectionState(deviceType, false)
                }
            } catch (e: Exception) {
                android.util.Log.e("PolyField", "Device connection error", e)
                updateDeviceConnectionState(deviceType, false)
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    private fun updateDeviceConnectionState(deviceType: String, connected: Boolean) {
        val devices = _uiState.value.devices
        val updatedDevices = when (deviceType) {
            "edm" -> devices.copy(edm = devices.edm.copy(connected = connected))
            "wind" -> devices.copy(wind = devices.wind.copy(connected = connected))
            "scoreboard" -> devices.copy(scoreboard = devices.scoreboard.copy(connected = connected))
            else -> devices
        }
        _uiState.value = _uiState.value.copy(devices = updatedDevices)
    }
    
    fun updateDeviceConfig(deviceType: String, deviceState: DeviceState) {
        val devices = _uiState.value.devices
        val updatedDevices = when (deviceType) {
            "edm" -> devices.copy(edm = deviceState)
            "wind" -> devices.copy(wind = deviceState)
            "scoreboard" -> devices.copy(scoreboard = deviceState)
            else -> devices
        }
        _uiState.value = _uiState.value.copy(devices = updatedDevices)
    }
    
    fun setCentre() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        if (_uiState.value.isDemoMode) {
            // Demo mode - simulate centre setting
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val stationX = (kotlin.random.Random.nextDouble() - 0.5) * 20 // ±10m
            val stationY = (kotlin.random.Random.nextDouble() - 0.5) * 20 // ±10m
            
            _uiState.value = _uiState.value.copy(
                calibration = _uiState.value.calibration.copy(
                    centreSet = true,
                    centreTimestamp = timestamp,
                    stationCoordinates = Pair(stationX, stationY)
                ),
                isLoading = false
            )
        } else {
            // Live mode - use real EDM device
            viewModelScope.launch {
                try {
                    val isDoubleReadMode = _uiState.value.settings.isDoubleReadMode
                    android.util.Log.d("PolyField", "Setting centre with real EDM device (${if (isDoubleReadMode) "double" else "single"} read mode)...")
                    
                    val reading = if (isDoubleReadMode) {
                        edmModule.getReliableEDMReading("edm")
                    } else {
                        edmModule.getSingleEDMReading("edm")
                    }
                    
                    if (reading.success) {
                        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                        // In real mode, station coordinates would come from GPS or manual input
                        val stationX = 0.0 // Centre of coordinate system
                        val stationY = 0.0
                        
                        android.util.Log.d("PolyField", "Centre set successfully")
                        
                        _uiState.value = _uiState.value.copy(
                            calibration = _uiState.value.calibration.copy(
                                centreSet = true,
                                centreTimestamp = timestamp,
                                stationCoordinates = Pair(stationX, stationY)
                            ),
                            isLoading = false
                        )
                    } else {
                        android.util.Log.e("PolyField", "Failed to set centre: ${reading.error}")
                        showErrorDialog(
                            "Centre Set Failed",
                            reading.error ?: "Failed to set centre with EDM device"
                        )
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PolyField", "Centre setting error", e)
                    showErrorDialog(
                        "Centre Set Failed", 
                        e.message ?: "Unknown error occurred while setting centre"
                    )
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }
    
    fun verifyEdge() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        if (_uiState.value.isDemoMode) {
            // Demo mode - simulate edge verification
            val targetRadius = _uiState.value.calibration.targetRadius
            val measurements = List(5) { 
                targetRadius + (kotlin.random.Random.nextDouble() - 0.5) * 0.01 // ±5mm variation
            }
            val averageRadius = measurements.average()
            val deviation = kotlin.math.abs(averageRadius - targetRadius)
            // Different tolerances per UKA/WA rules
            val tolerance = if (_uiState.value.calibration.circleType == "JAVELIN_ARC") 0.010 else 0.005 // 10mm for javelin, 5mm for others
            val toleranceCheck = deviation <= tolerance
            
            val edgeResult = EdgeResult(
                toleranceCheck = toleranceCheck,
                measurements = measurements,
                averageRadius = averageRadius,
                deviation = deviation
            )
            
            _uiState.value = _uiState.value.copy(
                calibration = _uiState.value.calibration.copy(
                    edgeVerified = toleranceCheck,
                    edgeResult = edgeResult
                ),
                isLoading = false
            )
        } else {
            // Live mode - use real EDM device for edge verification
            viewModelScope.launch {
                try {
                    val isDoubleReadMode = _uiState.value.settings.isDoubleReadMode
                    android.util.Log.d("PolyField", "Verifying edge with real EDM device (${if (isDoubleReadMode) "double" else "single"} read mode)...")
                    
                    val reading = if (isDoubleReadMode) {
                        edmModule.getReliableEDMReading("edm")
                    } else {
                        edmModule.getSingleEDMReading("edm")
                    }
                    
                    if (reading.success && reading.distance != null) {
                        val targetRadius = _uiState.value.calibration.targetRadius
                        val measuredRadius = reading.distance
                        val deviation = kotlin.math.abs(measuredRadius - targetRadius)
                        
                        // Different tolerances per UKA/WA rules
                        val tolerance = if (_uiState.value.calibration.circleType == "JAVELIN_ARC") 0.010 else 0.005 // 10mm for javelin, 5mm for others
                        val toleranceCheck = deviation <= tolerance
                        
                        android.util.Log.d("PolyField", "Edge verification: measured=${measuredRadius}m, target=${targetRadius}m, deviation=${deviation*1000}mm, pass=${toleranceCheck}")
                        
                        val edgeResult = EdgeResult(
                            toleranceCheck = toleranceCheck,
                            measurements = listOf(measuredRadius),
                            averageRadius = measuredRadius,
                            deviation = deviation
                        )
                        
                        _uiState.value = _uiState.value.copy(
                            calibration = _uiState.value.calibration.copy(
                                edgeVerified = toleranceCheck,
                                edgeResult = edgeResult
                            ),
                            isLoading = false
                        )
                    } else {
                        android.util.Log.e("PolyField", "Edge verification failed: ${reading.error}")
                        showErrorDialog(
                            "Edge Verification Failed",
                            reading.error ?: "Failed to verify edge with EDM device"
                        )
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PolyField", "Edge verification error", e)
                    showErrorDialog(
                        "Edge Verification Failed",
                        e.message ?: "Unknown error occurred while verifying edge"
                    )
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }
    
    fun measureDistance() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        if (_uiState.value.isDemoMode) {
            // Demo mode - use simulated values
            val distance = generateDemoThrow()
            _uiState.value = _uiState.value.copy(
                measurement = String.format("%.2f m", distance),
                isLoading = false
            )
            
            // Store throw coordinate
            val throwCoord = ThrowCoordinate(
                x = kotlin.random.Random.nextDouble() * 20 - 10,
                y = kotlin.random.Random.nextDouble() * 50 + distance,
                distance = distance,
                circleType = _uiState.value.calibration.circleType,
                timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            )
            
            _uiState.value = _uiState.value.copy(
                throwCoordinates = _uiState.value.throwCoordinates + throwCoord
            )
        } else {
            // Live mode - use real EDM device
            if (!_uiState.value.devices.edm.connected) {
                showErrorDialog(
                    "Device Error",
                    "EDM device is not connected. Please connect a device first."
                )
                _uiState.value = _uiState.value.copy(isLoading = false)
                return
            }
            
            viewModelScope.launch {
                try {
                    val isDoubleReadMode = _uiState.value.settings.isDoubleReadMode
                    android.util.Log.d("PolyField", "Getting real EDM reading (${if (isDoubleReadMode) "double" else "single"} read mode)...")
                    
                    val reading = if (isDoubleReadMode) {
                        edmModule.getReliableEDMReading("edm")
                    } else {
                        edmModule.getSingleEDMReading("edm")
                    }
                    
                    if (reading.success && reading.distance != null) {
                        val distance = reading.distance
                        android.util.Log.d("PolyField", "EDM reading successful: ${distance}m")
                        
                        _uiState.value = _uiState.value.copy(
                            measurement = String.format("%.2f m", distance),
                            isLoading = false
                        )
                        
                        // Store throw coordinate
                        val throwCoord = ThrowCoordinate(
                            x = kotlin.random.Random.nextDouble() * 20 - 10,
                            y = kotlin.random.Random.nextDouble() * 50 + distance,
                            distance = distance,
                            circleType = _uiState.value.calibration.circleType,
                            timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                        )
                        
                        _uiState.value = _uiState.value.copy(
                            throwCoordinates = _uiState.value.throwCoordinates + throwCoord
                        )
                    } else {
                        android.util.Log.e("PolyField", "EDM reading failed: ${reading.error}")
                        showErrorDialog(
                            "Measurement Error",
                            "Failed to get measurement from device"
                        )
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PolyField", "EDM measurement error", e)
                    showErrorDialog(
                        "Device Error",
                        "Failed to measure distance: ${e.message}"
                    )
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }
    
    fun measureWind() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        if (_uiState.value.isDemoMode) {
            // Demo mode - use simulated values
            val windSpeed = (kotlin.random.Random.nextDouble() - 0.5) * 4.0 // ±2 m/s
            _uiState.value = _uiState.value.copy(
                windMeasurement = String.format("%s%.1f m/s", if (windSpeed > 0) "+" else "", windSpeed),
                isLoading = false
            )
        } else {
            // Live mode - use real wind gauge
            if (!_uiState.value.devices.wind.connected) {
                showErrorDialog(
                    "Device Error",
                    "Wind gauge is not connected. Please connect a device first."
                )
                _uiState.value = _uiState.value.copy(isLoading = false)
                return
            }
            
            viewModelScope.launch {
                try {
                    android.util.Log.d("PolyField", "Getting real wind reading...")
                    val reading = edmModule.measureWind()
                    
                    if (reading.success && reading.windSpeed != null) {
                        val windSpeed = reading.windSpeed
                        android.util.Log.d("PolyField", "Wind reading successful: ${windSpeed}m/s")
                        
                        _uiState.value = _uiState.value.copy(
                            windMeasurement = String.format("%s%.1f m/s", if (windSpeed > 0) "+" else "", windSpeed),
                            isLoading = false
                        )
                    } else {
                        android.util.Log.e("PolyField", "Wind reading failed: ${reading.error}")
                        showErrorDialog(
                            "Measurement Error", 
                            "Failed to get wind measurement from device"
                        )
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PolyField", "Wind measurement error", e)
                    showErrorDialog(
                        "Device Error",
                        "Failed to measure wind: ${e.message}"
                    )
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }
    
    fun resetCalibration() {
        _uiState.value = _uiState.value.copy(
            calibration = CalibrationState()
        )
    }
    
    fun resetSession() {
        _uiState.value = _uiState.value.copy(
            throwCoordinates = emptyList(),
            measurement = "",
            windMeasurement = ""
        )
    }
    
    fun toggleDeviceSetupModal(deviceType: String? = null) {
        _uiState.value = _uiState.value.copy(
            deviceSetupVisible = !_uiState.value.deviceSetupVisible,
            selectedDeviceForConfig = if (!_uiState.value.deviceSetupVisible) deviceType else null
        )
    }
    
    fun showErrorDialog(title: String, message: String) {
        _uiState.value = _uiState.value.copy(
            errorTitle = title,
            errorMessage = message
        )
    }
    
    fun dismissErrorDialog() {
        _uiState.value = _uiState.value.copy(
            errorTitle = null,
            errorMessage = null
        )
    }
    
    fun toggleHeatMap() {
        _uiState.value = _uiState.value.copy(
            heatMapVisible = !_uiState.value.heatMapVisible
        )
    }
    
    private fun generateDemoThrow(): Double {
        val baseDistance = when (_uiState.value.calibration.circleType) {
            "SHOT" -> 15.0 + kotlin.random.Random.nextDouble() * 8.0 // 15-23m
            "DISCUS" -> 45.0 + kotlin.random.Random.nextDouble() * 25.0 // 45-70m  
            "HAMMER" -> 55.0 + kotlin.random.Random.nextDouble() * 25.0 // 55-80m
            "JAVELIN_ARC" -> 60.0 + kotlin.random.Random.nextDouble() * 30.0 // 60-90m
            else -> 20.0 + kotlin.random.Random.nextDouble() * 10.0
        }
        return baseDistance
    }
}

// Settings Screen
@Composable
fun SettingsScreen(
    isDemoMode: Boolean,
    isDoubleReadMode: Boolean,
    onDemoModeToggle: () -> Unit,
    onDoubleReadModeToggle: (Boolean) -> Unit,
    onBackClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(maxOf(20f, screenWidth * 0.025f).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "Settings",
                fontSize = maxOf(24f, screenWidth * 0.028f).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 40.dp)
            )
            
            // Settings options
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Demo/Live Mode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Demo Mode",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF333333)
                            )
                            Text(
                                text = if (isDemoMode) "Currently: Demo Active" else "Currently: Live Mode",
                                fontSize = 14.sp,
                                color = Color(0xFF666666)
                            )
                        }
                        Switch(
                            checked = isDemoMode,
                            onCheckedChange = { onDemoModeToggle() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFFFEB3B),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFF4CAF50)
                            )
                        )
                    }
                    
                    Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                    
                    // Single/Double Read Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "EDM Read Mode",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF333333)
                            )
                            Text(
                                text = if (isDoubleReadMode) "Double read with tolerance" else "Single read only",
                                fontSize = 14.sp,
                                color = Color(0xFF666666)
                            )
                        }
                        Switch(
                            checked = isDoubleReadMode,
                            onCheckedChange = onDoubleReadModeToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF1976D2),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFF999999)
                            )
                        )
                    }
                }
            }
        }
        
        // Back button
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            shape = RectangleShape,
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBackClick,
                    modifier = Modifier.width(200.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE0E0E0),
                        contentColor = Color(0xFF333333)
                    )
                ) {
                    Text(
                        text = "← Back",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

class MainActivityCompose : ComponentActivity() {
    
    // USB Management
    private lateinit var usbManager: UsbManager
    private var permissionIntent: PendingIntent? = null
    private var usbReceiver: BroadcastReceiver? = null
    
    private lateinit var viewModel: AppViewModel
    
    companion object {
        private const val ACTION_USB_PERMISSION = "com.polyfieldandroid.USB_PERMISSION"
    }
    
    // USB status logging (console only like original)
    private fun updateStatus(message: String) {
        android.util.Log.d("PolyField", "USB Status: $message")
    }
    
    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        kotlin.io.println(
            if (allGranted) "Runtime permissions granted" 
            else "Some permissions denied - functionality may be limited"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        viewModel = AppViewModel(this)
        initializeUSB()
        
        setContent {
            PolyFieldTheme {
                PolyFieldApp(viewModel = viewModel)
            }
        }
        
        checkConnectedUSBDevices()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        usbReceiver?.let { unregisterReceiver(it) }
    }
    
    // USB Management Implementation
    private fun initializeUSB() {
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        permissionIntent = PendingIntent.getBroadcast(
            this, 0, Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                android.util.Log.d("PolyField", "USB BroadcastReceiver: Received intent action: ${intent.action}")
                when (intent.action) {
                    ACTION_USB_PERMISSION -> {
                        synchronized(this) {
                            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                                device?.let { onUSBDevicePermissionGranted(it) }
                            } else {
                                android.util.Log.d("PolyField", "USB permission denied for device")
                            }
                        }
                    }
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        device?.let { onUSBDeviceAttached(it) }
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        device?.let { onUSBDeviceDetached(it) }
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        registerReceiver(usbReceiver, filter)
        
        requestRuntimePermissions()
    }
    
    private fun requestRuntimePermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
    
    private fun checkConnectedUSBDevices() {
        val deviceList = usbManager.deviceList
        android.util.Log.d("PolyField", "Checking USB devices - Total devices found: ${deviceList.size}")
        
        if (deviceList.isEmpty()) {
            android.util.Log.d("PolyField", "No USB devices detected")
            viewModel.updateDetectedDevices(emptyList())
            return
        }
        
        // Log all detected USB devices for debugging
        deviceList.values.forEach { device ->
            android.util.Log.d("PolyField", "USB Device found: ${device.productName ?: "Unknown"} " +
                    "(VID: ${String.format("%04X", device.vendorId)}, " +
                    "PID: ${String.format("%04X", device.productId)})")
        }
        
        // Use the EDMModule to list USB devices (matches v16 approach)
        val usbDevicesResult = viewModel.getUsbDevices()
        
        @Suppress("UNCHECKED_CAST")
        val usbDevices = (usbDevicesResult["ports"] as? List<Map<String, Any>>) ?: emptyList()
        
        // Find all serial devices and populate the detected devices list
        val serialDevices = deviceList.values.filter { isUSBSerialDevice(it) }
        android.util.Log.d("PolyField", "Compatible USB serial devices: ${serialDevices.size}")
        
        val detectedDevices = usbDevices.mapIndexed { index, deviceInfo ->
            DetectedDevice(
                vendorId = deviceInfo["vendorId"] as? Int ?: 0,
                productId = deviceInfo["productId"] as? Int ?: 0,
                deviceName = deviceInfo["description"] as? String ?: "USB Device ${index + 1}",
                serialPath = "/dev/ttyUSB$index"
            )
        }
        
        // Update the state with detected devices
        viewModel.updateDetectedDevices(detectedDevices)
        
        if (serialDevices.isNotEmpty()) {
            val deviceNames = serialDevices.map { it.productName ?: "Unknown" }
            android.util.Log.d("PolyField", "Found ${serialDevices.size} USB serial device(s): $deviceNames")
            
            // Request permission for the first device (or handle multiple devices)
            val firstDevice = serialDevices.first()
            android.util.Log.d("PolyField", "Checking permissions for: ${firstDevice.productName}")
            
            if (!usbManager.hasPermission(firstDevice)) {
                android.util.Log.d("PolyField", "Requesting USB permission for device")
                usbManager.requestPermission(firstDevice, permissionIntent)
            } else {
                android.util.Log.d("PolyField", "Device already has permission, connecting")
                onUSBDevicePermissionGranted(firstDevice)
            }
        } else {
            android.util.Log.d("PolyField", "No compatible USB serial devices found")
        }
    }
    
    private fun isUSBSerialDevice(device: UsbDevice): Boolean {
        val vendorId = device.vendorId
        val productId = device.productId
        
        // FTDI devices (0x0403 = 1027 decimal)
        if (vendorId == 1027) {
            return productId == 24577 || productId == 24596 || productId == 24582
        }
        
        // Prolific PL2303 (0x067b = 1659 decimal)
        if (vendorId == 1659 && productId == 8963) {
            return true
        }
        
        // Silicon Labs CP2102 (0x10c4 = 4292 decimal)
        if (vendorId == 4292 && productId == 60000) {
            return true
        }
        
        return false
    }
    
    private fun onUSBDeviceAttached(device: UsbDevice) {
        if (isUSBSerialDevice(device)) {
            val deviceName = device.productName ?: "Unknown"
            android.util.Log.d("PolyField", "USB serial device attached: $deviceName")
            
            if (!usbManager.hasPermission(device)) {
                usbManager.requestPermission(device, permissionIntent)
            } else {
                onUSBDevicePermissionGranted(device)
            }
        }
    }
    
    private fun onUSBDeviceDetached(device: UsbDevice) {
        val currentDevice = viewModel.uiState.value.connectedDevice
        if (device == currentDevice) {
            viewModel.updateDevice(null, true)
            android.util.Log.d("PolyField", "USB device disconnected - switching to demo mode")
        }
    }
    
    private fun onUSBDevicePermissionGranted(device: UsbDevice) {
        val deviceName = device.productName ?: "Serial Device"
        viewModel.updateDevice(device, false)
        
        // Auto-connect to EDM device if only one device found
        val deviceList = usbManager.deviceList
        val serialDevices = deviceList.values.filter { isUSBSerialDevice(it) }
        
        if (serialDevices.size == 1) {
            android.util.Log.d("PolyField", "Single USB serial device detected: $deviceName - Auto-connecting to EDM")
            
            // Update device config with auto-detected device info
            val devicePath = "/dev/ttyUSB0" // Default path for USB serial devices
            val autoDetectedConfig = DeviceState(
                connected = false, // Will be set to true after successful connection
                connectionType = "serial",
                serialPort = devicePath,
                ipAddress = "192.168.1.100", // Default fallback
                port = 8080 // Default fallback
            )
            
            // Update device configuration
            viewModel.updateDeviceConfig("edm", autoDetectedConfig)
            
            // Auto-connect to EDM device
            viewModel.updateDeviceConnection("edm", true)
            
            android.util.Log.d("PolyField", "Auto-connecting EDM device at $devicePath")
        } else if (serialDevices.size > 1) {
            android.util.Log.d("PolyField", "Multiple USB serial devices detected (${serialDevices.size}) - Manual selection required")
        }
        
        android.util.Log.d("PolyField", "USB device connected: $deviceName")
    }
}

// Theme Definition - Matching original exactly
@Composable
fun PolyFieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1976D2),           // Exact blue from original
            background = Color(0xFFF5F5F5),        // Exact background gray
            surface = Color.White,                  // White cards
            secondary = Color(0xFFFFEB3B),         // Demo yellow
            tertiary = Color(0xFF4CAF50),          // Success green
            onPrimary = Color.White,
            onBackground = Color(0xFF333333),      // Text color
            onSurface = Color(0xFF333333)
        ),
        content = content
    )
}

// Main App Composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolyFieldApp(viewModel: AppViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        PolyFieldHeaderExact(
            currentScreen = uiState.currentScreen,
            isDemoMode = uiState.isDemoMode,
            canGoBack = canGoBack(uiState.currentScreen),
            onBackClick = { navigateBack(viewModel, uiState) },
            onToggleDemoMode = { viewModel.toggleDemoMode() }
        )
        
        // Main Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (uiState.currentScreen) {
                "SELECT_EVENT_TYPE" -> SelectEventTypeScreenExact(
                    onEventSelected = { eventType ->
                        viewModel.updateEventType(eventType)
                        viewModel.updateScreen("DEVICE_SETUP")
                    },
                    onSettingsClick = {
                        viewModel.updateScreen("SETTINGS")
                    }
                )
                "DEVICE_SETUP" -> DeviceSetupScreenExact(
                    eventType = uiState.eventType,
                    devices = uiState.devices,
                    isDemoMode = uiState.isDemoMode,
                    onConnectDevice = { deviceType ->
                        viewModel.updateDeviceConnection(deviceType, !uiState.devices.let {
                            when (deviceType) {
                                "edm" -> it.edm.connected
                                "wind" -> it.wind.connected
                                "scoreboard" -> it.scoreboard.connected
                                else -> false
                            }
                        })
                    },
                    onToggleDeviceSetupModal = { deviceType -> viewModel.toggleDeviceSetupModal(deviceType) },
                    onContinue = {
                        val nextScreen = if (uiState.eventType == "Throws") "CALIBRATION_SELECT_CIRCLE" else "MEASUREMENT"
                        viewModel.updateScreen(nextScreen)
                    }
                )
                "CALIBRATION_SELECT_CIRCLE" -> CalibrationSelectCircleScreenExact(
                    selectedCircle = uiState.calibration.circleType,
                    isDoubleReadMode = uiState.settings.isDoubleReadMode,
                    onCircleSelected = { circleType ->
                        viewModel.updateCircleType(circleType)
                    }
                )
                "CALIBRATION_SET_CENTRE" -> CalibrationSetCentreScreenExact(
                    calibration = uiState.calibration,
                    isLoading = uiState.isLoading,
                    onSetCentre = { viewModel.setCentre() },
                    onResetCentre = { 
                        viewModel.resetCalibration()
                        viewModel.updateCircleType(uiState.calibration.circleType)
                    }
                )
                "CALIBRATION_VERIFY_EDGE" -> CalibrationVerifyEdgeScreenExact(
                    calibration = uiState.calibration,
                    isLoading = uiState.isLoading,
                    isDoubleReadMode = uiState.settings.isDoubleReadMode,
                    onVerifyEdge = { viewModel.verifyEdge() },
                    onResetEdge = { 
                        viewModel.resetCalibration()
                        viewModel.updateCircleType(uiState.calibration.circleType)
                    }
                )
                "CALIBRATION_EDGE_RESULTS" -> CalibrationEdgeResultsScreenExact(
                    calibration = uiState.calibration,
                    onContinue = {
                        viewModel.updateScreen("MEASUREMENT")
                    },
                    onRemeasure = {
                        viewModel.updateScreen("CALIBRATION_VERIFY_EDGE")
                    }
                )
                "MEASUREMENT" -> MeasurementScreenExact(
                    eventType = uiState.eventType,
                    calibration = uiState.calibration,
                    measurement = uiState.measurement,
                    windMeasurement = uiState.windMeasurement,
                    throwCoordinates = uiState.throwCoordinates,
                    isLoading = uiState.isLoading,
                    onMeasureDistance = { viewModel.measureDistance() },
                    onMeasureWind = { viewModel.measureWind() },
                    onResetSession = { viewModel.resetSession() },
                    onShowHeatMap = { viewModel.toggleHeatMap() },
                    onNewEvent = {
                        viewModel.resetSession()
                        viewModel.resetCalibration()
                        viewModel.updateScreen("SELECT_EVENT_TYPE")
                    }
                )
                "SETTINGS" -> SettingsScreen(
                    isDemoMode = uiState.isDemoMode,
                    isDoubleReadMode = uiState.settings.isDoubleReadMode,
                    onDemoModeToggle = { viewModel.toggleDemoMode() },
                    onDoubleReadModeToggle = { enabled ->
                        viewModel.updateSettings(uiState.settings.copy(isDoubleReadMode = enabled))
                    },
                    onBackClick = {
                        viewModel.updateScreen("SELECT_EVENT_TYPE")
                    }
                )
            }
        }
        
        // Device Configuration Modal
        if (uiState.deviceSetupVisible) {
            DeviceConfigurationModal(
                onDismiss = { viewModel.toggleDeviceSetupModal() },
                devices = uiState.devices,
                detectedDevices = uiState.detectedDevices,
                initialSelectedDevice = uiState.selectedDeviceForConfig,
                onUpdateDevice = { deviceType, deviceConfig ->
                    viewModel.updateDeviceConfig(deviceType, deviceConfig)
                    viewModel.toggleDeviceSetupModal()
                },
                onRefreshUsb = { viewModel.refreshUsbDevices() }
            )
        }
        
        // Error Dialog
        if (uiState.errorMessage != null && uiState.errorTitle != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissErrorDialog() },
                title = {
                    Text(
                        text = uiState.errorTitle ?: "Error",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = uiState.errorMessage ?: "An error occurred",
                        fontSize = 16.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissErrorDialog() }) {
                        Text("OK")
                    }
                }
            )
        }
        
        // Bottom Navigation - Hide on initial screen
        if (showBottomNavigation(uiState.currentScreen)) {
            BottomNavigationExact(
            currentScreen = uiState.currentScreen,
            eventType = uiState.eventType,
            canGoBack = canGoBack(uiState.currentScreen),
            canGoForward = canGoForward(uiState.currentScreen),
            showHeatMapButton = uiState.currentScreen == "MEASUREMENT" && uiState.eventType == "Throws" && uiState.throwCoordinates.isNotEmpty(),
            onBackClick = { navigateBack(viewModel, uiState) },
            onNextClick = { navigateForward(viewModel, uiState) },
            onHeatMapClick = { viewModel.toggleHeatMap() },
            onNewEventClick = {
                viewModel.resetSession()
                viewModel.resetCalibration()
                viewModel.updateScreen("SELECT_EVENT_TYPE")
            }
        )
        }
    }
}

// Helper functions for navigation and UI state
private fun getScreenTitle(screen: String, eventType: String, circleType: String): String {
    return when (screen) {
        "SELECT_EVENT_TYPE" -> "PolyField"
        "DEVICE_SETUP" -> "Device Setup - $eventType"
        "CALIBRATION_SELECT_CIRCLE" -> "Circle Selection"
        "CALIBRATION_SET_CENTRE" -> "Set Circle Centre - ${circleType.replace("_", " ")}"
        "CALIBRATION_VERIFY_EDGE" -> "Verify Circle Edge - ${circleType.replace("_", " ")}"
        "CALIBRATION_EDGE_RESULTS" -> "Calibration Results - ${circleType.replace("_", " ")}"
        "MEASUREMENT" -> "Measurement - ${circleType.replace("_", " ")}"
        else -> "PolyField"
    }
}

private fun canGoBack(screen: String): Boolean {
    return screen != "SELECT_EVENT_TYPE" && screen != "SETTINGS"
}

private fun canGoForward(screen: String): Boolean {
    return when (screen) {
        "SELECT_EVENT_TYPE", "DEVICE_SETUP", "CALIBRATION_SELECT_CIRCLE", 
        "CALIBRATION_SET_CENTRE", "CALIBRATION_VERIFY_EDGE", "CALIBRATION_EDGE_RESULTS" -> true
        else -> false
    }
}

private fun showBottomNavigation(screen: String): Boolean {
    return screen != "SELECT_EVENT_TYPE" && screen != "SETTINGS" // Hide on initial screen and settings
}

private fun navigateBack(viewModel: AppViewModel, uiState: AppState) {
    val previousScreen = when (uiState.currentScreen) {
        "DEVICE_SETUP" -> "SELECT_EVENT_TYPE"
        "CALIBRATION_SELECT_CIRCLE" -> "DEVICE_SETUP"
        "CALIBRATION_SET_CENTRE" -> "CALIBRATION_SELECT_CIRCLE"
        "CALIBRATION_VERIFY_EDGE" -> "CALIBRATION_SET_CENTRE"
        "CALIBRATION_EDGE_RESULTS" -> "CALIBRATION_VERIFY_EDGE"
        "MEASUREMENT" -> {
            if (uiState.eventType == "Throws") "CALIBRATION_VERIFY_EDGE" else "DEVICE_SETUP"
        }
        else -> uiState.currentScreen
    }
    viewModel.updateScreen(previousScreen)
}

private fun navigateForward(viewModel: AppViewModel, uiState: AppState) {
    val nextScreen = when (uiState.currentScreen) {
        "SELECT_EVENT_TYPE" -> "DEVICE_SETUP"
        "DEVICE_SETUP" -> if (uiState.eventType == "Throws") "CALIBRATION_SELECT_CIRCLE" else "MEASUREMENT"
        "CALIBRATION_SELECT_CIRCLE" -> {
            if (uiState.calibration.circleType.isNotEmpty()) "CALIBRATION_SET_CENTRE" 
            else uiState.currentScreen // Don't advance if no circle selected
        }
        "CALIBRATION_SET_CENTRE" -> "CALIBRATION_VERIFY_EDGE"
        "CALIBRATION_VERIFY_EDGE" -> "MEASUREMENT" // Skip results screen, go direct to measurement
        "CALIBRATION_EDGE_RESULTS" -> "MEASUREMENT"
        else -> uiState.currentScreen
    }
    viewModel.updateScreen(nextScreen)
}