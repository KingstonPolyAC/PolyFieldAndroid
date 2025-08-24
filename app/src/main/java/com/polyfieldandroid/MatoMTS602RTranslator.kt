package com.polyfieldandroid

import android.util.Log
import org.json.JSONObject

/**
 * Mato MTS-602R+ Device Translator
 * 
 * Handles the specific communication protocol for Mato MTS-602R+ EDM device.
 * 
 * Response format: "0008390 1001021 3080834 83"
 * - 0008390: Slope distance in mm (8.390m)
 * - 1001021: Vertical angle from vertical in DDDMMSS (100° 10' 21")
 * - 3080834: Horizontal angle in DDDMMSS (308° 08' 34")
 * - 83: Status/checksum code
 */
class MatoMTS602RTranslator(deviceSpec: EDMDeviceSpec) : EDMDeviceTranslator(deviceSpec) {
    
    companion object {
        private const val TAG = "MatoMTS602RTranslator"
        
        // Mato MTS-602R+ specific command
        private val MEASUREMENT_COMMAND = byteArrayOf(0x11, 0x0d, 0x0a)
        
        // Response validation
        private const val EXPECTED_PARTS = 4
        private const val MIN_RESPONSE_LENGTH = 20 // Rough minimum for valid response
        
        // Status codes (based on typical EDM status codes)
        private val STATUS_CODES = mapOf(
            "83" to "Normal measurement",
            "84" to "Warning - check prism",
            "85" to "Error - no prism found",
            "86" to "Error - signal too weak",
            "87" to "Error - measurement timeout",
            "88" to "Error - device not ready"
        )
    }
    
    override fun getMeasurementCommand(): ByteArray {
        Log.d(TAG, "Mato MTS-602R+ measurement command: ${MEASUREMENT_COMMAND.contentToString()}")
        return MEASUREMENT_COMMAND
    }
    
