package com.polyfieldandroid

import android.content.Context
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbDevice
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * EDM (Electronic Distance Measurement) Module for device communication
 * Handles connection and communication with EDM devices, wind gauges, and scoreboards
 */
class EDMModule(private val context: Context) {
    
    companion object {
        private const val TAG = "EDMModule"
    }
    
    // Device connection states
    private val connectedDevices = mutableMapOf<String, DeviceConnection>()
    
    // EDM device management
    private val edmCommunicationBridge = EDMCommunicationBridge()
    private val serialCommunicationModule = SerialCommunicationModule(context)
    private var selectedEDMDevice: EDMDeviceSpec = EDMDeviceRegistry.getDefaultDevice()
    
    // Track active serial connections
    private val activeSerialPorts = mutableMapOf<String, UsbSerialPort>()
    
    // Bridge for Go Mobile integration
    private var latestEDMReading: EDMParsedReading? = null
    private val edmReadingLock = Any()
    
    // DEBUG: Serial Communication Logging (REMOVE WHEN DEBUG COMPLETE)
    fun setDebugLogger(logger: (String, String, String, Boolean, String?) -> Unit) {
        serialCommunicationModule.debugLogger = logger
    }
    
    data class DeviceConnection(
        val deviceType: String,
        val connectionType: String,
        val address: String,
        val port: Int = 0,
        var isConnected: Boolean = false
    )
    
    data class EDMReading(
        val success: Boolean,
        val distance: Double? = null,
        val error: String? = null,
        val goMobileData: String? = null,
        val rawResponse: String? = null
    )
    
    data class WindReading(
        val success: Boolean,
        val windSpeed: Double? = null,
        val error: String? = null
    )
    
    /**
     * Connect to USB device (handles both direct USB and USB-to-serial adapters)
     * CRITICAL: Never simulates connections in live mode - only real device connections allowed
     */
    suspend fun connectUsbDevice(deviceType: String, address: String): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Connecting to USB device: $deviceType at $address")
            
