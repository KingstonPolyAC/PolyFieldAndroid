package com.polyfieldandroid

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException

/**
 * Serial Communication Module for EDM devices via USB-to-serial adapters
 * Handles CH340, FTDI, and other USB-to-serial chips
 */
class SerialCommunicationModule(private val context: Context) {
    
    companion object {
        private const val TAG = "SerialComm"
        private const val READ_TIMEOUT_MS = 10000 // 10 seconds per read
        private const val WRITE_TIMEOUT_MS = 5000  // 5 seconds for write
        private const val CONNECTION_TIMEOUT_MS = 5000 // 5 seconds for connection
    }
    
    // DEBUG: Serial Communication Logging (REMOVE WHEN DEBUG COMPLETE)
    var debugLogger: ((String, String, String, Boolean, String?) -> Unit)? = null
    
    data class SerialResponse(
        val success: Boolean,
        val data: String? = null,
        val error: String? = null
    )
    
    /**
     * Open serial connection to USB device
     */
    suspend fun openSerialConnection(
        usbDevice: UsbDevice,
        baudRate: Int = 9600,
        dataBits: Int = 8,
        stopBits: Int = UsbSerialPort.STOPBITS_1,
        parity: Int = UsbSerialPort.PARITY_NONE
    ): UsbSerialPort? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Opening serial connection to device: ${usbDevice.deviceName}")
                Log.d(TAG, "VID: 0x${String.format("%04X", usbDevice.vendorId)}, PID: 0x${String.format("%04X", usbDevice.productId)}")
                
                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                
                // Check if we have permission
                if (!usbManager.hasPermission(usbDevice)) {
                    Log.e(TAG, "No permission to access USB device")
                    return@withContext null
                }
                
                // Find compatible driver
                val driver = UsbSerialProber.getDefaultProber().probeDevice(usbDevice)
                if (driver == null) {
                    Log.e(TAG, "No compatible USB serial driver found for device")
                    return@withContext null
                }
                
                Log.d(TAG, "Found compatible driver: ${driver.javaClass.simpleName}")
                
                if (driver.ports.isEmpty()) {
                    Log.e(TAG, "Driver has no ports available")
                    return@withContext null
                }
                
                val port = driver.ports[0] // Use first port
                Log.d(TAG, "Using port: ${port.portNumber}")
                
                // Open connection
                val connection = usbManager.openDevice(usbDevice)
                if (connection == null) {
                    Log.e(TAG, "Failed to open USB device connection")
                    return@withContext null
                }
                
                port.open(connection)
                
                // Configure serial parameters
                port.setParameters(baudRate, dataBits, stopBits, parity)
                
                // Set DTR/RTS for proper communication
                port.dtr = true
                port.rts = true
                
                Log.d(TAG, "Serial connection established successfully")
                Log.d(TAG, "Parameters - Baud: $baudRate, Data: $dataBits, Stop: $stopBits, Parity: $parity")
                
