package com.polyfieldandroid

import android.content.Context
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
}