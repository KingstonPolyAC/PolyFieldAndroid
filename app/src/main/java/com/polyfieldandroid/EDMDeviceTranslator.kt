package com.polyfieldandroid

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.nio.charset.Charset

/**
 * EDM Device Management and Translation Layer
 * 
 * This layer provides:
 * 1. Abstract interface for all EDM devices
 * 2. Device-specific protocol translators
 * 3. Universal bridge to Go Mobile calculation engine
 * 4. Device registry and routing
 */

// --- Data Classes ---

enum class EDMManufacturer { 
    MATO, 
    LEICA, 
    TOPCON, 
    TRIMBLE 
}

data class EDMDeviceSpec(
    val manufacturer: EDMManufacturer,
    val model: String,
    val displayName: String,
    val baudRate: Int,
    val dataBits: Int = 8,
    val stopBits: Int = 1,
    val parity: String = "NONE",
    val vendorIds: List<Int> = emptyList(),
    val productIds: List<Int> = emptyList()
)

data class EDMRawReading(
    val rawResponse: String,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceSpec: EDMDeviceSpec
)

data class EDMParsedReading(
    val slopeDistanceMm: Double,
    val verticalAngleDegrees: Double,
    val horizontalAngleDegrees: Double,
    val statusCode: String? = null,
    val isValid: Boolean = true,
    val errorMessage: String? = null
)

data class EDMTranslationResult(
    val success: Boolean,
    val goMobileFormat: String? = null,
    val error: String? = null,
    val parsedReading: EDMParsedReading? = null
)

// --- Abstract EDM Device Interface ---

abstract class EDMDeviceTranslator(val deviceSpec: EDMDeviceSpec) {
    
    companion object {
        private const val TAG = "EDMDeviceTranslator"
        private const val USB_TIMEOUT_MS = 10000
        private const val READ_BUFFER_SIZE = 1024
    }
    
    /**
     * Get the command to send to the device to request a measurement
     */
    abstract fun getMeasurementCommand(): ByteArray
    
    /**
     * Parse the raw response from the device into structured data
     */
    abstract fun parseResponse(rawResponse: String): EDMParsedReading
    
    /**
     * Convert parsed reading to Go Mobile's expected JSON format
     */
    abstract fun toGoMobileFormat(parsedReading: EDMParsedReading): String
    
    /**
     * Validate if a response is complete and ready for parsing
     */
    abstract fun isResponseComplete(response: String): Boolean
    
    /**
     * Get device-specific error messages
     */
    abstract fun interpretStatusCode(statusCode: String?): String?
    
    /**
     * Send measurement command to USB device and get response
     */
    suspend fun performMeasurement(
        usbManager: UsbManager,
        usbDevice: UsbDevice
    ): EDMTranslationResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting measurement with ${deviceSpec.displayName}")
            
            // Open USB connection
            val connection = usbManager.openDevice(usbDevice)
                ?: return@withContext EDMTranslationResult(
                    success = false,
                    error = "Failed to open USB connection to ${deviceSpec.displayName}"
                )
            
            // Get USB interface and endpoints
            val usbInterface = usbDevice.getInterface(0)
            val endpointOut = findEndpoint(usbInterface, true)
            val endpointIn = findEndpoint(usbInterface, false)
            
            if (endpointOut == null || endpointIn == null) {
                connection.close()
                return@withContext EDMTranslationResult(
                    success = false,
                    error = "USB endpoints not found for ${deviceSpec.displayName}"
                )
            }
            
            // Claim interface
            if (!connection.claimInterface(usbInterface, true)) {
                connection.close()
                return@withContext EDMTranslationResult(
                    success = false,
                    error = "Failed to claim USB interface for ${deviceSpec.displayName}"
                )
            }
            
            try {
                // Send measurement command
                val command = getMeasurementCommand()
                Log.d(TAG, "Sending command: ${command.contentToString()}")
                
                val bytesSent = connection.bulkTransfer(
                    endpointOut, 
                    command, 
                    command.size, 
                    USB_TIMEOUT_MS
                )
                
                if (bytesSent < 0) {
                    return@withContext EDMTranslationResult(
                        success = false,
                        error = "Failed to send command to ${deviceSpec.displayName}"
                    )
                }
                
                // Read response with timeout
                val response = withTimeout(USB_TIMEOUT_MS.toLong()) {
                    readUsbResponse(connection, endpointIn)
                }
                
                Log.d(TAG, "Received response: $response")
                
                // Parse response
                val parsedReading = parseResponse(response)
                
                if (!parsedReading.isValid) {
                    return@withContext EDMTranslationResult(
                        success = false,
                        error = parsedReading.errorMessage ?: "Invalid reading from ${deviceSpec.displayName}"
                    )
                }
                
                // Convert to Go Mobile format
                val goMobileFormat = toGoMobileFormat(parsedReading)
                
                return@withContext EDMTranslationResult(
                    success = true,
                    goMobileFormat = goMobileFormat,
                    parsedReading = parsedReading
                )
                
            } finally {
                connection.releaseInterface(usbInterface)
                connection.close()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during EDM measurement", e)
            return@withContext EDMTranslationResult(
                success = false,
                error = "Measurement failed: ${e.message}"
            )
        }
    }
    
    private fun findEndpoint(usbInterface: UsbInterface, isOutput: Boolean): UsbEndpoint? {
        for (i in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(i)
            if (isOutput && endpoint.direction == 0) { // OUT endpoint
                return endpoint
            } else if (!isOutput && endpoint.direction != 0) { // IN endpoint
                return endpoint
            }
        }
        return null
    }
    
    private suspend fun readUsbResponse(
        connection: UsbDeviceConnection,
        endpoint: UsbEndpoint
    ): String = withContext(Dispatchers.IO) {
        val buffer = ByteArray(READ_BUFFER_SIZE)
        val responseBuilder = StringBuilder()
        
        var attempts = 0
        val maxAttempts = 5
        
        while (attempts < maxAttempts) {
            val bytesRead = connection.bulkTransfer(endpoint, buffer, buffer.size, 2000)
            
            if (bytesRead > 0) {
                val chunk = String(buffer, 0, bytesRead, Charset.forName("ASCII"))
                responseBuilder.append(chunk)
                
                val currentResponse = responseBuilder.toString()
                if (isResponseComplete(currentResponse)) {
                    break
                }
            } else if (bytesRead < 0) {
                Log.w(TAG, "USB read error: $bytesRead")
            }
            
            attempts++
        }
        
        responseBuilder.toString().trim()
    }
}

