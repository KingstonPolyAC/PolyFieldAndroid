package com.polyfieldandroid

import android.content.Context
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbDevice
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
        val error: String? = null
    )
    
    data class WindReading(
        val success: Boolean,
        val windSpeed: Double? = null,
        val error: String? = null
    )
    
    /**
     * Connect to USB device
     */
    suspend fun connectUsbDevice(deviceType: String, address: String): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Connecting to USB device: $deviceType at $address")
            
            try {
                // Simulate USB connection
                delay(1000)
                
                val connection = DeviceConnection(
                    deviceType = deviceType,
                    connectionType = "usb",
                    address = address,
                    isConnected = true
                )
                
                connectedDevices[deviceType] = connection
                
                mapOf(
                    "success" to true,
                    "message" to "Connected to $deviceType via USB at $address",
                    "deviceType" to deviceType,
                    "connectionType" to "usb"
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
     */
    suspend fun connectSerialDevice(deviceType: String, address: String): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Connecting to serial device: $deviceType at $address")
            
            try {
                // Simulate serial connection
                delay(1500)
                
                val connection = DeviceConnection(
                    deviceType = deviceType,
                    connectionType = "serial",
                    address = address,
                    isConnected = true
                )
                
                connectedDevices[deviceType] = connection
                
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
     */
    suspend fun connectNetworkDevice(deviceType: String, address: String, port: Int): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Connecting to network device: $deviceType at $address:$port")
            
            try {
                // Simulate network connection
                delay(2000)
                
                val connection = DeviceConnection(
                    deviceType = deviceType,
                    connectionType = "network",
                    address = address,
                    port = port,
                    isConnected = true
                )
                
                connectedDevices[deviceType] = connection
                
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
     * Get single EDM reading for distance measurement (no tolerance checking)
     * Calls the native Go Mobile module for a single reading
     */
    suspend fun getSingleEDMReading(deviceType: String): EDMReading {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Getting single EDM reading from native module: $deviceType")
            
            val connection = connectedDevices[deviceType]
            if (connection == null || !connection.isConnected) {
                return@withContext EDMReading(
                    success = false,
                    error = "Device $deviceType not connected"
                )
            }
            
            try {
                Log.d(TAG, "Calling native mobile.Mobile.getSingleEDMReading($deviceType)")
                
                // Call native Go Mobile module for single reading
                // The native module handles:
                // 1. Taking one EDM reading 
                // 2. No tolerance checking
                // 3. 10-second timeout
                // Note: For now, use the reliable reading method but treat as single read
                val result = mobile.Mobile.getReliableEDMReading(deviceType)
                
                Log.d(TAG, "Native module single reading result: $result")
                
                // Parse the JSON result from native module
                val jsonResult = org.json.JSONObject(result as String)
                
                if (jsonResult.has("error")) {
                    val error = jsonResult.getString("error")
                    if (error == "USB_ANDROID_DELEGATE") {
                        // Handle USB delegation if needed
                        return@withContext EDMReading(
                            success = false,
                            error = "USB communication not yet implemented"
                        )
                    }
                    return@withContext EDMReading(
                        success = false,
                        error = error
                    )
                }
                
                // Success case - extract slope distance and convert to meters
                val slopeDistanceMm = jsonResult.getDouble("slopeDistanceMm")
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
     * Get reliable EDM reading for distance measurement
     * Calls the native Go Mobile module which handles dual reading and tolerance checking
     */
    suspend fun getReliableEDMReading(deviceType: String): EDMReading {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Getting reliable EDM reading from native module: $deviceType")
            
            val connection = connectedDevices[deviceType]
            if (connection == null || !connection.isConnected) {
                return@withContext EDMReading(
                    success = false,
                    error = "Device $deviceType not connected"
                )
            }
            
            try {
                Log.d(TAG, "Calling native mobile.Mobile.getReliableEDMReading($deviceType)")
                
                // Call native Go Mobile module
                // The native module handles:
                // 1. Taking two EDM readings in quick succession (100ms apart)  
                // 2. Comparing them for tolerance (3mm for slope distance)
                // 3. Returning average if within tolerance
                // 4. 10-second timeout per individual read (max 20 seconds total)
                val result = mobile.Mobile.getReliableEDMReading(deviceType)
                
                Log.d(TAG, "Native module result: $result")
                
                // Parse the JSON result from native module
                val jsonResult = org.json.JSONObject(result)
                
                if (jsonResult.has("error")) {
                    val error = jsonResult.getString("error")
                    if (error == "USB_ANDROID_DELEGATE") {
                        // Handle USB delegation if needed
                        return@withContext EDMReading(
                            success = false,
                            error = "USB communication not yet implemented"
                        )
                    }
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
        return connectedDevices[deviceType]?.isConnected == true
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
     * Generate realistic throw distance for demo/fallback
     */
    private fun generateRealisticThrowDistance(): Double {
        // Generate distances similar to real athletic performances
        val baseDistance = when (kotlin.random.Random.nextInt(4)) {
            0 -> 15.0 + kotlin.random.Random.nextDouble() * 8.0  // Shot: 15-23m
            1 -> 45.0 + kotlin.random.Random.nextDouble() * 25.0 // Discus: 45-70m
            2 -> 55.0 + kotlin.random.Random.nextDouble() * 25.0 // Hammer: 55-80m
            3 -> 60.0 + kotlin.random.Random.nextDouble() * 30.0 // Javelin: 60-90m
            else -> 20.0 + kotlin.random.Random.nextDouble() * 10.0
        }
        
        // Add some measurement variation (±5cm)
        return baseDistance + (kotlin.random.Random.nextDouble() - 0.5) * 0.1
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
                
                // Create description for user (matches v16 format)
                val description = String.format(
                    "%s - %s (VID:%04X PID:%04X)",
                    device.manufacturerName ?: "Unknown",
                    device.productName ?: "USB Device",
                    device.vendorId,
                    device.productId
                )
                deviceInfo["description"] = description
                
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