                return@withContext port
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open serial connection", e)
                return@withContext null
            }
        }
    }
    
    /**
     * Send command to EDM device and wait for response
     */
    suspend fun sendEDMCommand(
        port: UsbSerialPort,
        command: String,
        expectedResponseLength: Int = 0,
        timeoutMs: Long = READ_TIMEOUT_MS.toLong()
    ): SerialResponse {
        return sendEDMCommandBytes(port, command.toByteArray(Charsets.UTF_8), expectedResponseLength, timeoutMs)
    }
    
    suspend fun sendEDMCommandBytes(
        port: UsbSerialPort,
        commandBytes: ByteArray,
        expectedResponseLength: Int = 0,
        timeoutMs: Long = READ_TIMEOUT_MS.toLong()
    ): SerialResponse {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Sending EDM command bytes: ${commandBytes.contentToString()}")
                
                // Clear any existing data in buffer
                val buffer = ByteArray(1024)
                try {
                    while (port.read(buffer, 10) > 0) {
                        // Drain buffer
                    }
                } catch (e: IOException) {
                    // Ignore timeout on buffer clearing
                }
                
                // Send command
                val bytesWritten = withTimeout(WRITE_TIMEOUT_MS.toLong()) {
                    port.write(commandBytes, WRITE_TIMEOUT_MS)
                    commandBytes.size // Return the expected number of bytes
                }
                
                if (bytesWritten != commandBytes.size) {
                    Log.e(TAG, "Failed to write complete command. Expected: ${commandBytes.size}, Written: $bytesWritten")
                    return@withContext SerialResponse(
                        success = false,
                        error = "Failed to send complete command to EDM device"
                    )
                }
                
                Log.d(TAG, "Command sent successfully ($bytesWritten bytes)")
                
                // DEBUG: Log outgoing command (REMOVE WHEN DEBUG COMPLETE)
                debugLogger?.invoke(
                    "OUT",
                    String(commandBytes, Charsets.UTF_8),
                    commandBytes.joinToString(" ") { "%02x".format(it) },
                    true,
                    null
                )
                
                // Wait for response
                val response = StringBuilder()
                val readBuffer = ByteArray(1024)
                val startTime = System.currentTimeMillis()
                
                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    try {
                        val bytesRead = port.read(readBuffer, 100) // 100ms read timeout
                        if (bytesRead > 0) {
                            val chunk = String(readBuffer, 0, bytesRead)
                            response.append(chunk)
                            Log.d(TAG, "📥 RAW CHUNK: '$chunk' (${bytesRead} bytes) [HEX: ${readBuffer.take(bytesRead).joinToString(" ") { "%02x".format(it) }}]")
                            
                            // DEBUG: Log incoming chunk (REMOVE WHEN DEBUG COMPLETE)
                            debugLogger?.invoke(
                                "IN",
                                chunk,
                                readBuffer.take(bytesRead).joinToString(" ") { "%02x".format(it) },
                                true,
                                null
                            )
                            
                            // Check if we have a complete response
                            val responseStr = response.toString()
                            Log.d(TAG, "📥 FULL RESPONSE SO FAR: '$responseStr' (${responseStr.length} chars)")
                            
                            if (isCompleteEDMResponse(responseStr)) {
                                Log.d(TAG, "✅ Complete EDM response received: '$responseStr'")
                                return@withContext SerialResponse(
                                    success = true,
                                    data = responseStr.trim()
                                )
                            } else {
                                Log.d(TAG, "⏳ Response not yet complete, continuing to read...")
                            }
                        }
                    } catch (e: IOException) {
                        // Timeout on individual read - continue trying
                        delay(10)
                    }
                }
                
                // Timeout occurred
                val responseStr = response.toString()
                if (responseStr.isNotEmpty()) {
                    Log.w(TAG, "Partial response received before timeout: '$responseStr'")
                    return@withContext SerialResponse(
                        success = false,
                        error = "Incomplete response from EDM device: '$responseStr'"
                    )
                } else {
                    Log.e(TAG, "No response received from EDM device within timeout")
                    
                    // DEBUG: Log timeout error (REMOVE WHEN DEBUG COMPLETE)
                    debugLogger?.invoke(
                        "IN",
                        "",
                        "",
                        false,
                        "Timeout: No response received from EDM device within ${timeoutMs}ms"
                    )
                    
                    return@withContext SerialResponse(
                        success = false,
                        error = "No response from EDM. Press F1 on EDM to reset if \"STOP\" is displayed"
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "EDM command failed", e)
                return@withContext SerialResponse(
                    success = false,
                    error = "Communication error: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Check if EDM response is complete
     * Different EDM devices have different response formats
     */
    private fun isCompleteEDMResponse(response: String): Boolean {
        // Common EDM response patterns:
        // - Ends with CR/LF
        // - Contains specific termination characters
        // - Has expected format for distance readings
        
        val trimmed = response.trim()
        if (trimmed.isEmpty()) return false
        
        // Check for common termination patterns
        if (response.endsWith("\r\n") || response.endsWith("\n") || response.endsWith("\r")) {
            return true
        }
        
        // Check for Mato MTS602R+ specific patterns
        // Expected format: "0003928 0971024 0782509 9c" 
        // - 7 digits (slope distance)
        // - 7 digits (vertical angle) 
        // - 7 digits (horizontal angle)
        // - 2 characters (status code - can be hex like 9c or decimal like 83)
        val parts = trimmed.split("\\s+".toRegex())
        if (parts.size == 4 && 
            parts[0].matches(Regex("\\d{7}")) &&
            parts[1].matches(Regex("\\d{7}")) && 
            parts[2].matches(Regex("\\d{7}")) &&
            parts[3].matches(Regex("[0-9a-fA-F]{2}"))) {
            Log.d(TAG, "✅ Detected Mato MTS602R+ response format: '$trimmed'")
            return true
        }
        
        // Check for error responses
        if (trimmed.contains("ERROR", ignoreCase = true) || 
            trimmed.contains("FAIL", ignoreCase = true) ||
            trimmed.contains("TIMEOUT", ignoreCase = true)) {
            return true
        }
        
        return false
    }
    
    /**
     * Get list of available USB serial devices
     */
    fun getAvailableSerialDevices(): List<UsbDevice> {
        try {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val deviceList = usbManager.deviceList
            
            val serialDevices = mutableListOf<UsbDevice>()
            
            for (device in deviceList.values) {
                // Check if device has a compatible driver
                val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
                if (driver != null) {
                    Log.d(TAG, "Found serial device: ${device.deviceName} (${driver.javaClass.simpleName})")
                    serialDevices.add(device)
                }
            }
            
            Log.d(TAG, "Found ${serialDevices.size} compatible serial devices")
            return serialDevices
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get serial devices", e)
            return emptyList()
        }
    }
    
    /**
     * Close serial port
     */
    fun closeSerialPort(port: UsbSerialPort?) {
        try {
            port?.close()
            Log.d(TAG, "Serial port closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing serial port", e)
        }
    }
    
    /**
     * Test serial connection by sending a simple command
     */
    suspend fun testSerialConnection(port: UsbSerialPort): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Send a simple test command (varies by device)
                val response = sendEDMCommand(port, "\r\n", timeoutMs = 2000)
                response.success
            } catch (e: Exception) {
                Log.e(TAG, "Serial connection test failed", e)
                false
            }
        }
    }
}