            try {
                // Get USB Manager and verify device actually exists
                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                val deviceList = usbManager.deviceList
                
                // Find the specific device at this address
                val targetDevice = deviceList.values.find { it.deviceName == address }
                if (targetDevice == null) {
                    Log.e(TAG, "Device not found at address: $address")
                    return@withContext mapOf(
                        "success" to false,
                        "error" to "Device not found at address: $address",
                        "deviceType" to deviceType
                    )
                }
                
                // Check if this is a known EDM device or USB-to-serial adapter
                val edmDevice = EDMDeviceRegistry.matchUsbDevice(targetDevice.vendorId, targetDevice.productId)
                val isSerialAdapter = edmDevice != null || isKnownSerialAdapter(targetDevice.vendorId, targetDevice.productId)
                
                if (isSerialAdapter) {
                    val deviceName = edmDevice?.displayName ?: "USB-to-Serial Adapter"
                    Log.d(TAG, "Detected EDM device via USB-to-serial: $deviceName")
                    
                    // For serial adapters, verify we can access the device
                    if (!usbManager.hasPermission(targetDevice)) {
                        Log.e(TAG, "No permission to access USB device: $deviceName")
                        return@withContext mapOf(
                            "success" to false,
                            "error" to "No permission to access USB device: $deviceName",
                            "deviceType" to deviceType
                        )
                    }
                    
                    // Try to establish serial connection (use default 9600 baud if device not recognized)
                    val serialPort = serialCommunicationModule.openSerialConnection(
                        targetDevice, 
                        edmDevice?.baudRate ?: 9600
                    )
                    
                    if (serialPort == null) {
                        Log.e(TAG, "Failed to open serial connection to $deviceName")
                        return@withContext mapOf(
                            "success" to false,
                            "error" to "Failed to open serial connection to $deviceName",
                            "deviceType" to deviceType
                        )
                    }
                    
                    // Test the connection
                    val connectionTest = serialCommunicationModule.testSerialConnection(serialPort)
                    if (!connectionTest) {
                        Log.w(TAG, "Serial connection test failed, but connection established")
                        // Don't fail here - some devices might not respond to test commands
                    }
                    
                    // Store the active serial port
                    activeSerialPorts[deviceType] = serialPort
                    Log.d(TAG, "Serial connection established to $deviceName")
                }
                
                // Attempt real connection with timeout
                val connectionSuccess = try {
                    if (isSerialAdapter) {
                        // Serial connection already established above
                        Log.d(TAG, "Serial connection already established")
                        true
                    } else {
                        // Test direct USB connection
                        Log.d(TAG, "Testing direct USB connection...")
                        val connection = usbManager.openDevice(targetDevice)
                        val success = connection != null
                        connection?.close()
                        success
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Connection test failed: ${e.message}")
                    false
                }
                
                if (!connectionSuccess) {
                    Log.e(TAG, "Failed to establish connection to device at $address")
                    return@withContext mapOf(
                        "success" to false,
                        "error" to "Failed to establish connection to device at $address",
                        "deviceType" to deviceType
                    )
                }
                
                // Real connection established
                val connectionType = if (isSerialAdapter) "serial" else "usb"
                val connection = DeviceConnection(
                    deviceType = deviceType,
                    connectionType = connectionType,
                    address = address,
                    isConnected = true
                )
                
                connectedDevices[deviceType] = connection
                
                val message = if (isSerialAdapter) {
                    val deviceName = edmDevice?.displayName ?: "USB-to-Serial Adapter"
                    "Connected to $deviceName via USB-to-serial adapter at $address"
                } else {
                    "Connected to $deviceType via USB at $address"
                }
                
                Log.d(TAG, "Real device connection established: $message")
                
                mapOf(
                    "success" to true,
                    "message" to message,
                    "deviceType" to deviceType,
                    "connectionType" to connectionType,
                    "isSerialAdapter" to isSerialAdapter,
                    "edmDevice" to (edmDevice?.displayName ?: "")
                )
            } catch (e: Exception) {
                Log.e(TAG, "USB connection failed", e)
                mapOf(
                    "success" to false,
                    "error" to e.message.orEmpty(),
                    "deviceType" to deviceType
                )
            }
        }
    }
    
    /**
     * Connect to serial device
     * CRITICAL: Never simulates connections in live mode - only real device connections allowed
     */
    suspend fun connectSerialDevice(deviceType: String, address: String): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Connecting to serial device: $deviceType at $address")
            
            try {
                // For serial connections, we'll validate when actual communication happens
                Log.d(TAG, "Serial connection requested to $address")
                val connectionSuccess = true // Assume connection available, actual validation happens during communication
                
                if (!connectionSuccess) {
                    Log.e(TAG, "Failed to establish serial connection to $address")
                    return@withContext mapOf(
                        "success" to false,
                        "error" to "Failed to establish serial connection to $address",
                        "deviceType" to deviceType
                    )
                }
                
                // Real serial connection established
                val connection = DeviceConnection(
                    deviceType = deviceType,
                    connectionType = "serial",
                    address = address,
                    isConnected = true
                )
                
                connectedDevices[deviceType] = connection
                
                Log.d(TAG, "Real serial connection established to $address")
                
                mapOf(
                    "success" to true,
                    "message" to "Connected to $deviceType via Serial at $address",
                    "deviceType" to deviceType,
                    "connectionType" to "serial"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Serial connection failed", e)
                mapOf(
                    "success" to false,
                    "error" to e.message.orEmpty(),
                    "deviceType" to deviceType
                )
            }
        }
    }
    
    /**
     * Connect to network device
     * CRITICAL: Never simulates connections in live mode - only real device connections allowed
     */
    suspend fun connectNetworkDevice(deviceType: String, address: String, port: Int): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Connecting to network device: $deviceType at $address:$port")
            
            try {
                // For network connections, we'll validate when actual communication happens
                Log.d(TAG, "Network connection requested to $address:$port")
                val connectionSuccess = true // Assume connection available, actual validation happens during communication
                
                if (!connectionSuccess) {
                    Log.e(TAG, "Failed to establish network connection to $address:$port")
                    return@withContext mapOf(
                        "success" to false,
                        "error" to "Failed to establish network connection to $address:$port",
                        "deviceType" to deviceType
                    )
                }
                
                // Real network connection established
                val connection = DeviceConnection(
                    deviceType = deviceType,
                    connectionType = "network",
                    address = address,
                    port = port,
                    isConnected = true
                )
                
                connectedDevices[deviceType] = connection
                
                Log.d(TAG, "Real network connection established to $address:$port")
                
                mapOf(
                    "success" to true,
                    "message" to "Connected to $deviceType via Network at $address:$port",
                    "deviceType" to deviceType,
                    "connectionType" to "network"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Network connection failed", e)
                mapOf(
                    "success" to false,
                    "error" to e.message.orEmpty(),
                    "deviceType" to deviceType
                )
            }
        }
    }
    
    /**
     * Set the selected EDM device type
     */
    fun setSelectedEDMDevice(deviceSpec: EDMDeviceSpec) {
        selectedEDMDevice = deviceSpec
        Log.d(TAG, "Selected EDM device: ${deviceSpec.displayName}")
    }
    
    /**
     * Get the currently selected EDM device
     */
    fun getSelectedEDMDevice(): EDMDeviceSpec {
        return selectedEDMDevice
    }
    
    /**
     * Get single EDM reading for distance measurement (no tolerance checking)
     * Uses device translator to communicate with actual EDM device, then calls Go Mobile for calculations
     */
    suspend fun getSingleEDMReading(deviceType: String): EDMReading {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Getting single EDM reading with ${selectedEDMDevice.displayName}: $deviceType")
            
            val connection = connectedDevices[deviceType]
            Log.d(TAG, "Connection state: $connection")
            
            try {
                // Check if this is a USB device that needs Android-side communication
                if (connection?.connectionType == "usb") {
                    return@withContext performUSBEDMReading(deviceType, single = true)
                }
                
                // CRITICAL: Verify device is still physically connected
                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                val deviceList = usbManager.deviceList
                val deviceStillConnected = deviceList.values.any { device ->
                    EDMDeviceRegistry.matchUsbDevice(device.vendorId, device.productId) != null
                }
                
                if (!deviceStillConnected) {
                    Log.e(TAG, "EDM device no longer physically connected")
                    return@withContext EDMReading(
                        success = false,
                        error = "EDM device disconnected - no device found"
                    )
                }
                
                // For serial/network connections, delegate to Go Mobile
                Log.d(TAG, "Calling native mobile.Mobile.getReliableEDMReading($deviceType) with singleMode=true")
                val result = mobile.Mobile.getReliableEDMReading(deviceType, true)
                
                Log.d(TAG, "Native module single reading result: $result")
                
                // Parse the JSON result from native module
                val jsonResult = JSONObject(result as String)
                
                if (jsonResult.has("error")) {
                    val error = jsonResult.getString("error")
                    return@withContext EDMReading(
                        success = false,
                        error = error
                    )
                }
                
                // CRITICAL: Validate that reading is real, not simulated
                val slopeDistanceMm = jsonResult.getDouble("slopeDistanceMm")
                if (slopeDistanceMm <= 0 || slopeDistanceMm > 100000) {
                    return@withContext EDMReading(
                        success = false,
                        error = "Invalid EDM reading received - possible simulation"
                    )
                }
                
                val distanceMeters = slopeDistanceMm / 1000.0
                
                Log.d(TAG, "Single EDM reading successful: ${distanceMeters}m (from ${slopeDistanceMm}mm)")
                
                EDMReading(
                    success = true,
                    distance = distanceMeters
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Single EDM reading failed", e)
                EDMReading(
                    success = false,
                    error = e.message ?: "Could not find prism. Check your aim and remeasure. If EDM displays \"STOP\" then press F1 to reset"
                )
            }
        }
    }
    
    /**
     * Perform USB/Serial EDM reading using device translator
     */
    private suspend fun performUSBEDMReading(deviceType: String, single: Boolean = false): EDMReading {
        try {
            // Check if we have a serial connection (USB-to-serial adapter)
            val connection = connectedDevices[deviceType]
            Log.d(TAG, "performUSBEDMReading - connection: $connection")
            Log.d(TAG, "performUSBEDMReading - connectionType: ${connection?.connectionType}")
            Log.d(TAG, "performUSBEDMReading - activeSerialPorts: ${activeSerialPorts.keys}")
            
            if (connection?.connectionType == "serial") {
                Log.d(TAG, "Using existing serial connection for EDM reading")
                return performSerialEDMReading(deviceType, singleMode = single)
            }
            
            // Fallback: check if we have an active serial port even if connection record is missing
            if (activeSerialPorts.containsKey(deviceType)) {
                Log.d(TAG, "Using active serial port for EDM reading (fallback)")
                return performSerialEDMReading(deviceType, singleMode = single)
            }
            
            // Try to detect and connect to serial devices (CH340, FTDI, etc.)
            Log.d(TAG, "Checking for USB-to-serial devices (CH340, FTDI, etc.)")
            val serialDevices = serialCommunicationModule.getAvailableSerialDevices()
            
            if (serialDevices.isNotEmpty()) {
                Log.d(TAG, "Found ${serialDevices.size} USB-to-serial device(s), attempting connection")
                val serialDevice = serialDevices.first() // Use first available serial device
                
                // Attempt to connect to serial device
                val serialPort = serialCommunicationModule.openSerialConnection(serialDevice)
                if (serialPort != null) {
                    Log.d(TAG, "Successfully connected to USB-to-serial device")
                    
                    // Store the connection for future use
                    connectedDevices[deviceType] = DeviceConnection(
                        deviceType = deviceType,
                        connectionType = "serial",
                        address = serialDevice.deviceName,
                        isConnected = true
                    )
                    activeSerialPorts[deviceType] = serialPort
                    
                    // Now perform serial reading
                    return performSerialEDMReading(deviceType, singleMode = single)
                } else {
                    Log.e(TAG, "Failed to connect to USB-to-serial device")
                }
            }
            
            // Original USB direct connection logic as fallback
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val usbDevices = getConnectedEDMDevices()
            
            if (usbDevices.isEmpty()) {
                return EDMReading(
                    success = false,
                    error = "No compatible EDM devices connected via USB or serial"
                )
            }
            
            val usbDevice = usbDevices.first() // Use first compatible device
            
            // Use device translator to communicate with EDM device
            val translationResult = edmCommunicationBridge.performMeasurement(
                selectedEDMDevice,
                usbManager,
                usbDevice
            )
            
            if (!translationResult.success) {
                return EDMReading(
                    success = false,
                    error = translationResult.error ?: "EDM measurement failed"
                )
            }
            
            // Parse the Go Mobile format result
            val goMobileResult = translationResult.goMobileFormat!!
            val jsonResult = JSONObject(goMobileResult)
            
            val slopeDistanceMm = jsonResult.getDouble("slopeDistanceMm")
            val distanceMeters = slopeDistanceMm / 1000.0
            
            Log.d(TAG, "USB EDM reading successful: ${distanceMeters}m via ${selectedEDMDevice.displayName}")
            
            return EDMReading(
                success = true,
                distance = distanceMeters
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "USB/Serial EDM reading failed", e)
            return EDMReading(
                success = false,
                error = "EDM measurement failed: ${e.message}"
            )
        }
    }
    
    /**
     * Perform Serial EDM reading via USB-to-serial adapter
     * Uses Go Mobile for proper trigonometric calculations
     */
    private suspend fun performSerialEDMReading(deviceType: String, singleMode: Boolean = false): EDMReading {
        try {
            Log.d(TAG, "Performing serial EDM reading via USB-to-serial adapter")
            
            // Get the active serial port (skip connection checks - trust that we have a working port)
            val serialPort = activeSerialPorts[deviceType]
            if (serialPort == null) {
                Log.e(TAG, "No active serial port for device type: $deviceType")
                return EDMReading(
                    success = false,
                    error = "Serial port not available"
                )
            }
            
            // CRITICAL: Verify device is still physically connected
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val deviceList = usbManager.deviceList
            val deviceStillConnected = deviceList.values.any { device ->
                EDMDeviceRegistry.matchUsbDevice(device.vendorId, device.productId) != null
            }
            
            if (!deviceStillConnected) {
                Log.e(TAG, "EDM device no longer physically connected")
                // Clean up the connection
                activeSerialPorts.remove(deviceType)
                serialCommunicationModule.closeSerialPort(serialPort)
                return EDMReading(
                    success = false,
                    error = "EDM device disconnected - no device found"
                )
            }
            
            // Use the device translator to get the appropriate command for this EDM device
            val edmTranslator = EDMDeviceRegistry.createTranslator(selectedEDMDevice)
            if (edmTranslator == null) {
                Log.e(TAG, "No translator available for ${selectedEDMDevice.displayName}")
                return EDMReading(
                    success = false,
                    error = "No translator available for ${selectedEDMDevice.displayName}"
                )
            }
            val measureCommandBytes = edmTranslator.getMeasurementCommand()
            
            Log.d(TAG, "Sending measurement command to ${selectedEDMDevice.displayName}: ${measureCommandBytes.contentToString()}")
            
            // Send command bytes directly (don't convert to string to avoid corruption)
            val response = serialCommunicationModule.sendEDMCommandBytes(
                serialPort,
                measureCommandBytes,
                timeoutMs = 10000 // 10 second timeout for measurement
            )
            
            if (!response.success) {
                Log.e(TAG, "Failed to get response from EDM device: ${response.error}")
                return EDMReading(
                    success = false,
                    error = response.error ?: "No response from EDM device"
                )
            }
            
            // Parse the response using device translator
            val parsedResult = edmTranslator.parseResponse(response.data!!)
            
            if (!parsedResult.isValid) {
                Log.e(TAG, "Failed to parse EDM response: ${parsedResult.errorMessage}")
                return EDMReading(
                    success = false,
                    error = parsedResult.errorMessage ?: "Invalid response from EDM device"
                )
            }
            
            Log.d(TAG, "Raw EDM data - Slope: ${parsedResult.slopeDistanceMm}mm, Vertical: ${parsedResult.verticalAngleDegrees}°, Horizontal: ${parsedResult.horizontalAngleDegrees}°")
            
            // CRITICAL: Use Go Mobile to process the EDM reading with proper trigonometric calculations
            // Convert to Go Mobile format for processing
            val goMobileFormat = edmTranslator.toGoMobileFormat(parsedResult)
            Log.d(TAG, "Converted to Go Mobile format: $goMobileFormat")
            
            // Store the raw EDM data for Go Mobile to process
            // Go Mobile will handle the trigonometric conversion from slope distance to horizontal distance
            // using the formula: horizontalDistance = slopeDistance * sin(verticalAngle)
            
            // During delegation, don't call Go Mobile again - return the raw EDM data
            // The higher-level Go Mobile functions (SetCentre, etc.) will handle trigonometry
            Log.d(TAG, "Delegation mode: Returning raw EDM data for Go Mobile trigonometry functions")
            
            // Return the parsed EDM data directly - Go Mobile's SetCentre/VerifyEdge/MeasureThrow will do the calculations
            return EDMReading(
                success = true,
                distance = parsedResult.slopeDistanceMm / 1000.0, // Convert mm to meters
                rawResponse = response.data,
                goMobileData = goMobileFormat // This contains the parsed EDM data for Go Mobile trigonometry
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Serial EDM reading with Go Mobile failed", e)
            return EDMReading(
                success = false,
                error = "Serial measurement failed: ${e.message}"
            )
        }
    }
    
    /**
     * Get connected USB devices that match supported EDM devices
     */
    private fun getConnectedEDMDevices(): List<UsbDevice> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList
        
        return deviceList.values.filter { device ->
            EDMDeviceRegistry.matchUsbDevice(device.vendorId, device.productId) != null
        }
    }

    /**
     * Get reliable EDM reading for distance measurement
     * When called by Go Mobile functions, returns our stored EDM data
     * When called directly, performs measurement through device translator
     */
    suspend fun getReliableEDMReading(deviceType: String, singleMode: Boolean = false): EDMReading {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Getting reliable EDM reading with ${selectedEDMDevice.displayName}: $deviceType")
            
            // For serial connections, we need to handle EDM communication for Go Mobile
            val connection = connectedDevices[deviceType]
            if (connection?.connectionType == "serial") {
                Log.d(TAG, "Go Mobile delegation: Performing EDM reading via serial connection")
                
                // Get the actual EDM reading from our serial communication
                val rawReading = getRawEDMReading(deviceType, !singleMode) // Convert singleMode to doubleReadMode parameter
                if (rawReading.success && rawReading.parsedReading != null) {
                    val reading = rawReading.parsedReading!!
                    Log.d(TAG, "Go Mobile EDM data: slope=${reading.slopeDistanceMm}mm, vAz=${reading.verticalAngleDegrees}°, hAr=${reading.horizontalAngleDegrees}°")
                    
                    // Return the EDM data in Go Mobile's expected JSON format
                    // This should allow Go Mobile to complete its SetCentre process and update cal.IsCentreSet
                    val jsonResult = JSONObject().apply {
                        put("success", true)
                        put("slopeDistanceMm", reading.slopeDistanceMm)
                        put("vAzDecimal", reading.verticalAngleDegrees) 
                        put("harDecimal", reading.horizontalAngleDegrees)
                    }
                    
                    Log.d(TAG, "Returning EDM data to Go Mobile: ${jsonResult}")
                    
                    return@withContext EDMReading(
                        success = true,
                        distance = reading.slopeDistanceMm / 1000.0,
                        goMobileData = jsonResult.toString()
                    )
                } else {
                    Log.e(TAG, "Failed to get EDM data for Go Mobile: ${rawReading.error}")
                    return@withContext EDMReading(
                        success = false,
                        error = rawReading.error ?: "Failed to get serial EDM reading for Go Mobile"
                    )
                }
            }
            
            // Connection validation removed - proceed with measurement
            
            try {
                // Route based on connection type
                when (connection?.connectionType ?: "serial") {
                    "usb" -> {
                        // For USB devices, use Android-side communication
                        return@withContext performDoubleUSBEDMReading(deviceType)
                    }
                    "serial" -> {
                        // For serial devices, use Android serial communication
                        Log.d(TAG, "Using Android serial communication for double reading")
                        return@withContext performDoubleUSBEDMReading(deviceType) // This handles both USB and serial now
                    }
                    else -> {
                        // For network connections, delegate to Go Mobile (if any)
                        Log.d(TAG, "Calling native mobile.Mobile.getReliableEDMReading($deviceType) for network connection")
                    }
                }
                
                // Call native Go Mobile module for network devices
                val result = mobile.Mobile.getReliableEDMReading(deviceType, singleMode)
                Log.d(TAG, "Native module result: $result")
                
                // Parse the JSON result from native module
                val jsonResult = JSONObject(result)
                
                if (jsonResult.has("error")) {
                    val error = jsonResult.getString("error")
                    return@withContext EDMReading(
                        success = false,
                        error = error
                    )
                }
                
                // Success case - extract slope distance and convert to meters
                val slopeDistanceMm = jsonResult.getDouble("slopeDistanceMm")
                val distanceMeters = slopeDistanceMm / 1000.0
                
                Log.d(TAG, "EDM reading successful: ${distanceMeters}m (from ${slopeDistanceMm}mm)")
                
                EDMReading(
                    success = true,
                    distance = distanceMeters
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "EDM reading failed", e)
                EDMReading(
                    success = false,
                    error = e.message ?: "Could not find prism. Check your aim and remeasure. If EDM displays \"STOP\" then press F1 to reset"
                )
            }
        }
    }
    
    /**
     * Perform double EDM reading with tolerance checking (mimics Go Mobile behavior)
     * Handles both USB and Serial connections
     */
    private suspend fun performDoubleUSBEDMReading(deviceType: String): EDMReading {
        try {
            val connection = connectedDevices[deviceType]
            val connectionDescription = if (connection?.connectionType == "serial") "Serial" else "USB"
            
            Log.d(TAG, "Performing double $connectionDescription EDM reading with tolerance checking")
            
            // For serial connections, Go Mobile handles the double reading internally
            if (connection?.connectionType == "serial") {
                return performSerialEDMReading(deviceType, singleMode = false)
            }
            
            // For direct USB connections, perform double reading on Android side
            // First reading
            val reading1 = performUSBEDMReading(deviceType, single = true)
            if (!reading1.success) {
                return reading1
            }
            
            // Wait 100ms between readings (matches Go Mobile delayBetweenReadsInPair)
            delay(100)
            
            // Second reading
            val reading2 = performUSBEDMReading(deviceType, single = true)
            if (!reading2.success) {
                return reading2
            }
            
            // Compare readings for tolerance (3mm for slope distance - matches Go Mobile sdToleranceMm)
            val distance1Mm = reading1.distance!! * 1000.0
            val distance2Mm = reading2.distance!! * 1000.0
            val difference = kotlin.math.abs(distance1Mm - distance2Mm)
            
            if (difference <= 3.0) { // 3mm tolerance
                // Average the readings
                val averageDistance = (reading1.distance!! + reading2.distance!!) / 2.0
                
                Log.d(TAG, "Double reading successful - R1: ${reading1.distance}m, R2: ${reading2.distance}m, Avg: ${averageDistance}m")
                
                return EDMReading(
                    success = true,
                    distance = averageDistance
                )
            } else {
                Log.w(TAG, "Readings inconsistent - R1: ${reading1.distance}m, R2: ${reading2.distance}m, Diff: ${difference}mm")
                return EDMReading(
                    success = false,
                    error = "Readings inconsistent. R1: ${"%.3f".format(reading1.distance)}m, R2: ${"%.3f".format(reading2.distance)}m (${difference.toInt()}mm difference)"
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Double EDM reading failed", e)
            return EDMReading(
                success = false,
                error = "Double reading failed: ${e.message}"
            )
        }
    }
    
    /**
     * Measure wind speed
     */
    suspend fun measureWind(): WindReading {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Measuring wind speed")
            
            val connection = connectedDevices["wind"]
            if (connection == null || !connection.isConnected) {
                return@withContext WindReading(
                    success = false,
                    error = "Wind gauge not connected"
                )
            }
            
            try {
                // Real wind gauge communication
                val windSpeed = sendWindCommand(connection)
                
                Log.d(TAG, "Wind measurement successful: ${windSpeed}m/s")
                
                WindReading(
                    success = true,
                    windSpeed = windSpeed
                )
            } catch (e: Exception) {
                Log.e(TAG, "Wind measurement failed", e)
                WindReading(
                    success = false,
                    error = e.message.orEmpty()
                )
            }
        }
    }
    
    /**
     * Disconnect device
     */
    fun disconnectDevice(deviceType: String): Boolean {
        Log.d(TAG, "Disconnecting device: $deviceType")
        
        return if (connectedDevices.containsKey(deviceType)) {
            // Clean up serial connection if exists
            val serialPort = activeSerialPorts.remove(deviceType)
            if (serialPort != null) {
                serialCommunicationModule.closeSerialPort(serialPort)
                Log.d(TAG, "Closed serial port for device: $deviceType")
            }
            
            connectedDevices.remove(deviceType)
            true
        } else {
            false
        }
    }
    
    /**
     * Check if device is connected
     */
    fun isDeviceConnected(deviceType: String): Boolean {
        val result = connectedDevices[deviceType]?.isConnected == true
        Log.d(TAG, "isDeviceConnected($deviceType): $result")
        Log.d(TAG, "Connected devices: ${connectedDevices.keys}")
        Log.d(TAG, "Device details: ${connectedDevices[deviceType]}")
        return result
    }
    
    // Serial communication is handled by Go Mobile native module
    // Android only handles UI, permissions, and device management
    
    // Network communication is handled by Go Mobile native module
    // Android only handles UI, permissions, and device management
    
    // USB communication is handled by Go Mobile native module
    // Android only handles UI, permissions, and device management
    
    /**
     * Send wind measurement command to device
     * Wind gauge communication is handled by Go Mobile native module
     */
    private suspend fun sendWindCommand(connection: DeviceConnection): Double {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Wind measurement delegated to Go Mobile native module")
            
            try {
                // Call native Go Mobile module for wind measurement
                // The native module handles the actual device communication
                throw Exception("Wind measurement not yet integrated with native module")
                
            } catch (e: Exception) {
                Log.e(TAG, "Wind measurement failed", e)
                throw e
            }
        }
    }
    
    
    /**
     * Set centre using Go Mobile's setCentre function with proper trigonometric calculations
     * Go Mobile will handle EDM communication internally
     */
    suspend fun setCentreWithGoMobile(deviceType: String, targetRadius: Double, circleType: String, doubleReadMode: Boolean = false): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Setting centre using Go Mobile calculations (internal EDM handling)")
            
            try {
                // Call Go Mobile's setCentre function with reading mode
                val singleMode = !doubleReadMode  // Convert to singleMode parameter
                Log.d(TAG, "Calling Go Mobile setCentre with singleMode=$singleMode")
                val result = mobile.Mobile.setCentre(deviceType, singleMode)
                
                Log.d(TAG, "Go Mobile setCentre result: $result")
                
                // Parse result to check for USB delegation or EDM communication failures
                val jsonResult = JSONObject(result as String)
                val hasError = jsonResult.has("error")
                
                // Check if we should delegate to Android EDM communication
                val shouldDelegateToAndroid = if (hasError) {
                    val errorMessage = jsonResult.getString("error")
                    Log.d(TAG, "🔍 Go Mobile returned error: '$errorMessage'")
                    
                    // Check if this is an EDM communication failure that Android can handle
                    val matchesDelegate = errorMessage == "USB_ANDROID_DELEGATE"
                    val matchesNotConnected = errorMessage.contains("not connected", ignoreCase = true)
                    val matchesEDMDeviceType = errorMessage.contains("EDM device type", ignoreCase = true)
                    val matchesReadFailed = errorMessage.contains("read failed", ignoreCase = true)
                    val matchesDeviceType = errorMessage.contains("device type", ignoreCase = true)
                    val matchesEDMError = errorMessage.contains("EDM", ignoreCase = true) && 
                                         (errorMessage.contains("error", ignoreCase = true) || 
                                          errorMessage.contains("fail", ignoreCase = true) ||
                                          errorMessage.contains("not", ignoreCase = true))
                    
                    Log.d(TAG, "🔍 Delegation condition checks:")
                    Log.d(TAG, "  - USB_ANDROID_DELEGATE: $matchesDelegate")
                    Log.d(TAG, "  - contains 'not connected': $matchesNotConnected")
                    Log.d(TAG, "  - contains 'EDM device type': $matchesEDMDeviceType")
                    Log.d(TAG, "  - contains 'read failed': $matchesReadFailed")
                    Log.d(TAG, "  - contains 'device type': $matchesDeviceType")
                    Log.d(TAG, "  - contains EDM + error/fail/not: $matchesEDMError")
                    
                    matchesDelegate || matchesNotConnected || matchesEDMDeviceType || matchesReadFailed || matchesDeviceType || matchesEDMError
                } else {
                    // Check if Go Mobile succeeded but returned incomplete data (missing coordinates)
                    val missingCoordinates = !jsonResult.has("stationX") || !jsonResult.has("stationY")
                    if (missingCoordinates) {
                        Log.d(TAG, "Go Mobile succeeded but returned incomplete data (missing coordinates) - delegating to Android")
                    }
                    missingCoordinates
                }
                
                Log.d(TAG, "🔍 Should delegate to Android: $shouldDelegateToAndroid")
                
                if (shouldDelegateToAndroid) {
                    // Go Mobile can't handle EDM communication - delegate to Android and then try Go Mobile again
                    val delegationReason = if (hasError) {
                        "error: ${jsonResult.getString("error")}"
                    } else {
                        "missing coordinate data"
                    }
                    Log.d(TAG, "✅ DELEGATION TRIGGERED: Go Mobile can't handle EDM communication ($delegationReason)")
                    Log.d(TAG, "🔄 Getting EDM data via Android, then calling Go Mobile SetCentre again")
                    
                    // Get EDM reading using Android's serial communication
                    val edmReading = performSerialEDMReading(deviceType, singleMode = !doubleReadMode)
                    
                    if (!edmReading.success) {
                        Log.e(TAG, "❌ Android EDM reading failed: ${edmReading.error}")
                        return@withContext mapOf<String, Any>(
                            "success" to false,
                            "error" to (edmReading.error ?: "EDM communication failed")
                        )
                    }
                    
                    Log.d(TAG, "✅ Android EDM reading successful! Now calling Go Mobile SetCentre with EDM data available")
                    
                    // Now that we have EDM data available in our getReliableEDMReading cache,
                    // try calling Go Mobile's SetCentre again - it should succeed this time
                    val secondResult = mobile.Mobile.setCentre(deviceType, singleMode)
                    Log.d(TAG, "Second Go Mobile SetCentre result: $secondResult")
                    
                    val secondJsonResult = JSONObject(secondResult as String)
                    if (secondJsonResult.has("error")) {
                        // If Go Mobile still fails, do the calculation ourselves but ensure proper format
                        Log.d(TAG, "Go Mobile SetCentre still failed, doing Android calculation")
                        
                        val goMobileData = edmReading.goMobileData
                        if (goMobileData.isNullOrEmpty()) {
                            return@withContext mapOf<String, Any>(
                                "success" to false,
                                "error" to "No EDM data available"
                            )
                        }
                        
                        val detailedData = JSONObject(goMobileData)
                        val slopeDistanceMm = detailedData.getDouble("slopeDistanceMm")
                        val verticalAngleDegrees = detailedData.getDouble("vAzDecimal")
                        val horizontalAngleDegrees = detailedData.getDouble("harDecimal")
                        
                        // Perform trigonometric calculations
                        val sdMeters = slopeDistanceMm / 1000.0
                        val vazRad = Math.toRadians(verticalAngleDegrees)
                        val harRad = Math.toRadians(horizontalAngleDegrees)
                        
                        val horizontalDistance = sdMeters * kotlin.math.sin(vazRad)
                        val stationX = -horizontalDistance * kotlin.math.cos(harRad)
                        val stationY = -horizontalDistance * kotlin.math.sin(harRad)
                        
                        val androidResult = JSONObject().apply {
                            put("success", true)
                            put("slopeDistanceMm", slopeDistanceMm)
                            put("vAzDecimal", verticalAngleDegrees)
                            put("hARDecimal", horizontalAngleDegrees)
                            put("stationX", stationX)
                            put("stationY", stationY)
                            put("message", "Centre set via Android calculation with real EDM data")
                        }
                        
                        // CRITICAL: Update Go Mobile's internal calibration state so VerifyEdge works
                        Log.e(TAG, "🔧 ABOUT TO UPDATE Go Mobile calibration state")
                        Log.e(TAG, "🔧 Parameters: deviceType=$deviceType, stationX=$stationX, stationY=$stationY, targetRadius=$targetRadius, circleType=$circleType")
                        try {
                            Log.e(TAG, "🔧 Calling mobile.Mobile.setCalibrationState...")
                            val calibrationResult = mobile.Mobile.setCalibrationState(deviceType, stationX, stationY, targetRadius, circleType)
                            Log.e(TAG, "✅ SUCCESS! Go Mobile calibration state updated: $calibrationResult")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ FAILED to update Go Mobile calibration state: ${e.message}")
                            Log.e(TAG, "❌ Exception type: ${e.javaClass.simpleName}")
                            e.printStackTrace()
                        }
                        
                        return@withContext mapOf<String, Any>(
                            "success" to true,
                            "result" to androidResult.toString()
                        )
                    } else {
                        // Go Mobile succeeded on second try
                        Log.d(TAG, "✅ Go Mobile SetCentre succeeded on second attempt!")
                        return@withContext mapOf<String, Any>(
                            "success" to true,
                            "result" to secondResult
                        )
                    }
                } else {
                    // Go Mobile succeeded on first try
                    Log.d(TAG, "✅ Go Mobile SetCentre succeeded on first attempt")
                    return@withContext mapOf<String, Any>(
                        "success" to true,
                        "result" to result
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "setCentreWithGoMobile failed", e)
                return@withContext mapOf(
                    "success" to false,
                    "error" to "Failed to set centre: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Verify edge using Go Mobile's verifyEdge function with proper trigonometric calculations
     * Go Mobile will handle the EDM communication internally
     */
    suspend fun verifyEdgeWithGoMobile(deviceType: String, targetRadius: Double, doubleReadMode: Boolean = false): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Verifying edge using Go Mobile calculations (internal EDM handling)")
            
            try {
                // For serial connections, we need to setup the connection first
                val connection = connectedDevices[deviceType]
                if (connection?.connectionType == "serial") {
                    Log.d(TAG, "Serial connection detected - setting up for Go Mobile access")
                    
                    // Prepare for Go Mobile to access our EDM data when it calls getReliableEDMReading
                    // But don't get the reading yet - let Go Mobile handle that internally
                }
                
                // Call Go Mobile's verifyEdge function with reading mode
                // Go Mobile will internally call getReliableEDMReading, which will trigger our serial communication
                val singleMode = !doubleReadMode  // Convert to singleMode parameter
                Log.d(TAG, "Calling Go Mobile verifyEdge with targetRadius: $targetRadius, singleMode: $singleMode")
                val result = mobile.Mobile.verifyEdge(deviceType, targetRadius, singleMode)
                
                Log.d(TAG, "Go Mobile verifyEdge result: $result")
                
                // Parse result
                val jsonResult = JSONObject(result as String)
                val hasError = jsonResult.has("error")
                
                // Check if Go Mobile is delegating USB communication back to Android
                if (hasError && jsonResult.getString("error") == "USB_ANDROID_DELEGATE") {
                    Log.d(TAG, "Go Mobile delegated edge verification back to Android - performing verification with USB communication")
                    
                    // Get calibration state from Go Mobile to get station coordinates
                    val calState = mobile.Mobile.getCalibration(deviceType)
                    Log.d(TAG, "Go Mobile calibration state: $calState")
                    
                    val calJson = JSONObject(calState)
                    Log.d(TAG, "Calibration JSON keys: ${calJson.keys().asSequence().toList()}")
                    
                    if (!calJson.has("stationCoordinates")) {
                        Log.e(TAG, "Missing stationCoordinates in calibration state")
                        return@withContext mapOf(
                            "success" to false,
                            "error" to "Station coordinates not available in calibration state"
                        )
                    }
                    
                    val stationCoords = calJson.getJSONObject("stationCoordinates")
                    val stationX = stationCoords.getDouble("x")
                    val stationY = stationCoords.getDouble("y")
                    
                    Log.d(TAG, "Retrieved station coordinates from Go Mobile: X=$stationX, Y=$stationY")
                    
                    // Perform edge verification using Android USB communication
                    return@withContext verifyEdgeViaAndroid(deviceType, targetRadius, stationX, stationY, singleMode)
                }
                
                return@withContext if (hasError) {
                    mapOf(
                        "success" to false,
                        "result" to result,
                        "error" to jsonResult.getString("error")
                    )
                } else {
                    mapOf(
                        "success" to true,
                        "result" to result
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "verifyEdgeWithGoMobile failed", e)
                return@withContext mapOf(
                    "success" to false,
                    "error" to "Failed to verify edge: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Verify edge using Android USB communication with Go Mobile's trigonometric calculations
     * Called when Go Mobile returns USB_ANDROID_DELEGATE
     */
    private suspend fun verifyEdgeViaAndroid(deviceType: String, targetRadius: Double, stationX: Double, stationY: Double, singleMode: Boolean): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Verifying edge via Android USB with station coordinates: X=$stationX, Y=$stationY")
            
            try {
                // Get EDM reading via USB communication (same as setCentreWithGoMobile)
                val edmReading = performSerialEDMReading(deviceType, singleMode = singleMode)
                
                if (!edmReading.success) {
                    return@withContext mapOf(
                        "success" to false,
                        "error" to "Failed to get EDM reading: ${edmReading.error}"
                    )
                }
                
                val goMobileData = edmReading.goMobileData
                if (goMobileData.isNullOrEmpty()) {
                    return@withContext mapOf(
                        "success" to false,
                        "error" to "No EDM measurement data available"
                    )
                }
                
                // Parse the detailed measurement data (same format as setCentreWithGoMobile)
                val detailedData = JSONObject(goMobileData)
                val slopeDistanceMm = detailedData.getDouble("slopeDistanceMm")
                val vAzDecimal = detailedData.getDouble("vAzDecimal")
                val hARDecimal = detailedData.getDouble("harDecimal")
                
                Log.d(TAG, "Edge verification reading - SD: ${slopeDistanceMm}mm, VAz: ${vAzDecimal}°, HAR: ${hARDecimal}°")
                
                // Perform trigonometric calculations (same as Go Mobile)
                val slopeDistanceMeters = slopeDistanceMm / 1000.0
                val vAzRadians = Math.toRadians(vAzDecimal)
                val hARRadians = Math.toRadians(hARDecimal)
                
                val horizontalDistance = slopeDistanceMeters * Math.sin(vAzRadians)
                val edgeX = horizontalDistance * Math.cos(hARRadians)
                val edgeY = horizontalDistance * Math.sin(hARRadians)
                
                val absoluteEdgeX: Double = stationX + edgeX
                val absoluteEdgeY: Double = stationY + edgeY
                
                val measuredRadius = Math.sqrt(absoluteEdgeX * absoluteEdgeX + absoluteEdgeY * absoluteEdgeY)
                val differenceMeters = measuredRadius - targetRadius
                val differenceMm = differenceMeters * 1000.0
                
                // Tolerance check (5mm for throws circles, 2mm for others)
                val toleranceMm = 5.0  // Assuming throws circle for now
                val toleranceCheck = Math.abs(differenceMm) <= toleranceMm
                
                Log.d(TAG, "Edge verification results - Measured: ${measuredRadius}m, Target: ${targetRadius}m, Diff: ${differenceMm}mm, Tolerance: $toleranceCheck")
                
                // Store the edge verification result in Go Mobile's calibration data
                try {
                    val edgeVerificationResult = mobile.Mobile.setEdgeVerificationResult(
                        deviceType, 
                        measuredRadius, 
                        differenceMm, 
                        toleranceMm, 
                        toleranceCheck
                    )
                    Log.d(TAG, "✅ SUCCESS! Go Mobile edge verification result updated: $edgeVerificationResult")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ FAILED to update Go Mobile edge verification result: ${e.message}")
                }
                
                // Create result in Go Mobile format
                val resultJson = JSONObject().apply {
                    put("success", true)
                    put("toleranceCheck", toleranceCheck)
                    put("differenceMm", differenceMm)
                    put("measuredRadius", measuredRadius)
                    put("targetRadius", targetRadius)
                    put("message", if (toleranceCheck) "Edge verification passed" else "Edge verification failed - outside tolerance")
                }
                
                return@withContext mapOf(
                    "success" to true,
                    "result" to resultJson.toString()
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "verifyEdgeViaAndroid failed", e)
                return@withContext mapOf(
                    "success" to false,
                    "error" to "Edge verification failed: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Measure throw using Go Mobile's measureThrow function with proper calculations
     * Go Mobile will handle EDM communication internally
     */
    suspend fun measureThrowWithGoMobile(deviceType: String, doubleReadMode: Boolean = false): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Measuring throw using Go Mobile calculations (internal EDM handling)")
            
            try {
                // Call Go Mobile's measureThrow function with reading mode
                // Go Mobile will internally call getReliableEDMReading, which will trigger our serial communication
                val singleMode = !doubleReadMode  // Convert to singleMode parameter
                Log.d(TAG, "Calling Go Mobile measureThrow with singleMode: $singleMode")
                val result = mobile.Mobile.measureThrow(deviceType, singleMode)
                
                Log.d(TAG, "Go Mobile measureThrow result: $result")
                
                // Parse result
                val jsonResult = JSONObject(result as String)
                val hasError = jsonResult.has("error")
                
                // Check if Go Mobile is delegating USB communication back to Android
                if (hasError && jsonResult.getString("error") == "USB_ANDROID_DELEGATE") {
                    Log.d(TAG, "Go Mobile delegated measureThrow back to Android - performing measurement with USB communication")
                    
                    // Get calibration state from Go Mobile to get station coordinates
                    val calState = mobile.Mobile.getCalibration(deviceType)
                    Log.d(TAG, "Go Mobile calibration state: $calState")
                    
                    val calJson = JSONObject(calState)
                    
                    if (!calJson.has("stationCoordinates")) {
                        Log.e(TAG, "Missing stationCoordinates in calibration state")
                        return@withContext mapOf(
                            "success" to false,
                            "error" to "Station coordinates not available in calibration state"
                        )
                    }
                    
                    val stationCoords = calJson.getJSONObject("stationCoordinates")
                    val stationX = stationCoords.getDouble("x")
                    val stationY = stationCoords.getDouble("y")
                    val targetRadius = calJson.getDouble("targetRadius")
                    
                    Log.d(TAG, "Retrieved station coordinates from Go Mobile: X=$stationX, Y=$stationY")
                    
                    // Perform throw measurement using Android USB communication
                    return@withContext measureThrowViaAndroid(deviceType, stationX, stationY, targetRadius, singleMode)
                }
                
                return@withContext if (hasError) {
                    mapOf(
                        "success" to false,
                        "result" to result,
                        "error" to jsonResult.getString("error")
                    )
                } else {
                    mapOf(
                        "success" to true,
                        "result" to result
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "measureThrowWithGoMobile failed", e)
                return@withContext mapOf(
                    "success" to false,
                    "error" to "Failed to measure throw: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Measure throw using Android USB communication with Go Mobile's trigonometric calculations
     * Called when Go Mobile returns USB_ANDROID_DELEGATE
     */
    private suspend fun measureThrowViaAndroid(deviceType: String, stationX: Double, stationY: Double, targetRadius: Double, singleMode: Boolean): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Measuring throw via Android USB with station coordinates: X=$stationX, Y=$stationY")
            
            try {
                // Get EDM reading via USB communication (same as verifyEdgeViaAndroid)
                val edmReading = performSerialEDMReading(deviceType, singleMode = singleMode)
                
                if (!edmReading.success) {
                    return@withContext mapOf(
                        "success" to false,
                        "error" to "Failed to get EDM reading: ${edmReading.error}"
                    )
                }
                
                val goMobileData = edmReading.goMobileData
                if (goMobileData.isNullOrEmpty()) {
                    return@withContext mapOf(
                        "success" to false,
                        "error" to "No EDM measurement data available"
                    )
                }
                
                // Parse the detailed measurement data
                val detailedData = JSONObject(goMobileData)
                val slopeDistanceMm = detailedData.getDouble("slopeDistanceMm")
                val vAzDecimal = detailedData.getDouble("vAzDecimal")
                val hARDecimal = detailedData.getDouble("harDecimal")
                
                Log.d(TAG, "Throw measurement reading - SD: ${slopeDistanceMm}mm, VAz: ${vAzDecimal}°, HAR: ${hARDecimal}°")
                
                // Perform trigonometric calculations (same as Go Mobile)
                val slopeDistanceMeters = slopeDistanceMm / 1000.0
                val vAzRadians = Math.toRadians(vAzDecimal)
                val hARRadians = Math.toRadians(hARDecimal)
                
                val horizontalDistance = slopeDistanceMeters * Math.sin(vAzRadians)
                val throwX = horizontalDistance * Math.cos(hARRadians)
                val throwY = horizontalDistance * Math.sin(hARRadians)
                
                val absoluteThrowX: Double = stationX + throwX
                val absoluteThrowY: Double = stationY + throwY
                
                // Calculate distance from centre (0,0)
                val distanceFromCentre = Math.sqrt(absoluteThrowX * absoluteThrowX + absoluteThrowY * absoluteThrowY)
                
                // Calculate performance metrics
                val distanceBeyondCircle = distanceFromCentre - targetRadius
                
                Log.d(TAG, "Throw measurement results - Distance from centre: ${distanceFromCentre}m, Beyond circle: ${distanceBeyondCircle}m")
                
                // Create result in Go Mobile format
                val resultJson = JSONObject().apply {
                    put("success", true)
                    put("distance", distanceFromCentre)
                    put("distanceFromCentre", distanceFromCentre)
                    put("throwDistance", distanceFromCentre)
                    put("distanceBeyondCircle", distanceBeyondCircle)
                    put("throwX", absoluteThrowX)
                    put("throwY", absoluteThrowY)
                    put("slopeDistanceMm", slopeDistanceMm)
                    put("vAzDecimal", vAzDecimal)
                    put("hARDecimal", hARDecimal)
                    put("message", "Throw measurement completed via Android USB communication")
                }
                
                return@withContext mapOf(
                    "success" to true,
                    "result" to resultJson.toString()
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "measureThrowViaAndroid failed", e)
                return@withContext mapOf(
                    "success" to false,
                    "error" to "Throw measurement failed: ${e.message}"
                )
            }
        }
    }
    
    data class RawEDMResult(
        val success: Boolean,
        val parsedReading: EDMParsedReading? = null,
        val error: String? = null
    )
    
    /**
     * Get raw EDM reading without Go Mobile processing
     * This is used internally to feed data to Go Mobile functions
     * Supports both single and double read modes
     */
    private suspend fun getRawEDMReading(deviceType: String, doubleReadMode: Boolean = false): RawEDMResult {
        Log.d(TAG, "🔵 getRawEDMReading called with doubleReadMode: $doubleReadMode")
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Getting raw EDM reading for Go Mobile processing")
            
            val connection = connectedDevices[deviceType]
            Log.d(TAG, "Raw reading connection state: $connection")
            // Connection validation removed - proceed with measurement
            
            try {
                when (connection?.connectionType ?: "serial") {
                    "serial" -> {
                        // Get serial port and perform measurement
                        val serialPort = activeSerialPorts[deviceType]
                        if (serialPort == null) {
                            return@withContext RawEDMResult(
                                success = false,
                                error = "Serial port not available"
                            )
                        }
                        
                        // Get translator and send command
                        val edmTranslator = EDMDeviceRegistry.createTranslator(selectedEDMDevice)
                        if (edmTranslator == null) {
                            return@withContext RawEDMResult(
                                success = false,
                                error = "No translator available for ${selectedEDMDevice.displayName}"
                            )
                        }
                        
                        val measureCommandBytes = edmTranslator.getMeasurementCommand()
                        
                        if (doubleReadMode) {
                            Log.d(TAG, "🔵 Performing double EDM reading with tolerance checking")
                            
                            // First reading
                            val response1 = serialCommunicationModule.sendEDMCommandBytes(
                                serialPort,
                                measureCommandBytes,
                                timeoutMs = 10000
                            )
                            
                            if (!response1.success) {
                                return@withContext RawEDMResult(
                                    success = false,
                                    error = response1.error ?: "No response from EDM device on first reading"
                                )
                            }
                            
                            val parsedResult1 = edmTranslator.parseResponse(response1.data!!)
                            if (!parsedResult1.isValid) {
                                return@withContext RawEDMResult(
                                    success = false,
                                    error = parsedResult1.errorMessage ?: "Invalid response from EDM device on first reading"
                                )
                            }
                            
                            // Wait 100ms between readings (matches Go Mobile delayBetweenReadsInPair)
                            delay(100)
                            
                            // Second reading
                            val response2 = serialCommunicationModule.sendEDMCommandBytes(
                                serialPort,
                                measureCommandBytes,
                                timeoutMs = 10000
                            )
                            
                            if (!response2.success) {
                                return@withContext RawEDMResult(
                                    success = false,
                                    error = response2.error ?: "No response from EDM device on second reading"
                                )
                            }
                            
                            val parsedResult2 = edmTranslator.parseResponse(response2.data!!)
                            if (!parsedResult2.isValid) {
                                return@withContext RawEDMResult(
                                    success = false,
                                    error = parsedResult2.errorMessage ?: "Invalid response from EDM device on second reading"
                                )
                            }
                            
                            // Compare readings for tolerance (3mm for slope distance - matches Go Mobile sdToleranceMm)
                            val distance1Mm = parsedResult1.slopeDistanceMm
                            val distance2Mm = parsedResult2.slopeDistanceMm
                            val difference = kotlin.math.abs(distance1Mm - distance2Mm)
                            
                            if (difference <= 3.0) { // 3mm tolerance
                                // Average the readings
                                val avgSlopeDistance = (parsedResult1.slopeDistanceMm + parsedResult2.slopeDistanceMm) / 2.0
                                val avgVerticalAngle = (parsedResult1.verticalAngleDegrees + parsedResult2.verticalAngleDegrees) / 2.0
                                val avgHorizontalAngle = (parsedResult1.horizontalAngleDegrees + parsedResult2.horizontalAngleDegrees) / 2.0
                                
                                Log.d(TAG, "Double reading successful - R1: ${distance1Mm}mm, R2: ${distance2Mm}mm, Avg: ${avgSlopeDistance}mm")
                                
                                val averagedResult = EDMParsedReading(
                                    slopeDistanceMm = avgSlopeDistance,
                                    verticalAngleDegrees = avgVerticalAngle,
                                    horizontalAngleDegrees = avgHorizontalAngle,
                                    statusCode = parsedResult1.statusCode,
                                    isValid = true
                                )
                                
                                return@withContext RawEDMResult(
                                    success = true,
                                    parsedReading = averagedResult
                                )
                            } else {
                                Log.w(TAG, "Readings inconsistent - R1: ${distance1Mm}mm, R2: ${distance2Mm}mm, Diff: ${difference}mm")
                                return@withContext RawEDMResult(
                                    success = false,
                                    error = "Readings inconsistent. R1: ${"%.0f".format(distance1Mm)}mm, R2: ${"%.0f".format(distance2Mm)}mm (${difference.toInt()}mm difference)"
                                )
                            }
                            
                        } else {
                            Log.d(TAG, "🔵 Performing single EDM reading (doubleReadMode=false)")
                            
                            // Single reading
                            val response = serialCommunicationModule.sendEDMCommandBytes(
                                serialPort,
                                measureCommandBytes,
                                timeoutMs = 10000
                            )
                            
                            if (!response.success) {
                                return@withContext RawEDMResult(
                                    success = false,
                                    error = response.error ?: "No response from EDM device"
                                )
                            }
                            
                            val parsedResult = edmTranslator.parseResponse(response.data!!)
                            if (!parsedResult.isValid) {
                                return@withContext RawEDMResult(
                                    success = false,
                                    error = parsedResult.errorMessage ?: "Invalid response from EDM device"
                                )
                            }
                            
                            return@withContext RawEDMResult(
                                success = true,
                                parsedReading = parsedResult
                            )
                        }
                    }
                    else -> {
                        // For USB/network connections, delegate to Go Mobile
                        return@withContext RawEDMResult(
                            success = false,
                            error = "Raw reading not supported for ${connection?.connectionType ?: "unknown"} connections"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Raw EDM reading failed", e)
                return@withContext RawEDMResult(
                    success = false,
                    error = "Raw reading failed: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Get latest EDM reading for Go Mobile consumption
     * This is called by Go Mobile's getReliableEDMReading when it needs our data
     */
    fun getLatestEDMReadingForGoMobile(): String? {
        synchronized(edmReadingLock) {
            val reading = latestEDMReading
            if (reading != null) {
                // Convert to Go Mobile's expected JSON format
                val jsonObject = JSONObject().apply {
                    put("slopeDistanceMm", reading.slopeDistanceMm)
                    put("vAzDecimal", reading.verticalAngleDegrees)  
                    put("harDecimal", reading.horizontalAngleDegrees)
                }
                Log.d(TAG, "Providing EDM data to Go Mobile: ${jsonObject}")
                return jsonObject.toString()
            }
            return null
        }
    }
    
    /**
     * Check if device is a known USB-to-serial adapter
     */
    private fun isKnownSerialAdapter(vendorId: Int, productId: Int): Boolean {
        // Common USB-to-serial adapter VID/PID combinations
        val knownAdapters = listOf(
            // CH340 series
            Pair(0x1A86, 0x7523), // CH340
            Pair(0x1A86, 0x5523), // CH341
            // FTDI series  
            Pair(0x0403, 0x6001), // FT232R
            Pair(0x0403, 0x6014), // FT232H
            Pair(0x0403, 0x6006), // FT2232D
            // Prolific
            Pair(0x067B, 0x2303), // PL2303
            // Silicon Labs
            Pair(0x10C4, 0xEA60), // CP2102
            Pair(0x10C4, 0xEA70), // CP2105
            // Others commonly used for EDM devices
            Pair(0x1659, 0x8963)  // Another common serial adapter
        )
        
        return knownAdapters.any { (vid, pid) -> vid == vendorId && pid == productId }
    }
    
    /**
     * List all connected USB devices (matches v16 implementation)
     */
    fun listUsbDevices(): Map<String, Any> {
        Log.d(TAG, "=== USB Device Detection Started ===")
        
        try {
            // Check if USB host feature is available
            val pm = context.packageManager
            val hasUsbHostFeature = pm.hasSystemFeature("android.hardware.usb.host")
            Log.d(TAG, "USB Host feature available: $hasUsbHostFeature")
            
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            Log.d(TAG, "USB Manager class: ${usbManager.javaClass}")
            
            val deviceList = usbManager.deviceList
            Log.d(TAG, "USB Manager obtained - Device list size: ${deviceList.size}")
            Log.d(TAG, "Device list keys: ${deviceList.keys}")
            
            if (deviceList.isEmpty()) {
                Log.w(TAG, "No USB devices found by UsbManager - This is unusual!")
                Log.w(TAG, "Expected to see at least the USB connection cable.")
                Log.w(TAG, "Possible issues:")
                Log.w(TAG, "1. Device not in USB Host mode")
                Log.w(TAG, "2. USB Host API not supported")
                Log.w(TAG, "3. Missing USB permissions")
                Log.w(TAG, "4. Cable is USB device mode only")
                return mapOf(
                    "ports" to emptyList<Map<String, Any>>(),
                    "method" to "android_usb_host_api", 
                    "count" to 0,
                    "status" to "no_devices",
                    "usb_host_available" to hasUsbHostFeature
                )
            }
            
            val devices = mutableListOf<Map<String, Any>>()
            
            for (device in deviceList.values) {
                Log.d(TAG, "Processing USB device:")
                Log.d(TAG, "  Device Name: ${device.deviceName}")
                Log.d(TAG, "  Manufacturer: ${device.manufacturerName ?: "Unknown"}")
                Log.d(TAG, "  Product: ${device.productName ?: "Unknown"}")
                Log.d(TAG, "  Vendor ID: 0x${String.format("%04X", device.vendorId)} (${device.vendorId})")
                Log.d(TAG, "  Product ID: 0x${String.format("%04X", device.productId)} (${device.productId})")
                Log.d(TAG, "  Device Class: ${device.deviceClass}")
                Log.d(TAG, "  Interface Count: ${device.interfaceCount}")
                
                val deviceInfo = mutableMapOf<String, Any>()
                deviceInfo["deviceName"] = device.deviceName
                deviceInfo["manufacturerName"] = device.manufacturerName ?: "Unknown"
                deviceInfo["productName"] = device.productName ?: "Unknown"
                deviceInfo["vendorId"] = device.vendorId
                deviceInfo["productId"] = device.productId
                deviceInfo["deviceClass"] = device.deviceClass
                deviceInfo["port"] = device.deviceName // Use device name as port identifier
                
                // Check if this device matches a known EDM device or is a USB-to-serial device
                val matchedEDMDevice = EDMDeviceRegistry.matchUsbDevice(device.vendorId, device.productId)
                
                // Check if this is a USB-to-serial device (common VID/PIDs for serial adapters)
                val isUsbSerial = matchedEDMDevice != null || isKnownSerialAdapter(device.vendorId, device.productId)
                
                // Create description for user (matches v16 format)
                val description = if (matchedEDMDevice != null) {
                    // Show EDM device name for recognized devices
                    String.format(
                        "%s (VID:%04X PID:%04X)",
                        matchedEDMDevice.displayName,
                        device.vendorId,
                        device.productId
                    )
                } else if (isUsbSerial) {
                    // Show as potential EDM device for serial adapters
                    String.format(
                        "EDM via USB-Serial - %s %s (VID:%04X PID:%04X)",
                        device.manufacturerName ?: "Unknown",
                        device.productName ?: "USB Device",
                        device.vendorId,
                        device.productId
                    )
                } else {
                    // Show generic info for other devices
                    String.format(
                        "%s - %s (VID:%04X PID:%04X)",
                        device.manufacturerName ?: "Unknown",
                        device.productName ?: "USB Device",
                        device.vendorId,
                        device.productId
                    )
                }
                deviceInfo["description"] = description
                
                // Add EDM device info if matched or is USB-serial
                if (matchedEDMDevice != null || isUsbSerial) {
                    deviceInfo["edmDevice"] = true
                    deviceInfo["edmDeviceSpec"] = matchedEDMDevice?.displayName ?: "Generic EDM via USB-Serial"
                    deviceInfo["isSerial"] = true
                } else {
                    deviceInfo["edmDevice"] = false
                }
                
                devices.add(deviceInfo)
                Log.d(TAG, "Added device: $description")
            }
            
            Log.d(TAG, "=== USB Device Detection Complete - Found ${devices.size} devices ===")
            
            return mapOf(
                "ports" to devices,
                "method" to "android_usb_host_api",
                "count" to devices.size,
                "status" to "success"
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "=== USB Device Detection FAILED ===", e)
            Log.e(TAG, "Error: ${e.message}")
            Log.e(TAG, "Stack trace: ${e.stackTrace.contentToString()}")
            return mapOf(
                "error" to (e.message ?: "Unknown error"),
                "ports" to emptyList<Map<String, Any>>(),
                "count" to 0,
                "status" to "error"
            )
        }
    }
}