    override fun parseResponse(rawResponse: String): EDMParsedReading {
        Log.d(TAG, "📥 Parsing Mato MTS602R+ response: '$rawResponse'")
        Log.d(TAG, "📏 Raw response length: ${rawResponse.length}")
        
        try {
            val trimmed = rawResponse.trim()
            Log.d(TAG, "📏 Trimmed response: '$trimmed' (length: ${trimmed.length})")
            
            // Validate response format
            if (trimmed.length < MIN_RESPONSE_LENGTH) {
                return EDMParsedReading(
                    slopeDistanceMm = 0.0,
                    verticalAngleDegrees = 0.0,
                    horizontalAngleDegrees = 0.0,
                    isValid = false,
                    errorMessage = "Response too short: '$trimmed'"
                )
            }
            
            // Split response into parts
            val parts = trimmed.split("\\s+".toRegex())
            Log.d(TAG, "📊 Response parts: ${parts.joinToString(", ") { "'$it'" }}")
            Log.d(TAG, "📊 Expected format: 'DDDDDDD DDDDDDD DDDDDDD DD' (slope_mm vertical_angle horizontal_angle status)")
            
            if (parts.size != EXPECTED_PARTS) {
                return EDMParsedReading(
                    slopeDistanceMm = 0.0,
                    verticalAngleDegrees = 0.0,
                    horizontalAngleDegrees = 0.0,
                    isValid = false,
                    errorMessage = "Invalid response format. Expected $EXPECTED_PARTS parts, got ${parts.size}: $parts"
                )
            }
            
            // Parse slope distance (in mm)
            Log.d(TAG, "🔄 Parsing slope distance: '${parts[0]}'")
            val slopeDistanceMm = try {
                val distance = parts[0].toDouble()
                Log.d(TAG, "✅ Slope distance: ${distance}mm (${distance/1000.0}m)")
                distance
            } catch (e: NumberFormatException) {
                Log.e(TAG, "❌ Failed to parse slope distance: '${parts[0]}'")
                return EDMParsedReading(
                    slopeDistanceMm = 0.0,
                    verticalAngleDegrees = 0.0,
                    horizontalAngleDegrees = 0.0,
                    isValid = false,
                    errorMessage = "Invalid slope distance format: '${parts[0]}'"
                )
            }
            
            // Parse vertical angle (DDDMMSS format)
            Log.d(TAG, "🔄 Parsing vertical angle: '${parts[1]}'")
            val verticalAngleDegrees = try {
                val angle = parseDDDMMSSAngle(parts[1])
                Log.d(TAG, "✅ Vertical angle: ${angle}° (from '${parts[1]}')")
                angle
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to parse vertical angle: '${parts[1]}' - ${e.message}")
                return EDMParsedReading(
                    slopeDistanceMm = 0.0,
                    verticalAngleDegrees = 0.0,
                    horizontalAngleDegrees = 0.0,
                    isValid = false,
                    errorMessage = "Invalid vertical angle format: '${parts[1]}' - ${e.message}"
                )
            }
            
            // Parse horizontal angle (DDDMMSS format)  
            Log.d(TAG, "🔄 Parsing horizontal angle: '${parts[2]}'")
            val horizontalAngleDegrees = try {
                val angle = parseDDDMMSSAngle(parts[2])
                Log.d(TAG, "✅ Horizontal angle: ${angle}° (from '${parts[2]}')")
                angle
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to parse horizontal angle: '${parts[2]}' - ${e.message}")
                return EDMParsedReading(
                    slopeDistanceMm = 0.0,
                    verticalAngleDegrees = 0.0,
                    horizontalAngleDegrees = 0.0,
                    isValid = false,
                    errorMessage = "Invalid horizontal angle format: '${parts[2]}' - ${e.message}"
                )
            }
            
            // Parse status code
            val statusCode = parts[3]
            Log.d(TAG, "🔄 Parsing status code: '$statusCode'")
            val statusMessage = interpretStatusCode(statusCode)
            
            // Accept all status codes since we ignore status validation
            val isValidMeasurement = true // Accept all measurements regardless of status
            
            Log.d(TAG, "✅ SUCCESSFULLY PARSED Mato MTS602R+ reading:")
            Log.d(TAG, "   📏 Slope Distance: ${slopeDistanceMm}mm (${slopeDistanceMm/1000.0}m)")
            Log.d(TAG, "   📐 Vertical Angle: ${verticalAngleDegrees}°") 
            Log.d(TAG, "   🧭 Horizontal Angle: ${horizontalAngleDegrees}°")
            Log.d(TAG, "   ⚡ Status: $statusCode ($statusMessage)")
            
            return EDMParsedReading(
                slopeDistanceMm = slopeDistanceMm,
                verticalAngleDegrees = verticalAngleDegrees,
                horizontalAngleDegrees = horizontalAngleDegrees,
                statusCode = statusCode,
                isValid = isValidMeasurement,
                errorMessage = if (isValidMeasurement) null else statusMessage
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Mato response: '$rawResponse'", e)
            return EDMParsedReading(
                slopeDistanceMm = 0.0,
                verticalAngleDegrees = 0.0,
                horizontalAngleDegrees = 0.0,
                isValid = false,
                errorMessage = "Parse error: ${e.message}"
            )
        }
    }
    
    override fun toGoMobileFormat(parsedReading: EDMParsedReading): String {
        // Convert to the JSON format expected by Go Mobile's AveragedEDMReading
        val jsonObject = JSONObject().apply {
            put("slopeDistanceMm", parsedReading.slopeDistanceMm)
            put("vAzDecimal", parsedReading.verticalAngleDegrees)
            put("harDecimal", parsedReading.horizontalAngleDegrees)
        }
        
        val result = jsonObject.toString()
        Log.d(TAG, "Converted to Go Mobile format: $result")
        return result
    }
    
    override fun isResponseComplete(response: String): Boolean {
        val trimmed = response.trim()
        
        // Check for minimum length and basic format
        if (trimmed.length < MIN_RESPONSE_LENGTH) {
            return false
        }
        
        // Check for expected number of space-separated parts
        val parts = trimmed.split("\\s+".toRegex())
        if (parts.size < EXPECTED_PARTS) {
            return false
        }
        
        // Check if last part (status code) is present and numeric
        return try {
            parts[3].toInt()
            true
        } catch (e: NumberFormatException) {
            false
        }
    }
    
    override fun interpretStatusCode(statusCode: String?): String? {
        // Ignore status codes - we only need the measurement data
        return "Status: $statusCode (ignored)"
    }
    
    /**
     * Parse DDDMMSS angle format to decimal degrees
     * Example: "1001021" -> 100° 10' 21" -> 100.1725°
     */
    private fun parseDDDMMSSAngle(angleStr: String): Double {
        if (angleStr.length < 6 || angleStr.length > 7) {
            throw IllegalArgumentException("Invalid angle string length: got ${angleStr.length} for '$angleStr'")
        }
        
        // Pad with leading zero if needed (6 digits -> 7 digits)
        val paddedAngle = if (angleStr.length == 6) "0$angleStr" else angleStr
        
        val degrees = paddedAngle.substring(0, 3).toInt()
        val minutes = paddedAngle.substring(3, 5).toInt()
        val seconds = paddedAngle.substring(5, 7).toInt()
        
        // Validate ranges
        if (minutes >= 60 || seconds >= 60) {
            throw IllegalArgumentException("Invalid angle values (MM or SS >= 60) in '$angleStr'")
        }
        
        if (degrees > 360) {
            throw IllegalArgumentException("Invalid degrees value (> 360) in '$angleStr'")
        }
        
        // Convert to decimal degrees
        return degrees + (minutes / 60.0) + (seconds / 3600.0)
    }
}