// --- Device Registry ---

object EDMDeviceRegistry {
    
    private val TAG = "EDMDeviceRegistry"
    
    // Registry of available EDM device specifications
    private val deviceSpecs = mapOf(
        "MATO_MTS602R" to EDMDeviceSpec(
            manufacturer = EDMManufacturer.MATO,
            model = "MTS-602R+",
            displayName = "Mato MTS-602R+",
            baudRate = 9600,
            vendorIds = listOf(1027), // FTDI VID
            productIds = listOf(24577) // FTDI FT232R PID
        )
        // Future devices will be added here:
        // "LEICA_TCR405" to EDMDeviceSpec(...),
        // "TOPCON_GTS102N" to EDMDeviceSpec(...)
    )
    
    // Registry of device translators
    private val translators = mapOf<String, () -> EDMDeviceTranslator>(
        "MATO_MTS602R" to { MatoMTS602RTranslator(deviceSpecs["MATO_MTS602R"]!!) }
        // Future translators will be added here:
        // "LEICA_TCR405" to { LeicaTCR405Translator(deviceSpecs["LEICA_TCR405"]!!) }
    )
    
    /**
     * Get all available EDM device specifications
     */
    fun getAvailableDevices(): List<EDMDeviceSpec> {
        return deviceSpecs.values.toList()
    }
    
    /**
     * Get default EDM device (Mato MTS-602R+)
     */
    fun getDefaultDevice(): EDMDeviceSpec {
        return deviceSpecs["MATO_MTS602R"]!!
    }
    
    /**
     * Find device spec by manufacturer and model
     */
    fun findDevice(manufacturer: EDMManufacturer, model: String): EDMDeviceSpec? {
        return deviceSpecs.values.find { 
            it.manufacturer == manufacturer && it.model == model 
        }
    }
    
    /**
     * Create translator for specified device
     */
    fun createTranslator(deviceSpec: EDMDeviceSpec): EDMDeviceTranslator? {
        val key = "${deviceSpec.manufacturer.name}_${deviceSpec.model.replace("+", "").replace("-", "")}"
        Log.d(TAG, "Looking for translator with key: $key")
        return translators[key]?.invoke()
    }
    
    /**
     * Check if USB device matches any supported EDM device
     */
    fun matchUsbDevice(vendorId: Int, productId: Int): EDMDeviceSpec? {
        return deviceSpecs.values.find { spec ->
            spec.vendorIds.contains(vendorId) && spec.productIds.contains(productId)
        }
    }
}

// --- Universal EDM Communication Bridge ---

class EDMCommunicationBridge {
    
    companion object {
        private const val TAG = "EDMCommunicationBridge"
    }
    
    /**
     * Perform measurement using specified device and translate result for Go Mobile
     */
    suspend fun performMeasurement(
        deviceSpec: EDMDeviceSpec,
        usbManager: UsbManager,
        usbDevice: UsbDevice
    ): EDMTranslationResult {
        
        Log.d(TAG, "Starting measurement with ${deviceSpec.displayName}")
        
        // Get appropriate translator for device
        val translator = EDMDeviceRegistry.createTranslator(deviceSpec)
            ?: return EDMTranslationResult(
                success = false,
                error = "No translator available for ${deviceSpec.displayName}"
            )
        
        // Perform measurement through device-specific translator
        return translator.performMeasurement(usbManager, usbDevice)
    }
    
    /**
     * Convert device-specific response to universal format for Go Mobile
     */
    fun translateToGoMobile(
        rawResponse: String,
        deviceSpec: EDMDeviceSpec
    ): EDMTranslationResult {
        
        val translator = EDMDeviceRegistry.createTranslator(deviceSpec)
            ?: return EDMTranslationResult(
                success = false,
                error = "No translator available for ${deviceSpec.displayName}"
            )
        
        try {
            val parsed = translator.parseResponse(rawResponse)
            if (!parsed.isValid) {
                return EDMTranslationResult(
                    success = false,
                    error = parsed.errorMessage ?: "Failed to parse response"
                )
            }
            
            val goMobileFormat = translator.toGoMobileFormat(parsed)
            return EDMTranslationResult(
                success = true,
                goMobileFormat = goMobileFormat,
                parsedReading = parsed
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Translation error", e)
            return EDMTranslationResult(
                success = false,
                error = "Translation failed: ${e.message}"
            )
        }
    }
}